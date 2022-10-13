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

package openmetrics4s

import cats.{FlatMap, ~>}
import cats.effect.kernel.Clock
import cats.syntax.flatMap._

import scala.concurrent.duration.FiniteDuration

sealed abstract class CurrentTimeRecorder[F[_]] { self =>
  def mark: F[Unit]

  def mapK[G[_]](fk: F ~> G): CurrentTimeRecorder[G] = new CurrentTimeRecorder[G] {
    override def mark: G[Unit] = fk(self.mark)
  }
}

object CurrentTimeRecorder {
  def fromLongGauge[F[_]: FlatMap: Clock](gauge: Gauge[F, Long])(f: FiniteDuration => Long): CurrentTimeRecorder[F] =
    new CurrentTimeRecorder[F] {
      override def mark: F[Unit] = Clock[F].monotonic.flatMap(dur => gauge.set(f(dur)))
    }

  def fromDoubleGauge[F[_]: FlatMap: Clock](
      gauge: Gauge[F, Double]
  )(f: FiniteDuration => Double): CurrentTimeRecorder[F] = new CurrentTimeRecorder[F] {
    override def mark: F[Unit] = Clock[F].monotonic.flatMap(dur => gauge.set(f(dur)))
  }

  trait Labelled[F[_], A] extends Metric.Labelled[A] { self =>
    def mark(labels: A): F[Unit]

    def contramapLabels[B](f: B => A): Labelled[F, B] = new Labelled[F, B] {
      override def mark(labels: B): F[Unit] = self.mark(f(labels))
    }

    def mapK[G[_]](fk: F ~> G): Labelled[G, A] = new Labelled[G, A] {
      override def mark(labels: A): G[Unit] = fk(self.mark(labels))
    }
  }

  object Labelled {
    implicit def labelsContravariant[F[_]]: LabelsContravariant[Labelled[F, *]] =
      new LabelsContravariant[Labelled[F, *]] {
        override def contramapLabels[A, B](fa: Labelled[F, A])(f: B => A): Labelled[F, B] = fa.contramapLabels(f)
      }

    def fromLongGauge[F[_]: FlatMap: Clock, A](
        gauge: Gauge.Labelled[F, Long, A]
    )(f: FiniteDuration => Long): CurrentTimeRecorder.Labelled[F, A] =
      new CurrentTimeRecorder.Labelled[F, A] {
        override def mark(labels: A): F[Unit] = Clock[F].monotonic.flatMap(dur => gauge.set(f(dur), labels))
      }

    def fromDoubleGauge[F[_]: FlatMap: Clock, A](
        gauge: Gauge.Labelled[F, Double, A]
    )(f: FiniteDuration => Double): CurrentTimeRecorder.Labelled[F, A] = new CurrentTimeRecorder.Labelled[F, A] {
      override def mark(labels: A): F[Unit] = Clock[F].monotonic.flatMap(dur => gauge.set(f(dur), labels))
    }
  }
}
