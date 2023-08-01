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

package prometheus4cats

import java.util.concurrent.TimeUnit

import cats.effect.kernel.Clock
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, FlatMap, MonadThrow, ~>}

import scala.concurrent.duration.FiniteDuration

/** A derived metric type that can time a given operation. See [[Timer.fromHistogram]] and [[Timer.fromGauge]] for more
  * information.
  */
sealed abstract class Timer[F[_]: FlatMap: Clock] { self =>
  type Metric

  /** Time an operation using an instance of [[cats.effect.kernel.Clock]].
    *
    * The resulting metrics depend on the underlying implementation. See [[Timer.fromHistogram]] and [[Timer.fromGauge]]
    * for more details.
    *
    * @param fa
    *   operation to be timed
    */
  final def time[B](fa: F[B]): F[B] = Clock[F].timed(fa).flatMap { case (t, a) => recordTime(t).as(a) }

  def recordTime(duration: FiniteDuration): F[Unit]

  def mapK[G[_]: FlatMap: Clock](fk: F ~> G): Timer[G] = new Timer[G] {
    override type Metric = self.Metric

    override def recordTime(duration: FiniteDuration): G[Unit] = fk(self.recordTime(duration))
  }
}

object Timer {
  type Aux[F[_], M[_[_], _]] = Timer[F] {
    type Metric = M[F, Double]
  }

  /** Create a [[Timer]] from a [[Histogram]] instance.
    *
    * This delegates to the underlying [[Histogram]] instance and assumes you have already set up sensible buckets for
    * the distribution of values.
    *
    * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to seconds.
    *
    * The best way to construct a histogram based [[Timer]] is to use the `.asTimer` on the histogram DSL provided by
    * [[MetricFactory]].
    *
    * @return
    *   a [[Timer.Aux]] that is annotated with the type of underlying metrics, in this case [[Histogram]]
    */
  def fromHistogram[F[_]: FlatMap: Clock](histogram: Histogram[F, Double]): Timer.Aux[F, Histogram] =
    new Timer[F] {
      override type Metric = Histogram[F, Double]
      override def recordTime(duration: FiniteDuration): F[Unit] = histogram.observe(duration.toUnit(TimeUnit.SECONDS))
    }

  /** Create a [[Timer]] from a [[Summary]] instance.
    *
    * This delegates to the underlying [[Summary]] instance and assumes you have already set up sensible buckets for the
    * distribution of values.
    *
    * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to seconds.
    *
    * The best way to construct a histogram based [[Timer]] is to use the `.asTimer` on the summary DSL provided by
    * [[MetricFactory]].
    *
    * @return
    *   a [[Timer.Aux]] that is annotated with the type of underlying metrics, in this case [[Summary]]
    */
  def fromSummary[F[_]: FlatMap: Clock](summary: Summary[F, Double]): Timer.Aux[F, Summary] =
    new Timer[F] {
      override type Metric = Summary[F, Double]

      override def recordTime(duration: FiniteDuration): F[Unit] = summary.observe(duration.toUnit(TimeUnit.SECONDS))
    }

  /** Create a [[Timer]] from a [[Gauge]] instance.
    *
    * This delegates to the underlying [[Gauge]] instance which will only ever show the last value for duration of the
    * given operation.
    *
    * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to seconds.
    *
    * The best way to construct a gauge based [[Timer]] is to use the `.asTimer` on the gauge DSL provided by
    * [[MetricFactory]].
    *
    * @return
    *   a [[Timer.Aux]] that is annotated with the type of underlying metrics, in this case [[Gauge.Labelled]]
    */
  def fromGauge[F[_]: FlatMap: Clock](gauge: Gauge[F, Double]): Timer.Aux[F, Gauge] =
    new Timer[F] {
      override type Metric = Gauge[F, Double]
      override def recordTime(duration: FiniteDuration): F[Unit] = gauge.set(duration.toUnit(TimeUnit.SECONDS))
    }

  sealed abstract class Exemplar[F[_]: FlatMap: Clock] { self =>
    type Metric

    /** Time an operation using an instance of [[cats.effect.kernel.Clock]].
      *
      * The resulting metrics depend on the underlying implementation. See [[Timer.Exemplar.fromHistogram]] and for more
      * details.
      *
      * @param fa
      *   operation to be timed
      * @param recordExemplar
      *   function to decide a duration threshold or the resulting value of `B` to record an exemplar
      */
    final def time[B](fa: F[B])(recordExemplar: (FiniteDuration, B) => Boolean): F[B] =
      Clock[F].timed(fa).flatMap { case (t, b) =>
        (if (recordExemplar(t, b)) recordTimeWithExemplar(t)
         else recordTime(t)).as(b)
      }

