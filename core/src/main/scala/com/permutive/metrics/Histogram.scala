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

package com.permutive.metrics

import cats.{Applicative, Eq, Hash, Order, Show, ~>}
import java.util.concurrent.TimeUnit

import cats.data.NonEmptySeq

import scala.concurrent.duration.FiniteDuration

sealed abstract class Histogram[F[_]: Record] { self =>
  final def time[A](fa: F[A]): F[A] =
    Record[F].record(fa)((t, _) => observeDuration(t))

  protected def durationToDouble(duration: FiniteDuration): Double =
    duration.toUnit(TimeUnit.SECONDS)

  def observe(n: Double): F[Unit]

  final def observeDuration(duration: FiniteDuration): F[Unit] = observe(
    durationToDouble(duration)
  )

  final def mapK[G[_]: Record](fk: F ~> G): Histogram[G] = new Histogram[G] {
    override def observe(n: Double): G[Unit] = fk(self.observe(n))
  }
}

/** Escape hatch for writing testing implementations in `metrics-testing` module
  */
abstract private[metrics] class Histogram_[F[_]: Record] extends Histogram[F]

object Histogram {

  val DefaultHttpBuckets: NonEmptySeq[Double] =
    NonEmptySeq.of(0.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)

  final class Name private (val value: String) extends AnyVal {
    override def toString: String = value
  }

  object Name extends HistogramNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r

    def from(string: String): Either[String, Name] =
      Either.cond(
        regex.matches(string),
        new Name(string),
        s"$string must match `$regex`"
      )

    implicit val HistogramNameHash: Hash[Name] = Hash.by(_.value)

    implicit val HistogramNameEq: Eq[Name] = Eq.by(_.value)

    implicit val HistogramNameShow: Show[Name] = Show.show(_.value)

    implicit val HistogramNameOrder: Order[Name] = Order.by(_.value)

  }

  def make[F[_]: Record](_observe: Double => F[Unit]): Histogram[F] =
    new Histogram[F] {
      override def observe(n: Double): F[Unit] = _observe(n)
    }

  def make[F[_]: Record](
      _observe: Double => F[Unit],
      durationUnits: TimeUnit
  ): Histogram[F] =
    new Histogram[F] {
      override def observe(n: Double): F[Unit] = _observe(n)
      override protected def durationToDouble(
          duration: FiniteDuration
      ): Double = duration.toUnit(durationUnits)
    }

  def noop[F[_]: Applicative]: Histogram[F] = {
    implicit val record: Record[F] = Record.noOpRecord[F]

    new Histogram[F] {
      override def observe(n: Double): F[Unit] = Applicative[F].unit
    }
  }

  sealed abstract class Labelled[F[_]: RecordAttempt, A] {
    self =>
    protected def durationToDouble(duration: FiniteDuration): Double =
      duration.toUnit(TimeUnit.SECONDS)

    def observe(n: Double, labels: A): F[Unit]

    final def observeDuration(duration: FiniteDuration, labels: A): F[Unit] =
      observe(durationToDouble(duration), labels)

    final def time[B](fb: F[B], labels: A): F[B] =
      timeWithComputedLabels(fb, _ => labels)

    final def timeWithComputedLabels[B](fb: F[B], labels: B => A): F[B] =
      RecordAttempt[F].record(fb)((t, b) => observeDuration(t, labels(b)))

    final def timeAttempt[B](
        fb: F[B],
        labelsSuccess: B => A,
        labelsError: PartialFunction[Throwable, A]
    ): F[B] =
      RecordAttempt[F].recordAttemptFold[B, A](
        fb,
        observeDuration,
        transformSuccess = labelsSuccess,
        transformError = labelsError
      )

    final def mapK[G[_]: RecordAttempt](fk: F ~> G): Labelled[G, A] =
      new Labelled[G, A] {
        override def observe(n: Double, labels: A): G[Unit] = fk(
          self.observe(n, labels)
        )
      }

  }

  /** Escape hatch for writing testing implementations in `metrics-testing` module
    */
  abstract private[metrics] class Labelled_[F[_]: RecordAttempt, A] extends Labelled[F, A]

  object Labelled {
    def make[F[_]: RecordAttempt, A](
        _observe: (Double, A) => F[Unit]
    ): Labelled[F, A] =
      new Labelled[F, A] {
        override def observe(n: Double, labels: A): F[Unit] =
          _observe(n, labels)
      }

    def make[F[_]: RecordAttempt, A](
        _observe: (Double, A) => F[Unit],
        durationUnit: TimeUnit
    ): Labelled[F, A] =
      new Labelled[F, A] {
        override def observe(n: Double, labels: A): F[Unit] =
          _observe(n, labels)

        override protected def durationToDouble(
            duration: FiniteDuration
        ): Double = duration.toUnit(durationUnit)
      }

    def noop[F[_]: Applicative, A]: Labelled[F, A] = {
      implicit val recordAttempt: RecordAttempt[F] =
        RecordAttempt.noOpRecordAttempt[F]

      new Labelled[F, A] {
        override def observe(n: Double, labels: A): F[Unit] =
          Applicative[F].unit
      }
    }
  }
}
