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

import cats.effect.kernel.Clock
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, FlatMap, MonadThrow, ~>}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/** A derived metric type that can time a given operation. See [[Timer.fromHistogram]] and [[Timer.fromGauge]] for more
  * information.
  */
sealed abstract class Timer[F[_], A] extends Metric.Labelled[A] { self =>
  type Metric

  protected def recordTimeImpl(duration: FiniteDuration, labels: A): F[Unit]

  def contramapLabels[B](f: B => A): Timer[F, B] = new Timer[F, B] {
    override type Metric = self.Metric

    override def recordTimeImpl(duration: FiniteDuration, labels: B): F[Unit] = self.recordTimeImpl(duration, f(labels))
  }

  def mapK[G[_]](fk: F ~> G): Timer[G, A] = new Timer[G, A] {
    override type Metric = self.Metric

    override def recordTimeImpl(duration: FiniteDuration, labels: A): G[Unit] = fk(
      self.recordTimeImpl(duration, labels)
    )
  }
}

object Timer {

  type Aux[F[_], A, M[_[_], _, _]] = Timer[F, A] {
    type Metric = M[F, Double, A]
  }

  implicit class TimerSyntax[F[_]](timer: Timer[F, Unit]) {
    final def recordTime(duration: FiniteDuration): F[Unit] = timer.recordTimeImpl(duration, ())

    /** Time an operation using an instance of [[cats.effect.kernel.Clock]].
      *
      * The resulting metrics depend on the underlying implementation. See [[Timer.fromHistogram]] and
      * [[Timer.fromGauge]] for more details.
      *
      * @param fb
      *   operation to be timed
      */
    final def time[B](fb: F[B])(implicit F: MonadThrow[F], clock: Clock[F]): F[B] =
      clock.timed(fb.attempt).flatMap { case (duration, b) => recordTime(duration).flatMap(_ => b.liftTo[F]) }
  }

