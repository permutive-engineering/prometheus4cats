/*
 * Copyright 2022-2026 Permutive Ltd. <https://permutive.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package prometheus4cats.javaclient.internal

import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.duration.FiniteDuration

import io.prometheus.metrics.core.metrics.StatefulMetric
import io.prometheus.metrics.model.registry.Collector
import io.prometheus.metrics.model.registry.MetricType
import io.prometheus.metrics.model.snapshots.MetricMetadata
import io.prometheus.metrics.model.snapshots.MetricSnapshot

/** A [[io.prometheus.metrics.model.registry.Collector]] decorator that evicts stale series from `underlying` at scrape
  * time: `collect()` snapshots the underlying metric, then removes every label set whose last [[touch]] is older than
  * `ttl`. A stale series is therefore exposed one final time on the scrape that evicts it (so a write is never silently
  * dropped even when the scrape interval exceeds the TTL) and disappears from the scrape after that; a later write
  * recreates the series, which restarts from zero. Eviction only ever happens when the registry is scraped — an
  * unscraped registry accumulates series indefinitely.
  *
  * Label sets never seen by [[touch]] (e.g. pre-initialised but never written) are never evicted.
  *
  * Eviction scans the [[touch]] map rather than the underlying metric's data points: the map is the same size as the
  * live series set, so the common case (nothing stale) costs one `long` comparison per series instead of a hash and a
  * lookup, and a tracking entry whose write was rejected before its data point was created is reclaimed rather than
  * retained for the lifetime of the process.
  *
  * The metadata accessors must all delegate to `underlying`: `PrometheusRegistry.register` uses them for duplicate-name
  * and type-conflict detection and skips those checks when they return `null`, so missing one would silently disable
  * that validation for the wrapped metric.
  *
  * Timestamps come from an injectable `now` (defaulting to `System.nanoTime()`) rather than `Clock[F]`: `collect()`
  * runs on the exporter's own thread outside any effect context, and the write path deliberately keeps `getDataPoint` a
  * pure `Array[String] => D` so a metric write stays a single `Sync[F].delay`. `touch` costs one `List` wrapper and one
  * boxed `Long` allocation per write.
  *
  * Staleness is decided from the [[touch]] map, but the removal itself is not atomic with that decision. The removal is
  * gated on the tracking entry still holding the timestamp the decision was made from, so a write that lands in that
  * window keeps both its entry and its data point; what it can lose is the eviction of a series that went stale in the
  * same instant, which the following scrape re-evaluates.
  */
final private[javaclient] class EvictingCollector(
    val underlying: StatefulMetric[_, _],
    ttl: FiniteDuration,
    now: () => Long = () => System.nanoTime()
) extends Collector {

  private[this] val ttlNanos = ttl.toNanos

  private[this] val lastWrite = new ConcurrentHashMap[java.util.List[String], java.lang.Long]()

  def touch(labelValues: Array[String]): Unit =
    lastWrite.put(java.util.Arrays.asList(labelValues: _*), java.lang.Long.valueOf(now())): Unit

  private[javaclient] def trackedSeries: Int = lastWrite.size()

  override def collect(): MetricSnapshot = {
    val snapshot = underlying.collect()
    // Compared by difference: `System.nanoTime()`'s origin is arbitrary and only differences are meaningful.
    val cutoff = now() - ttlNanos
    lastWrite.forEach { (labels, written) =>
      if (written.longValue() - cutoff < 0 && lastWrite.remove(labels, written))
        underlying.remove(labels.toArray(new Array[String](labels.size())): _*)
    }
    snapshot
  }

  override def getPrometheusName: String = underlying.getPrometheusName

  override def getMetricType: MetricType = underlying.getMetricType

  override def getMetadata: MetricMetadata = underlying.getMetadata

  override def getLabelNames: java.util.Set[String] = underlying.getLabelNames

}
