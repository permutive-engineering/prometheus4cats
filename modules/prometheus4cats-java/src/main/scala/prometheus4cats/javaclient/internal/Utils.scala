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

import cats.effect.kernel.Sync
import cats.syntax.all._

import io.prometheus.metrics.core.metrics.MetricWithFixedMetadata
import io.prometheus.metrics.model.registry.PrometheusRegistry
import prometheus4cats.Label
import prometheus4cats.javaclient.models.Exceptions._

/** Memoizes the resolution from a typed label value `A` to the upstream
  * [[io.prometheus.metrics.core.datapoints.DataPoint]] it addresses.
  *
  * Resolving a data point on every metric update is one of the dominant costs of recording a labelled metric: the typed
  * labels are rendered to strings, copied into an array, and the upstream client then allocates a sorted `Labels`
  * object and hashes it to find the data point. None of those steps' inputs change between updates with the same label
  * value, and data points are never removed while a collector is registered, so the resolved data point is cached keyed
  * by `A`. Subsequent updates reduce to one map hit plus the underlying adder/observation.
  *
  * Failed resolutions are not cached: `computeIfAbsent` stores nothing when the mapping function throws.
  *
  * The cache stops accepting new entries once [[DataPointResolver.MaxCachedLabelSets]] distinct label values have been
  * seen and resolves directly instead; label sets with cardinality beyond the cap are pathological for Prometheus
  * itself, and the cap also bounds memory should an `A` without value-based equality slip through.
  */
final private[javaclient] class DataPointResolver[A, D](
    f: A => IndexedSeq[String],
    commonLabelValues: Array[String],
    getDataPoint: Array[String] => D,
    maxCachedLabelSets: Int = DataPointResolver.MaxCachedLabelSets
) {

  private[this] val cache = new ConcurrentHashMap[A, D]()

  private[this] val compute = new java.util.function.Function[A, D] {

    override def apply(labels: A): D = getDataPoint(labelArray(labels))

  }

  /** Renders the full label-value array for `labels`. Only needed on resolution misses and error paths. */
  def labelArray(labels: A): Array[String] = Utils.buildLabelArray(f(labels), commonLabelValues)

  // Plain get-then-compute rather than bare computeIfAbsent: computeIfAbsent only skips
  // locking when the key is the first node of its hash bin, so under collision chains it
  // synchronizes on every call. A get hit is always a lock-free read; the racy get/compute
  // window is benign because computeIfAbsent deduplicates the insert.
  def apply(labels: A): D = {
    val cached = cache.get(labels)
    if (cached != null) cached // scalafix:ok
    else if (cache.size >= maxCachedLabelSets) compute(labels)
    else cache.computeIfAbsent(labels, compute)
  }

}

private[javaclient] object DataPointResolver {

  private[internal] val MaxCachedLabelSets = 65536

}

private[javaclient] object Utils {

  private[javaclient] def unregister[F[_]: Sync](
      collector: MetricWithFixedMetadata,
      registry: PrometheusRegistry,
      logger: Throwable => String => F[Unit]
  ): F[Unit] =
    Sync[F].delay(registry.unregister(collector)).handleErrorWith { e =>
      logger(e)(s"Failed to unregister a collector: '$collector'")
    }

  /** Builds a label array directly from dynamic label values and pre-computed common label values, avoiding
    * intermediate IndexedSeq concatenation and varargs String[] allocation.
    */
  @inline private[javaclient] def buildLabelArray(
      dynamicLabels: IndexedSeq[String],
      commonLabelValues: Array[String]
  ): Array[String] = {
    val arr = new Array[String](dynamicLabels.length + commonLabelValues.length)
    dynamicLabels.copyToArray(arr, 0): Unit
    System.arraycopy(commonLabelValues, 0, arr, dynamicLabels.length, commonLabelValues.length)
    arr
  }

  /** Resolves the [[io.prometheus.metrics.core.datapoints.DataPoint]] for the given typed label value (via the
    * memoizing `resolver`) and applies the `modify` function to it. Errors raised by either the data-point resolution
    * or the modification are wrapped in a [[UnhandledPrometheusException]] and forwarded to the logger so they are
    * observable but not propagated. Rendered label values for the error message are only computed on the error path.
    */
  private[javaclient] def modifyMetric[F[_]: Sync, A, M, D](
      metricName: M,
      allLabelNames: IndexedSeq[Label.Name],
      labels: A,
      resolver: DataPointResolver[A, D],
      modify: D => Unit,
      logger: Throwable => String => F[Unit]
  ): F[Unit] = {
    val mod: F[Unit] =
      for {
        dp <-
          handleErrors(Sync[F].delay(resolver(labels)), metricName, allLabelNames, () => resolver.labelArray(labels))
        _ <- handleErrors(Sync[F].delay(modify(dp)), metricName, allLabelNames, () => resolver.labelArray(labels))
      } yield ()

    mod.recoverWith { case e: PrometheusException[_] =>
      logger(e)("Failed to modify Prometheus metric")
    }
  }

  private def handleErrors[F[_]: Sync, A, B](
      fa: F[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: () => Array[String]
  ): F[B] =
    fa.handleErrorWith(e =>
      classStringRep(e)
        .flatMap(className =>
          Sync[F].raiseError(
            UnhandledPrometheusException(className, metricName, labelNames.zip(labels().toIndexedSeq).toMap, e)
          )
        )
    )

  private def classStringRep[F[_]: Sync, A](a: A): F[String] =
    Sync[F].delay(a.getClass.toString) // scalafix:ok

}
