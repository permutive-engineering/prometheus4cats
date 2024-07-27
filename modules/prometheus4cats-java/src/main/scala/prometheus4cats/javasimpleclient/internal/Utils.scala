/*
 * Copyright 2022 Permutive
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
import org.typelevel.log4cats.Logger
import prometheus4cats.Label
import prometheus4cats.javasimpleclient.models.Exceptions._

private[javasimpleclient] object Utils {

  private[javasimpleclient] def unregister[F[_]: Sync: Logger](
      collector: Collector,
      registry: CollectorRegistry
  ): F[Unit] =
    Sync[F].delay(registry.unregister(collector)).handleErrorWith { e =>
      Logger[F].warn(e)(s"Failed to unregister a collector: '$collector'")
    }

  private[javasimpleclient] def modifyMetric[F[_]: Sync: Logger, A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String],
      modify: B => Unit
  ): F[Unit] = modifyMetricF[F, A, B](c, metricName, labelNames, labels, b => Sync[F].delay(modify(b)))

  private[javasimpleclient] def modifyMetricF[F[_]: Sync: Logger, A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String],
      modify: B => F[Unit]
  ): F[Unit] = {
    val mod: F[Unit] =
      for {
        a <- retrieveCollectorForLabels(c, metricName, labelNames, labels)
        _ <- handlePrometheusCollectorErrors(modify(a), c, metricName, labelNames, labels)
      } yield ()

    mod.recoverWith { case e: PrometheusException[_] =>
      Logger[F].warn(e)("Failed to modify Prometheus metric")
    }
  }

  private def retrieveCollectorForLabels[F[_], A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String]
  )(implicit F: Sync[F]): F[B] =
    for {
      child <- handlePrometheusCollectorErrors(
                 F.delay(c.labels(labels: _*)),
                 c,
                 metricName,
                 labelNames,
                 labels
               )
    } yield child

  private def handlePrometheusCollectorErrors[F[_], A: Show, B](
      fa: F[B],
      c: SimpleCollector[_],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String]
  )(implicit F: Sync[F]): F[B] =
    fa.handleErrorWith(e =>
      classStringRep(c)
        .flatMap(className =>
          F.raiseError(UnhandledPrometheusException(className, metricName, labelNames.zip(labels).toMap, e))
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