    def recordTime(duration: FiniteDuration): F[Unit]

    def recordTimeWithExemplar(duration: FiniteDuration): F[Unit]

    def mapK[G[_]: FlatMap: Clock](fk: F ~> G): Exemplar[G] = new Exemplar[G] {
      override type Metric = self.Metric

      override def recordTime(duration: FiniteDuration): G[Unit] = fk(self.recordTime(duration))

      override def recordTimeWithExemplar(duration: FiniteDuration): G[Unit] = fk(self.recordTimeWithExemplar(duration))
    }
  }

  object Exemplar {
    type Aux[F[_], M[_[_], _]] = Timer.Exemplar[F] {
      type Metric = M[F, Double]
    }

    /** Create a [[Timer.Exemplar]] from a [[Histogram.Exemplar]] instance.
      *
      * This delegates to the underlying [[Histogram.Exemplar]] instance and assumes you have already set up sensible
      * buckets for the distribution of values.
      *
      * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to
      * seconds.
      *
      * The best way to construct a histogram based [[Timer]] is to use the `.asTimer` on the histogram DSL provided by
      * [[MetricFactory]].
      *
      * @return
      *   a [[Timer.Aux]] that is annotated with the type of underlying metrics, in this case [[Histogram.Exemplar]]
      */
    def fromHistogram[F[_]: FlatMap: Clock](
        histogram: Histogram.Exemplar[F, Double]
    ): Exemplar.Aux[F, Histogram.Exemplar] =
      new Exemplar[F] {
        override type Metric = Histogram.Exemplar[F, Double]

        override def recordTime(duration: FiniteDuration): F[Unit] =
          histogram.observe(duration.toUnit(TimeUnit.SECONDS))

        override def recordTimeWithExemplar(duration: FiniteDuration): F[Unit] =
          histogram.observeWithExemplar(duration.toUnit(TimeUnit.SECONDS))
      }
  }

  /** A derived metric type that can time a given operation. See [[Timer.Labelled.fromHistogram]] and
    * [[Timer.Labelled.fromGauge]] for more information.
    */
  sealed abstract class Labelled[F[_]: MonadThrow: Clock, -A] extends Metric.Labelled[A] { self =>
    type Metric

    def recordTime(duration: FiniteDuration, labels: A): F[Unit]

    /** Time an operation using an instance of [[cats.effect.kernel.Clock]].
      *
      * The resulting metrics depend on the underlying implementation. See [[Timer.fromHistogram]] and
      * [[Timer.fromGauge]] for more details.
      *
      * @param fb
      *   operation to be timed
      * @param labels
      *   labels to add to the underlying metric
      */
    final def time[B](fb: F[B], labels: A): F[B] =
      timeWithComputedLabels(fb)(_ => labels)

    /** Time an operation using an instance of [[cats.effect.kernel.Clock]], computing labels from the result.
      *
      * @param fb
      *   operation to be timed
      * @param labels
      *   function to convert the result of `fb` to labels
      */
    final def timeWithComputedLabels[B](fb: F[B])(labels: B => A): F[B] =
      Clock[F].timed(fb).flatMap { case (t, a) => recordTime(t, labels(a)).as(a) }

    /** Time an operation using an instance of [[cats.effect.kernel.Clock]], handling failures and computing labels from
      * the result.
      *
      * @param fb
      *   operation to be timed
      * @param labelsSuccess
      *   function to convert the successful result of `fb` to labels
      * @param labelsError
      *   partial function to convert an exception raised on unsuccessful operation of `fb`. If the exception does not
      *   match the provided [[scala.PartialFunction]] then no value will be recorded.
      */
    final def timeAttempt[B](fb: F[B])(
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

    def contramapLabels[B](f: B => A): Labelled[F, B] = new Labelled[F, B] {
      override type Metric = self.Metric

      override def recordTime(duration: FiniteDuration, labels: B): F[Unit] = self.recordTime(duration, f(labels))
    }

    def mapK[G[_]: MonadThrow: Clock](fk: F ~> G): Labelled[G, A] = new Labelled[G, A] {
      override type Metric = self.Metric

      override def recordTime(duration: FiniteDuration, labels: A): G[Unit] = fk(self.recordTime(duration, labels))
    }
  }

  object Labelled {
    type Aux[F[_], A, M[_[_], _, _]] = Timer.Labelled[F, A] {
      type Metric = M[F, Double, A]
    }

