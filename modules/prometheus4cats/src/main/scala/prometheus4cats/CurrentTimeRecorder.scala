/*
 * Copyright 2022-2025 Permutive Ltd. <https://permutive.com>
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

package prometheus4cats

import scala.annotation.nowarn
import scala.concurrent.duration.FiniteDuration

import cats.FlatMap
import cats.effect.kernel.Clock
import cats.syntax.flatMap._
import cats.~>

import prometheus4cats.internal.Neq

/** A derived metric type that sets an underlying [[Gauge]] to the current system time. */
trait CurrentTimeRecorder[F[_], A] extends Metric.Labelled[A] { self =>

  /** Set the underlying [[Gauge]] to the current system time. */
  protected def markImpl(labels: A): F[Unit]

  def contramapLabels[B](f: B => A): CurrentTimeRecorder[F, B] = new CurrentTimeRecorder[F, B] {

    override def markImpl(labels: B): F[Unit] = self.markImpl(f(labels))

  }

  def mapK[G[_]](fk: F ~> G): CurrentTimeRecorder[G, A] = new CurrentTimeRecorder[G, A] {

    override def markImpl(labels: A): G[Unit] = fk(self.markImpl(labels))

  }

}

object CurrentTimeRecorder {

  implicit class CurrentTimeRecorderSyntax[F[_]](recorder: CurrentTimeRecorder[F, Unit]) {

    def mark: F[Unit] = recorder.markImpl(())

  }

  @nowarn("msg=unused implicit")
  implicit class LabelledCurrentTimeRecorderSyntax[F[_], A](recorder: CurrentTimeRecorder[F, A])(implicit
      ev: Unit Neq A
  ) {

    def mark(labels: A): F[Unit] = recorder.markImpl(labels)

  }

  /** Create a [[CurrentTimeRecorder]] from a [[Gauge]] that records [[scala.Long]] values
    *
    * The best way to construct a [[CurrentTimeRecorder]] is to use the `asCurrentTimeRecorder` on the gauge DSL
    * provided by [[MetricFactory]]
    *
    * @param gauge
    *   the [[Gauge]] instance to set the current time value against
    * @param f
    *   a function to go from the current time represented in [[scala.concurrent.duration.FiniteDuration]] as a
    *   [[scala.Long]]
    */
  def fromLongGauge[F[_]: FlatMap: Clock, A](
      gauge: Gauge[F, Long, A]
  )(f: FiniteDuration => Long): CurrentTimeRecorder[F, A] =
    new CurrentTimeRecorder[F, A] {

      override def markImpl(labels: A): F[Unit] = Clock[F].monotonic.flatMap(dur => gauge.set(f(dur), labels))

    }

  /** Create a [[CurrentTimeRecorder]] from a [[Gauge]] that records [[scala.Double]] values
    *
    * The best way to construct a [[CurrentTimeRecorder]] is to use the `asCurrentTimeRecorder` on the gauge DSL
    * provided by [[MetricFactory]]
    *
    * @param gauge
    *   the [[Gauge]] instance to set the current time value against
    * @param f
    *   a function to go from the current time represented in [[scala.concurrent.duration.FiniteDuration]] as a
    *   [[scala.Double]]
    */
  def fromDoubleGauge[F[_]: FlatMap: Clock, A](
      gauge: Gauge[F, Double, A]
  )(f: FiniteDuration => Double): CurrentTimeRecorder[F, A] = new CurrentTimeRecorder[F, A] {

    override def markImpl(labels: A): F[Unit] = Clock[F].monotonic.flatMap(dur => gauge.set(f(dur), labels))

  }

  implicit def labelsContravariant[F[_]]: LabelsContravariant[CurrentTimeRecorder[F, *]] =
    new LabelsContravariant[CurrentTimeRecorder[F, *]] {

      override def contramapLabels[A, B](fa: CurrentTimeRecorder[F, A])(f: B => A): CurrentTimeRecorder[F, B] =
        fa.contramapLabels(f)

    }

}
