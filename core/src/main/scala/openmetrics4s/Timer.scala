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

import scala.concurrent.duration.FiniteDuration

sealed abstract class Timer[F[_]: Record] {
  type Number
  type Metric

  final def time[B](fa: F[B]): F[B] = Record[F].record(fa)((t, _) => recordTime(t))

  def recordTime(duration: FiniteDuration): F[Unit]
}

object Timer {
  type Aux[F[_], N, M[_[_], _]] = Timer[F] {
    type Number = N
    type Metric = M[F, N]
  }

  def fromHistogram[F[_]: Record](histogram: Histogram[F, Double]): Timer.Aux[F, Double, Histogram] =
    new Timer[F] {
      override type Number = Double
      override type Metric = Histogram[F, Double]
      override def recordTime(duration: FiniteDuration): F[Unit] = histogram.observe(duration.toUnit(TimeUnit.SECONDS))
    }

  def fromGauge[F[_]: Record](gauge: Gauge[F, Double]): Timer.Aux[F, Double, Gauge] =
    new Timer[F] {
      override type Number = Double
      override type Metric = Gauge[F, Double]
      override def recordTime(duration: FiniteDuration): F[Unit] = gauge.set(duration.toUnit(TimeUnit.SECONDS))
    }

  sealed abstract class Labelled[F[_]: RecordAttempt, A] {
    self =>

    type Number
    type Metric

    def recordTime(duration: FiniteDuration, labels: A): F[Unit]

    final def time[B](fb: F[B], labels: A): F[B] =
      timeWithComputedLabels(fb, _ => labels)

    final def timeWithComputedLabels[B](fb: F[B], labels: B => A): F[B] =
      RecordAttempt[F].record(fb)((t, b) => recordTime(t, labels(b)))

    final def timeAttempt[B](
        fb: F[B],
        labelsSuccess: B => A,
        labelsError: PartialFunction[Throwable, A]
    ): F[B] =
      RecordAttempt[F].recordAttemptFold[B, A](
        fb,
        recordTime,
        transformSuccess = labelsSuccess,
        transformError = labelsError
      )
  }

  object Labelled {
    type Aux[F[_], A, N, M[_[_], _, _]] = Timer.Labelled[F, A] {
      type Number = N
      type Metric = M[F, N, A]
    }

    def fromHistogram[F[_]: RecordAttempt, A](
        histogram: Histogram.Labelled[F, Double, A]
    ): Labelled.Aux[F, A, Double, Histogram.Labelled] =
      new Labelled[F, A] {
        override type Number = Double
        override type Metric = Histogram.Labelled[F, Double, A]

        override def recordTime(duration: FiniteDuration, labels: A): F[Unit] =
          histogram.observe(duration.toUnit(TimeUnit.SECONDS), labels)
      }

    def fromGauge[F[_]: RecordAttempt, A](
        gauge: Gauge.Labelled[F, Double, A]
    ): Labelled.Aux[F, A, Double, Gauge.Labelled] = new Labelled[F, A] {
      override type Number = Double
      override type Metric = Gauge.Labelled[F, Double, A]

      override def recordTime(duration: FiniteDuration, labels: A): F[Unit] =
        gauge.set(duration.toUnit(TimeUnit.SECONDS), labels)
    }
  }
}