  implicit class LabelledTimerSyntax[F[_], A](timer: Timer[F, A])(implicit ev: Unit =:!= A) {
    final def recordTime(duration: FiniteDuration, labels: A): F[Unit] = timer.recordTimeImpl(duration, labels)

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
    final def time[B](fb: F[B], labels: A)(implicit F: MonadThrow[F], clock: Clock[F]): F[B] =
      timeWithComputedLabels(fb)(_ => labels)

    /** Time an operation using an instance of [[cats.effect.kernel.Clock]], computing labels from the result.
      *
      * @param fb
      *   operation to be timed
      * @param labels
      *   function to convert the result of `fb` to labels
      */
    final def timeWithComputedLabels[B](fb: F[B])(labels: B => A)(implicit F: MonadThrow[F], clock: Clock[F]): F[B] =
      clock.timed(fb).flatMap { case (t, a) => recordTime(t, labels(a)).as(a) }

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
    )(implicit F: MonadThrow[F], clock: Clock[F]): F[B] =
      for {
        x <- clock.timed(fb.attempt)
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
  def fromHistogram[F[_], A](
      histogram: Histogram[F, Double, A]
  ): Timer.Aux[F, A, Histogram] =
    new Timer[F, A] {
      override type Metric = Histogram[F, Double, A]

      override def recordTimeImpl(duration: FiniteDuration, labels: A): F[Unit] =
        histogram.observe(duration.toUnit(TimeUnit.SECONDS), labels)
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
  def fromSummary[F[_], A](
      summary: Summary[F, Double, A]
  ): Timer.Aux[F, A, Summary] =
    new Timer[F, A] {
      override type Metric = Summary[F, Double, A]

      override def recordTimeImpl(duration: FiniteDuration, labels: A): F[Unit] =
        summary.observe(duration.toUnit(TimeUnit.SECONDS), labels)
    }

  /** Create a [[Timer]] from a [[Gauge]] instance.
    *
    * This delegates to the underlying [[Gauge]] instance which will only ever show the last value for duration of the
    * given operation.
    *
    * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to seconds.
    *
    * The best way to construct a gauge based [[Timer]] is to use the `.asTimer` on the histogram DSL provided by
    * [[MetricFactory]].
    *
    * @return
    *   a [[Timer.Aux]] that is annotated with the type of underlying metrics, in this case [[Gauge]]
    */
  def fromGauge[F[_], A](
      gauge: Gauge[F, Double, A]
  ): Timer.Aux[F, A, Gauge] = new Timer[F, A] {
    override type Metric = Gauge[F, Double, A]

    override def recordTimeImpl(duration: FiniteDuration, labels: A): F[Unit] =
      gauge.set(duration.toUnit(TimeUnit.SECONDS), labels)
  }

  sealed abstract class Exemplar[F[_], A] extends Metric.Labelled[A] {
    self =>
    type Metric

    protected def recordTimeImpl(duration: FiniteDuration, labels: A): F[Unit] =
      recordTimeWithExemplarImpl(duration, labels, None)

    protected def recordTimeWithExemplarImpl(
        duration: FiniteDuration,
        labels: A,
        exemplar: Option[prometheus4cats.Exemplar.Labels]
    ): F[Unit]

    def contramapLabels[B](f: B => A): Exemplar[F, B] = new Exemplar[F, B] {
      override type Metric = self.Metric

      override def recordTimeWithExemplarImpl(
          duration: FiniteDuration,
          labels: B,
          exemplar: Option[prometheus4cats.Exemplar.Labels]
      ): F[Unit] = self.recordTimeWithExemplarImpl(duration, f(labels), exemplar)
    }

    def mapK[G[_]](fk: F ~> G): Exemplar[G, A] = new Exemplar[G, A] {
      override type Metric = self.Metric

      override def recordTimeWithExemplarImpl(
          duration: FiniteDuration,
          labels: A,
          exemplar: Option[prometheus4cats.Exemplar.Labels]
      ): G[Unit] =
        fk(self.recordTimeWithExemplarImpl(duration, labels, exemplar))
    }

  }

  object Exemplar {
    type Aux[F[_], A, M[_[_], _, _]] = Timer.Exemplar[F, A] {
      type Metric = M[F, Double, A]
    }

    implicit class TimerSyntax[F[_]](timer: Timer.Exemplar[F, Unit]) {
      final def recordTime(duration: FiniteDuration): F[Unit] = timer.recordTimeImpl(duration, ())

      def recordTimeWithExemplar(
          duration: FiniteDuration
      )(implicit F: FlatMap[F], exemplar: prometheus4cats.Exemplar[F]): F[Unit] =
        exemplar.get.flatMap(recordTimeWithExemplar(duration, _))

      def recordTimeWithExemplar(
          duration: FiniteDuration,
          exemplar: Option[prometheus4cats.Exemplar.Labels]
      ): F[Unit] = timer.recordTimeWithExemplarImpl(duration, (), exemplar)

      /** Time an operation using an instance of [[cats.effect.kernel.Clock]].
        *
        * The resulting metrics depend on the underlying implementation. See [[Timer.fromHistogram]] and
        * [[Timer.fromGauge]] for more details.
        *
        * @param fb
        *   operation to be timed
        */
      final def time[B](fb: F[B])(
          recordExemplar: (FiniteDuration, B) => Boolean
      )(implicit F: MonadThrow[F], clock: Clock[F], exemplar: prometheus4cats.Exemplar[F]): F[B] =
        exemplar.get.flatMap(ex => timeWithExemplar(fb)((dur, b) => if (recordExemplar(dur, b)) ex else None))

      final def timeWithExemplar[B](fb: F[B])(
          recordExemplar: (FiniteDuration, B) => Option[prometheus4cats.Exemplar.Labels]
      )(implicit F: MonadThrow[F], clock: Clock[F]): F[B] =
        clock.timed(fb.attempt).flatMap { case (duration, b) =>
          b match {
            case Left(_) => recordTime(duration).flatMap(_ => b.liftTo[F])
            case Right(value) => recordTimeWithExemplar(duration, recordExemplar(duration, value)).as(value)
          }
        }
    }

    implicit class LabelledTimerSyntax[F[_], A](timer: Timer.Exemplar[F, A])(implicit ev: Unit =:!= A) {
      final def recordTime(duration: FiniteDuration, labels: A): F[Unit] = timer.recordTimeImpl(duration, labels)

      def recordTimeWithExemplar(duration: FiniteDuration, labels: A)(implicit
          F: FlatMap[F],
          exemplar: prometheus4cats.Exemplar[F]
      ): F[Unit] = exemplar.get.flatMap(recordTimeWithExemplar(duration, labels, _))

      def recordTimeWithExemplar(
          duration: FiniteDuration,
          labels: A,
          exemplar: Option[prometheus4cats.Exemplar.Labels]
      ): F[Unit] = timer.recordTimeWithExemplarImpl(duration, labels, exemplar)

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
      final def time[B](fb: F[B], labels: A)(recordExemplar: (FiniteDuration, B, A) => Boolean)(implicit
          F: MonadThrow[F],
          clock: Clock[F],
          exemplar: prometheus4cats.Exemplar[F]
      ): F[B] =
        timeWithComputedLabels(fb)((_: B) => labels, recordExemplar)

      final def timeWithExemplar[B](fb: F[B], labels: A)(
          recordExemplar: (FiniteDuration, B, A) => Option[prometheus4cats.Exemplar.Labels]
      )(implicit F: MonadThrow[F], clock: Clock[F]): F[B] =
        timeWithComputedLabelsExemplar(fb)((_: B) => labels, recordExemplar)

      /** Time an operation using an instance of [[cats.effect.kernel.Clock]], computing labels from the result.
        *
        * @param fb
        *   operation to be timed
        * @param labels
        *   function to convert the result of `fb` to labels
        */
      final def timeWithComputedLabels[B](
          fb: F[B]
      )(labels: B => A, recordExemplar: (FiniteDuration, B, A) => Boolean)(implicit
          F: MonadThrow[F],
          clock: Clock[F],
          exemplar: prometheus4cats.Exemplar[F]
      ): F[B] =
        exemplar.get.flatMap(ex =>
          timeWithComputedLabelsExemplar(fb)(labels, (t, b, a) => ex.filter(_ => recordExemplar(t, b, a)))
        )

      final def timeWithComputedLabelsExemplar[B](
          fb: F[B]
      )(labels: B => A, recordExemplar: (FiniteDuration, B, A) => Option[prometheus4cats.Exemplar.Labels])(implicit
          F: MonadThrow[F],
          clock: Clock[F]
      ): F[B] =
        Clock[F].timed(fb).flatMap { case (t, b) =>
          val a = labels(b)
          recordTimeWithExemplar(t, a, recordExemplar(t, b, a)).as(b)
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
      )(implicit F: MonadThrow[F], clock: Clock[F], exemplar: prometheus4cats.Exemplar[F]): F[B] =
        for {
          x <- clock.timed(fb.attempt)
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
      final def timeAttemptWithExemplar[B](fb: F[B])(
          labelsSuccess: B => A,
          labelsError: PartialFunction[Throwable, A],
          recordExemplarSuccess: (FiniteDuration, B, A) => Option[prometheus4cats.Exemplar.Labels],
          recordExemplarFailure: (FiniteDuration, Throwable, A) => Option[prometheus4cats.Exemplar.Labels]
      )(implicit F: MonadThrow[F], clock: Clock[F]): F[B] =
        for {
          x <- clock.timed(fb.attempt)
          _ <- x._2.fold(
            e =>
              labelsError
                .lift(e)
                .fold(Applicative[F].unit)(a => recordTimeWithExemplar(x._1, a, recordExemplarFailure(x._1, e, a))),
            b => {
              val a = labelsSuccess(b)

              recordTimeWithExemplar(x._1, labelsSuccess(b), recordExemplarSuccess(x._1, b, a))
            }
          )
          res <- x._2.liftTo[F]
        } yield res
    }

    implicit def labelsExemplarContravariant[F[_]]: LabelsContravariant[Exemplar[F, *]] =
      new LabelsContravariant[Exemplar[F, *]] {
        override def contramapLabels[A, B](fa: Exemplar[F, A])(f: B => A): Exemplar[F, B] = fa.contramapLabels(f)
      }

    /** Create a [[Timer.Exemplar]] from a [[Histogram]] instance.
      *
      * This delegates to the underlying [[Histogram]] instance and assumes you have already set up sensible buckets for
      * the distribution of values.
      *
      * Values are recorded in [[scala.Double]]s by converting a [[scala.concurrent.duration.FiniteDuration]] to
      * seconds.
      *
      * The best way to construct a histogram based [[Timer.Exemplar]] is to use the `.asTimer` on the histogram DSL
      * provided by [[MetricFactory]].
      *
      * @return
      *   a [[Timer.Exemplar.Aux]] that is annotated with the type of underlying metrics, in this case [[Histogram]]
      */
    def fromHistogram[F[_], A](
        histogram: Histogram[F, Double, A]
    ): Exemplar.Aux[F, A, Histogram] = new Timer.Exemplar[F, A] {
      override type Metric = Histogram[F, Double, A]

      override def recordTimeWithExemplarImpl(
          duration: FiniteDuration,
          labels: A,
          exemplar: Option[prometheus4cats.Exemplar.Labels]
      ): F[Unit] =
        histogram.observeWithExemplar(duration.toUnit(TimeUnit.SECONDS), labels, exemplar)
    }
  }

  implicit def labelsContravariant[F[_]]: LabelsContravariant[Timer[F, *]] =
    new LabelsContravariant[Timer[F, *]] {
      override def contramapLabels[A, B](fa: Timer[F, A])(f: B => A): Timer[F, B] = fa.contramapLabels(f)
    }

}