    sealed abstract class Exemplar[F[_]: MonadThrow: Clock, A] extends Metric.Labelled[A] {
      self =>
      type Metric

      def recordTime(duration: FiniteDuration, labels: A): F[Unit]

      def recordTimeWithExemplar(duration: FiniteDuration, labels: A): F[Unit]

      /** Time an operation using an instance of [[cats.effect.kernel.Clock]].
        *
        * The resulting metrics depend on the underlying implementation. See [[Timer.fromHistogram]] and
        * [[Timer.fromGauge]] for more details.
        *
        * @param fb
        *   operation to be timed
        * @param labels
        *   labels to add to the underlying metric
        */
      final def time[B](fb: F[B], labels: A)(recordExemplar: (FiniteDuration, B, A) => Boolean): F[B] =
        timeWithComputedLabels(fb)(_ => labels, recordExemplar)

      /** Time an operation using an instance of [[cats.effect.kernel.Clock]], computing labels from the result.
        *
        * @param fb
        *   operation to be timed
        * @param labels
        *   function to convert the result of `fb` to labels
        */
      final def timeWithComputedLabels[B](
          fb: F[B]
      )(labels: B => A, recordExemplar: (FiniteDuration, B, A) => Boolean): F[B] =
        Clock[F].timed(fb).flatMap { case (t, b) =>
          val a = labels(b)
          (if (recordExemplar(t, b, a)) recordTime(t, labels(b)) else recordTimeWithExemplar(t, labels(b))).as(b)
        }

      /** Time an operation using an instance of [[cats.effect.kernel.Clock]], handling failures and computing labels
        * from the result.
        *
        * @param fb
        *   operation to be timed
        * @param labelsSuccess
        *   function to convert the successful result of `fb` to labels
        * @param labelsError
        *   partial function to convert an exception raised on unsuccessful operation of `fb`. If the exception does not
        *   match the provided [[scala.PartialFunction]] then no value will be recorded.
        */
      final def timeAttempt[B](fb: F[B])(
          labelsSuccess: B => A,
          labelsError: PartialFunction[Throwable, A],
          recordExemplarSuccess: (FiniteDuration, B, A) => Boolean,
          recordExemplarFailure: (FiniteDuration, Throwable, A) => Boolean
      ): F[B] =
        for {
          x <- Clock[F].timed(fb.attempt)
          _ <- x._2.fold(
            e =>
              labelsError
                .lift(e)
                .fold(Applicative[F].unit)(a =>
                  if (recordExemplarFailure(x._1, e, a)) recordTimeWithExemplar(x._1, a) else recordTime(x._1, a)
                ),
            b => {
              val a = labelsSuccess(b)

              if (recordExemplarSuccess(x._1, b, a)) recordTimeWithExemplar(x._1, labelsSuccess(b))
              else recordTime(x._1, labelsSuccess(b))
            }
          )
          res <- x._2.liftTo[F]
        } yield res

      def contramapLabels[B](f: B => A): Exemplar[F, B] = new Exemplar[F, B] {
        override type Metric = self.Metric

        override def recordTime(duration: FiniteDuration, labels: B): F[Unit] = self.recordTime(duration, f(labels))

        override def recordTimeWithExemplar(duration: FiniteDuration, labels: B): F[Unit] =
          self.recordTimeWithExemplar(duration, f(labels))
      }

      def mapK[G[_]: MonadThrow: Clock](fk: F ~> G): Exemplar[G, A] = new Exemplar[G, A] {
        override type Metric = self.Metric

        override def recordTime(duration: FiniteDuration, labels: A): G[Unit] = fk(self.recordTime(duration, labels))

        override def recordTimeWithExemplar(duration: FiniteDuration, labels: A): G[Unit] = fk(
          self.recordTimeWithExemplar(duration, labels)
        )
      }
    }

    object Exemplar {
      type Aux[F[_], A, M[_[_], _, _]] = Timer.Labelled.Exemplar[F, A] {
        type Metric = M[F, Double, A]
      }

      implicit def labelsExemplarContravariant[F[_]]: LabelsContravariant[Exemplar[F, *]] =
        new LabelsContravariant[Exemplar[F, *]] {
          override def contramapLabels[A, B](fa: Exemplar[F, A])(f: B => A): Exemplar[F, B] = fa.contramapLabels(f)
        }

