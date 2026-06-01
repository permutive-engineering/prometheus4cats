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

import cats.effect.kernel.Sync
import cats.syntax.all._

import io.prometheus.metrics.core.metrics.MetricWithFixedMetadata
import io.prometheus.metrics.model.registry.PrometheusRegistry
import prometheus4cats.Label
import prometheus4cats.javaclient.models.Exceptions._

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

  /** Retrieves the [[io.prometheus.metrics.core.datapoints.DataPoint]] for the given label values and applies the
    * `modify` function to it. Errors raised by either the data-point lookup or the modification are wrapped in a
    * [[UnhandledPrometheusException]] and forwarded to the logger so they are observable but not propagated.
    */
  private[javaclient] def modifyMetric[F[_]: Sync, A, D](
      metricName: A,
      allLabelNames: IndexedSeq[Label.Name],
      dynamicLabels: IndexedSeq[String],
      commonLabelValues: Array[String],
      getDataPoint: Array[String] => D,
      modify: D => Unit,
      logger: Throwable => String => F[Unit]
  ): F[Unit] = {
    val labelArray = buildLabelArray(dynamicLabels, commonLabelValues)
    val mod: F[Unit] =
      for {
        dp <- handleErrors(Sync[F].delay(getDataPoint(labelArray)), metricName, allLabelNames, labelArray)
        _  <- handleErrors(Sync[F].delay(modify(dp)), metricName, allLabelNames, labelArray)
      } yield ()

    mod.recoverWith { case e: PrometheusException[_] =>
      logger(e)("Failed to modify Prometheus metric")
    }
  }

  private def handleErrors[F[_]: Sync, A, B](
      fa: F[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: Array[String]
  ): F[B] =
    fa.handleErrorWith(e =>
      classStringRep(e)
        .flatMap(className =>
          Sync[F].raiseError(
            UnhandledPrometheusException(className, metricName, labelNames.zip(labels.toIndexedSeq).toMap, e)
          )
        )
    )

  private def classStringRep[F[_]: Sync, A](a: A): F[String] =
    Sync[F].delay(a.getClass.toString) // scalafix:ok

}
