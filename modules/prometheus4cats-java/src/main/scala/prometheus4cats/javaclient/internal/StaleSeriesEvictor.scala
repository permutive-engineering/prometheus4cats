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

import scala.concurrent.duration._

import io.prometheus.metrics.core.metrics.StatefulMetric

/** Tracks the last write time of every (metric, label values) pair and removes pairs that have not been written to
  * within `ttl` from the underlying collector, so idle series stop being exposed. A series removed by a sweep is
  * recreated (from zero) by its next write. Writes racing a sweep keep their map entry and are re-evaluated on the next
  * sweep; at worst a series that was written to in the instant it was being evicted restarts from zero.
  */
final private[javaclient] class StaleSeriesEvictor(ttl: FiniteDuration) {

  import StaleSeriesEvictor.Key

  private[this] val lastTouched = new ConcurrentHashMap[Key, java.lang.Long]()

  val sweepInterval: FiniteDuration = ttl / 4

  def touch(metric: StatefulMetric[_, _], labelValues: Array[String]): Unit =
    lastTouched.put(new Key(metric, labelValues), java.lang.Long.valueOf(System.nanoTime())): Unit

  def sweep(): Unit = {
    val cutoff = System.nanoTime() - ttl.toNanos
    lastTouched.forEach { (key, lastWrite) =>
      if (lastWrite.longValue() < cutoff && lastTouched.remove(key, lastWrite))
        key.metric.remove(key.labelValues: _*)
    }
  }

}

private[javaclient] object StaleSeriesEvictor {

  @SuppressWarnings(Array("scalafix:Disable.equals", "scalafix:Disable.hashCode"))
  final private class Key(val metric: StatefulMetric[_, _], val labelValues: Array[String]) {

    override val hashCode: Int =
      System.identityHashCode(metric) * 31 + java.util.Arrays.hashCode(labelValues.asInstanceOf[Array[AnyRef]])

    override def equals(other: Any): Boolean = other match {
      case that: Key =>
        (that.metric eq metric) && java.util.Arrays.equals(
          that.labelValues.asInstanceOf[Array[AnyRef]],
          labelValues.asInstanceOf[Array[AnyRef]]
        )
      case _ => false
    }

  }

}
