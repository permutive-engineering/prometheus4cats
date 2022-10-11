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

import java.util.concurrent.TimeUnit

import cats.effect.kernel.Clock
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, FlatMap, MonadThrow}

import scala.concurrent.duration.FiniteDuration

sealed abstract class Timer[F[_]: FlatMap: Clock] {
  type Number
  type Metric

  final def time[B](fa: F[B]): F[B] = Clock[F].timed(fa).flatMap { case (t, a) => recordTime(t).as(a) }

  def recordTime(duration: FiniteDuration): F[Unit]
}

object Timer {
  type Aux[F[_], N, M[_[_], _]] = Timer[F] {
    type Number = N
    type Metric = M[F, N]
  }

  def fromHistogram[F[_]: FlatMap: Clock](histogram: Histogram[F, Double]): Timer.Aux[F, Double, Histogram] =
    new Timer[F] {
      override type Number = Double
      override type Metric = Histogram[F, Double]
      override def recordTime(duration: FiniteDuration): F[Unit] = histogram.observe(duration.toUnit(TimeUnit.SECONDS))
    }

  def fromGauge[F[_]: FlatMap: Clock](gauge: Gauge[F, Double]): Timer.Aux[F, Double, Gauge] =
    new Timer[F] {
      override type Number = Double
      override type Metric = Gauge[F, Double]
      override def recordTime(duration: FiniteDuration): F[Unit] = gauge.set(duration.toUnit(TimeUnit.SECONDS))
    }

  sealed abstract class Labelled[F[_]: MonadThrow: Clock, A] {
    self =>

    type Number
    type Metric

    def recordTime(duration: FiniteDuration, labels: A): F[Unit]

    final def time[B](fb: F[B], labels: A): F[B] =
      timeWithComputedLabels(fb, _ => labels)

    final def timeWithComputedLabels[B](fb: F[B], labels: B => A): F[B] =
      Clock[F].timed(fb).flatMap { case (t, a) => recordTime(t, labels(a)).as(a) }

    final def timeAttempt[B](
        fb: F[B],
        labelsSuccess: B => A,
        labelsError: PartialFunction[Throwable, A]
    ): F[B] =
      for {
        x <- Clock[F].timed(fb.attempt)
        _ <- x._2.fold(
          e =>
            labelsError
              .lift(e)
              .fold(Applicative[F].unit)(b => recordTime(x._1, b)),
          a => recordTime(x._1, labelsSuccess(a))
        )
        res <- x._2.liftTo[F]
      } yield res
  }

  object Labelled {
    type Aux[F[_], A, N, M[_[_], _, _]] = Timer.Labelled[F, A] {
      type Number = N
      type Metric = M[F, N, A]
    }

    def fromHistogram[F[_]: MonadThrow: Clock, A](
        histogram: Histogram.Labelled[F, Double, A]
    ): Labelled.Aux[F, A, Double, Histogram.Labelled] =
      new Labelled[F, A] {
        override type Number = Double
        override type Metric = Histogram.Labelled[F, Double, A]

        override def recordTime(duration: FiniteDuration, labels: A): F[Unit] =
          histogram.observe(duration.toUnit(TimeUnit.SECONDS), labels)
      }

    def fromGauge[F[_]: MonadThrow: Clock, A](
        gauge: Gauge.Labelled[F, Double, A]
    ): Labelled.Aux[F, A, Double, Gauge.Labelled] = new Labelled[F, A] {
      override type Number = Double
      override type Metric = Gauge.Labelled[F, Double, A]

      override def recordTime(duration: FiniteDuration, labels: A): F[Unit] =
        gauge.set(duration.toUnit(TimeUnit.SECONDS), labels)
    }
  }
}
