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

import cats.Show
import cats.effect.kernel.syntax.temporal._
import cats.effect.kernel.{Sync, Temporal}
import cats.effect.std.Dispatcher
import cats.syntax.all._
import io.prometheus.client.{Collector, CollectorRegistry, SimpleCollector}
import org.typelevel.log4cats.Logger
import prometheus4cats.Label
import prometheus4cats.javasimpleclient.models.Exceptions._

import scala.concurrent.duration.FiniteDuration

private[javasimpleclient] object Utils {
  def unregister[F[_]: Sync: Logger](collector: Collector, registry: CollectorRegistry): F[Unit] =
    Sync[F].delay(registry.unregister(collector)).handleErrorWith { e =>
      Logger[F].warn(e)(s"Failed to unregister a collector: '$collector'")
    }

  def modifyMetric[F[_]: Sync: Logger, A: Show, B](
      c: SimpleCollector[B],
      metricName: A,
      labelNames: IndexedSeq[Label.Name],
      labels: IndexedSeq[String],
      modify: B => Unit
  ): F[Unit] = {
    val mod: F[Unit] =
      for {
        a <- retrieveCollectorForLabels(c, metricName, labelNames, labels)
        _ <- handlePrometheusCollectorErrors(Sync[F].delay(modify(a)), c, metricName, labelNames, labels)
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
    Sync[F].delay(a.getClass.toString)

  private[javasimpleclient] def timeoutCallback[F[_]: Temporal: Logger, A](
      dispatcher: Dispatcher[F],
      callbackTimeout: FiniteDuration,
      fa: F[A],
      onError: A,
      supplementalError: String
  ): A =
    dispatcher.unsafeRunSync(fa.timeout(callbackTimeout).handleErrorWith {
      case th: TimeoutException =>
        Logger[F]
          .warn(th)(
            s"Timed out running callback after $callbackTimeout.\n" +
              "This may be due to a callback having been registered that performs some long running calculation which blocks\n" +
              "Please review your code or raise an issue or pull request with the library from which this callback was registered\n"
              + supplementalError
          )
          .as(onError)
      case th =>
        Logger[F]
          .warn(th)(
            s"Callback failed with the following exception.\n" +
              "Callbacks that can routinely throw exceptions are strongly discouraged as this can cause performance problems when polling metrics\n" +
              "Please review your code or raise an issue or pull request with the library from which this callback was registered\n"
              + supplementalError
          )
          .as(onError)
    })
}
