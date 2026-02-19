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

package prometheus4cats.javasimpleclient.internal

import java.util.concurrent.TimeoutException

import scala.concurrent.duration.FiniteDuration

import cats.Show
import cats.effect.kernel.Sync
import cats.effect.kernel.Temporal
import cats.effect.kernel.syntax.temporal._
import cats.effect.std.Dispatcher
import cats.syntax.all._

import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.SimpleCollector
import prometheus4cats.Label
import prometheus4cats.javasimpleclient.models.Exceptions._

private[javasimpleclient] object Utils {

  private[javasimpleclient] def unregister[F[_]: Sync](
      collector: Collector,
      registry: CollectorRegistry,
      logger: Throwable => String => F[Unit]
  ): F[Unit] =
    Sync[F].delay(registry.unregister(collector)).handleErrorWith { e =>
      logger(e)(s"Failed to unregister a collector: '$collector'")
    }

  /** Builds a label array directly from dynamic label values and pre-computed common label values, avoiding
    * intermediate IndexedSeq concatenation and varargs String[] allocation.
    */
  @inline private def buildLabelArray(
      dynamicLabels: IndexedSeq[String],
      commonLabelValues: Array[String]
  ): Array[String] = {
    val arr = new Array[String](dynamicLabels.length + commonLabelValues.length)
    dynamicLabels.copyToArray(arr, 0): Unit
    System.arraycopy(commonLabelValues, 0, arr, dynamicLabels.length, commonLabelValues.length)
    arr
  }

  private[javasimpleclient] def modifyMetric[F[_]: Sync, A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String],
      modify: B => Unit,
      logger: Throwable => String => F[Unit]
  ): F[Unit] = modifyMetricF[F, A, B](c, metricName, labelNames, labels, b => Sync[F].delay(modify(b)), logger)

  /** Overload that accepts dynamic and common label values separately to avoid IndexedSeq concatenation per call. */
  private[javasimpleclient] def modifyMetric[F[_]: Sync, A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      allLabelNames: IndexedSeq[Label.Name],
      dynamicLabels: IndexedSeq[String],
      commonLabelValues: Array[String],
      modify: B => Unit,
      logger: Throwable => String => F[Unit]
  ): F[Unit] = {
    val labelArray = buildLabelArray(dynamicLabels, commonLabelValues)
    val mod: F[Unit] =
      for {
        a <- retrieveCollectorForLabels(c, metricName, allLabelNames, labelArray)
        _ <- handlePrometheusCollectorErrors(Sync[F].delay(modify(a)), c, metricName, allLabelNames, labelArray)
      } yield ()

    mod.recoverWith { case e: PrometheusException[_] =>
      logger(e)("Failed to modify Prometheus metric")
    }
  }

  private[javasimpleclient] def modifyMetricF[F[_]: Sync, A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String],
      modify: B => F[Unit],
      logger: Throwable => String => F[Unit]
  ): F[Unit] = {
    val mod: F[Unit] =
      for {
        a <- retrieveCollectorForLabels(c, metricName, labelNames, labels)
        _ <- handlePrometheusCollectorErrors(modify(a), c, metricName, labelNames, labels)
      } yield ()

    mod.recoverWith { case e: PrometheusException[_] =>
      logger(e)("Failed to modify Prometheus metric")
    }
  }

  private def retrieveCollectorForLabels[F[_]: Sync, A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String]
  ): F[B] =
    handlePrometheusCollectorErrors(
      Sync[F].delay(c.labels(labels: _*)),
      c,
      metricName,
      labelNames,
      labels
    )

  /** Overload that accepts a pre-built Array[String] to avoid varargs String[] allocation. */
  private def retrieveCollectorForLabels[F[_]: Sync, A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: Array[String]
  ): F[B] =
    handlePrometheusCollectorErrors(
      Sync[F].delay(c.labels(labels: _*)),
      c,
      metricName,
      labelNames,
      labels
    )

  private def handlePrometheusCollectorErrors[F[_]: Sync, A: Show, B](
      fa: F[B],
      c: SimpleCollector[_],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String]
  ): F[B] =
    fa.handleErrorWith(e =>
      classStringRep(c)
        .flatMap(className =>
          Sync[F].raiseError(UnhandledPrometheusException(className, metricName, labelNames.zip(labels).toMap, e))
        )
    )

  /** Overload that accepts Array[String] labels, only building the error map on failure. */
  private def handlePrometheusCollectorErrors[F[_]: Sync, A: Show, B](
      fa: F[B],
      c: SimpleCollector[_],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: Array[String]
  ): F[B] =
    fa.handleErrorWith(e =>
      classStringRep(c)
        .flatMap(className =>
          Sync[F].raiseError(
            UnhandledPrometheusException(className, metricName, labelNames.zip(labels.toIndexedSeq).toMap, e)
          )
        )
    )

  private def classStringRep[F[_]: Sync, A](a: A): F[String] =
    Sync[F].delay(a.getClass.toString) // scalafix:ok

  private[javasimpleclient] def timeoutCallback[F[_]: Temporal, A](
      dispatcher: Dispatcher[F],
      callbackTimeout: FiniteDuration,
      fa: F[A],
      onTimeout: TimeoutException => F[A],
      onError: Throwable => F[A]
  ): A =
    dispatcher.unsafeRunSync(fa.timeout(callbackTimeout).handleErrorWith {
      case th: TimeoutException => onTimeout(th)
      case th                   => onError(th)
    })

}