      /** Create a [[Timer.Labelled.Exemplar]] from a [[Histogram.Labelled.Exemplar]] instance.
        *
        * This delegates to the underlying [[Histogram.Labelled.Exemplar]] instance and assumes you have already set up
        * sensible buckets for the distribution of values.
        *
        * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to
        * seconds.
        *
        * The best way to construct a histogram based [[Timer.Labelled.Exemplar]] is to use the `.asTimer` on the
        * histogram DSL provided by [[MetricFactory]].
        *
        * @return
        *   a [[Timer.Labelled.Exemplar.Aux]] that is annotated with the type of underlying metrics, in this case
        *   [[Histogram.Labelled.Exemplar]]
        */
      def fromHistogram[F[_]: MonadThrow: Clock, A](
          histogram: Histogram.Labelled.Exemplar[F, Double, A]
      ): Exemplar.Aux[F, A, Histogram.Labelled.Exemplar] = new Labelled.Exemplar[F, A] {
        override type Metric = Histogram.Labelled.Exemplar[F, Double, A]

        override def recordTime(duration: FiniteDuration, labels: A): F[Unit] =
          histogram.observe(duration.toUnit(TimeUnit.SECONDS), labels)

        override def recordTimeWithExemplar(duration: FiniteDuration, labels: A): F[Unit] =
          histogram.observeWithExemplar(duration.toUnit(TimeUnit.SECONDS), labels)
      }
    }

    implicit def labelsContravariant[F[_]]: LabelsContravariant[Labelled[F, *]] =
      new LabelsContravariant[Labelled[F, *]] {
        override def contramapLabels[A, B](fa: Labelled[F, A])(f: B => A): Labelled[F, B] = fa.contramapLabels(f)
      }

    /** Create a [[Timer.Labelled]] from a [[Histogram.Labelled]] instance.
      *
      * This delegates to the underlying [[Histogram.Labelled]] instance and assumes you have already set up sensible
      * buckets for the distribution of values.
      *
      * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to
      * seconds.
      *
      * The best way to construct a histogram based [[Timer.Labelled]] is to use the `.asTimer` on the histogram DSL
      * provided by [[MetricFactory]].
      *
      * @return
      *   a [[Timer.Labelled.Aux]] that is annotated with the type of underlying metrics, in this case
      *   [[Histogram.Labelled]]
      */
    def fromHistogram[F[_]: MonadThrow: Clock, A](
        histogram: Histogram.Labelled[F, Double, A]
    ): Labelled.Aux[F, A, Histogram.Labelled] =
      new Labelled[F, A] {
        override type Metric = Histogram.Labelled[F, Double, A]

        override def recordTime(duration: FiniteDuration, labels: A): F[Unit] =
          histogram.observe(duration.toUnit(TimeUnit.SECONDS), labels)
      }

    /** Create a [[Timer.Labelled]] from a [[Summary.Labelled]] instance.
      *
      * This delegates to the underlying [[Summary.Labelled]] instance and assumes you have already set up sensible
      * buckets for the distribution of values.
      *
      * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to
      * seconds.
      *
      * The best way to construct a histogram based [[Timer.Labelled]] is to use the `.asTimer` on the summary DSL
      * provided by [[MetricFactory]].
      *
      * @return
      *   a [[Timer.Labelled.Aux]] that is annotated with the type of underlying metrics, in this case
      *   [[Summary.Labelled]]
      */
    def fromSummary[F[_]: MonadThrow: Clock, A](
        summary: Summary.Labelled[F, Double, A]
    ): Labelled.Aux[F, A, Summary.Labelled] =
      new Labelled[F, A] {
        override type Metric = Summary.Labelled[F, Double, A]

        override def recordTime(duration: FiniteDuration, labels: A): F[Unit] =
          summary.observe(duration.toUnit(TimeUnit.SECONDS), labels)
      }

    /** Create a [[Timer.Labelled]] from a [[Gauge.Labelled]] instance.
      *
      * This delegates to the underlying [[Gauge.Labelled]] instance which will only ever show the last value for
      * duration of the given operation.
      *
      * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to
      * seconds.
      *
      * The best way to construct a gauge based [[Timer.Labelled]] is to use the `.asTimer` on the histogram DSL
      * provided by [[MetricFactory]].
      *
      * @return
      *   a [[Timer.Labelled.Aux]] that is annotated with the type of underlying metrics, in this case
      *   [[Gauge.Labelled]]
      */
    def fromGauge[F[_]: MonadThrow: Clock, A](
        gauge: Gauge.Labelled[F, Double, A]
    ): Labelled.Aux[F, A, Gauge.Labelled] = new Labelled[F, A] {
      override type Metric = Gauge.Labelled[F, Double, A]

      override def recordTime(duration: FiniteDuration, labels: A): F[Unit] =
        gauge.set(duration.toUnit(TimeUnit.SECONDS), labels)
    }
  }
}
