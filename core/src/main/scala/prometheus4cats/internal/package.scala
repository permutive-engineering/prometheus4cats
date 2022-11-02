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

package prometheus4cats.internal

import cats.effect.kernel.{Clock, MonadCancelThrow, Resource}
import cats.syntax.all._
import cats.{Contravariant, FlatMap, Functor, MonadThrow, Show}
import prometheus4cats.OutcomeRecorder.Status
import prometheus4cats._

import scala.concurrent.duration.FiniteDuration

private[prometheus4cats] trait BuildStep[F[_], A] { self =>

  /** Builds the metric */
  def build: Resource[F, A]

  /** Unsafely builds the metric, but does not deallocate
    */
  def unsafeBuild(implicit F: MonadCancelThrow[F]): F[A] = build.allocated.map(_._1)

  def map[B](f: A => B): BuildStep[F, B] = new BuildStep[F, B] {
    override def build: Resource[F, B] = self.build.map(f)
  }

}

object BuildStep {
  private[prometheus4cats] def apply[F[_], A](fa: Resource[F, A]): BuildStep[F, A] = new BuildStep[F, A] {
    override def build: Resource[F, A] = fa
  }

  implicit class DoubleGaugeSyntax[F[_]: FlatMap: Clock](bs: BuildStep[F, Gauge[F, Double]]) {
    def asTimer: BuildStep[F, Timer.Aux[F, Gauge]] = bs.map(Timer.fromGauge[F])

    def asCurrentTimeRecorder: BuildStep[F, CurrentTimeRecorder[F]] = asCurrentTimeRecorder(_.toSeconds.toDouble)

    def asCurrentTimeRecorder(f: FiniteDuration => Double): BuildStep[F, CurrentTimeRecorder[F]] =
      bs.map(CurrentTimeRecorder.fromDoubleGauge(_)(f))
  }

  implicit class LongGaugeSyntax[F[_]: FlatMap: Clock](bs: BuildStep[F, Gauge[F, Long]]) {
    def asCurrentTimeRecorder: BuildStep[F, CurrentTimeRecorder[F]] = asCurrentTimeRecorder(_.toSeconds)

    def asCurrentTimeRecorder(f: FiniteDuration => Long): BuildStep[F, CurrentTimeRecorder[F]] =
      bs.map(CurrentTimeRecorder.fromLongGauge(_)(f))
  }

  implicit class DoubleHistogramSyntax[F[_]: FlatMap: Clock](bs: BuildStep[F, Histogram[F, Double]]) {
    def asTimer: BuildStep[F, Timer.Aux[F, Histogram]] = bs.map(Timer.fromHistogram[F])
  }

  implicit class DoubleLabelledGaugeSyntax[F[_]: MonadThrow: Clock, A](
      bs: BuildStep[F, Gauge.Labelled[F, Double, A]]
  ) {
    def asTimer: BuildStep[F, Timer.Labelled.Aux[F, A, Gauge.Labelled]] =
      bs.map(Timer.Labelled.fromGauge[F, A])

    def asCurrentTimeRecorder: BuildStep[F, CurrentTimeRecorder.Labelled[F, A]] = asCurrentTimeRecorder(
      _.toSeconds.toDouble
    )

    def asCurrentTimeRecorder(f: FiniteDuration => Double): BuildStep[F, CurrentTimeRecorder.Labelled[F, A]] =
      bs.map(CurrentTimeRecorder.Labelled.fromDoubleGauge(_)(f))
  }

  implicit class LongLabelledGaugeSyntax[F[_]: MonadThrow: Clock, A](
      bs: BuildStep[F, Gauge.Labelled[F, Long, A]]
  ) {
    def asCurrentTimeRecorder: BuildStep[F, CurrentTimeRecorder.Labelled[F, A]] = asCurrentTimeRecorder(_.toSeconds)

    def asCurrentTimeRecorder(f: FiniteDuration => Long): BuildStep[F, CurrentTimeRecorder.Labelled[F, A]] =
      bs.map(CurrentTimeRecorder.Labelled.fromLongGauge(_)(f))
  }

  implicit class DoubleLabelledHistogramSyntax[F[_]: MonadThrow: Clock, A](
      bs: BuildStep[F, Histogram.Labelled[F, Double, A]]
  ) {
    def asTimer: BuildStep[F, Timer.Labelled.Aux[F, A, Histogram.Labelled]] =
      bs.map(Timer.Labelled.fromHistogram[F, A])
  }

  implicit class DoubleSummarySyntax[F[_]: FlatMap: Clock](bs: BuildStep[F, Summary[F, Double]]) {
    def asTimer: BuildStep[F, Timer.Aux[F, Summary]] = bs.map(Timer.fromSummary[F])
  }

  implicit class DoubleLabelledSummarySyntax[F[_]: MonadThrow: Clock, A](
      bs: BuildStep[F, Summary.Labelled[F, Double, A]]
  ) {
    def asTimer: BuildStep[F, Timer.Labelled.Aux[F, A, Summary.Labelled]] =
      bs.map(Timer.Labelled.fromSummary[F, A])
  }

  type Aux[F[_], M[_], A] = BuildStep[F, M[A]]

  implicit def auxContravariant[F[_], M[_]: Contravariant]: Contravariant[Aux[F, M, *]] =
    new Contravariant[Aux[F, M, *]] {
      override def contramap[A, B](fa: Aux[F, M, A])(f: B => A): Aux[F, M, B] =
        fa.map(_.contramap(f))
    }

  implicit def auxLabelsContravariant[F[_], M[_]: LabelsContravariant]: LabelsContravariant[Aux[F, M, *]] =
    new LabelsContravariant[Aux[F, M, *]] {
      override def contramapLabels[A, B](fa: Aux[F, M, A])(f: B => A): Aux[F, M, B] =
        fa.map(LabelsContravariant[M].contramapLabels(_)(f))
    }

  implicit class ContravariantSyntax[F[_], M[_]: Contravariant, A](bs: BuildStep[F, M[A]]) {
    def contramap[B](f: B => A): BuildStep[F, M[B]] = bs.map(_.contramap(f))
  }

  implicit class LabelsContravariantSyntax[F[_], M[_]: LabelsContravariant, A](bs: BuildStep[F, M[A]]) {
    def contramapLabels[B](f: B => A): BuildStep[F, M[B]] = bs.map(LabelsContravariant[M].contramapLabels(_)(f))
  }
}

trait CallbackStep[F[_], A] { self =>
  protected def buildCallback: F[A] => Resource[F, Unit]

  def callback(callback: F[A]): BuildStep[F, Unit] = new BuildStep[F, Unit] {
    override def build: Resource[F, Unit] = buildCallback(callback)
  }

  def contramapCallback[B](f: B => A)(implicit F: Functor[F]): CallbackStep[F, B] = new CallbackStep[F, B] {
    override protected def buildCallback: F[B] => Resource[F, Unit] = fb => self.buildCallback(fb.map(f))
  }
}

class CallbackBuildStep[F[_], A, B](fa: Resource[F, A], override val buildCallback: F[B] => Resource[F, Unit])
    extends BuildStep[F, A]
    with CallbackStep[F, B] {
  override def build: Resource[F, A] = fa
}

class MetricDsl[F[_], A, M[_[_], _], L[_[_], _, _]] private[prometheus4cats] (
    makeMetric: Resource[F, M[F, A]],
    private[internal] val makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L]
) extends BuildStep[F, M[F, A]] {

  override def build: Resource[F, M[F, A]] = makeMetric

  /** Sets the first label of the metric. Requires either a `Show` instance for the label type, or a method converting
    * the label value to a `String`.
    */
  def label[B]: FirstLabelApply[F, A, B, L] =
    (name, toString) =>
      new LabelledMetricDsl(
        makeLabelledMetric,
        Sized(name),
        a => Sized(toString(a))
      )

  /** Creates a metric whose labels aren't checked at compile time. Provides a builder for a labelled metric that takes
    * a map of label names to their values.
    *
    * This should be used when the labels are not known at compile time and potentially come from some source at
    * runtime.
    *
    * @param labelNames
    *   names of the labels
    */
  def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, L[F, A, Map[Label.Name, String]]] =
    BuildStep[F, L[F, A, Map[Label.Name, String]]](
      makeLabelledMetric(
        labelNames
      )(labels => labelNames.flatMap(labels.get))
    )

  /** Creates a metric whose labels aren't checked at compile time. Provides a builder for a labelled metric that takes
    * a map of label names to their values.
    *
    * This should be used when the labels are not known at compile time and potentially come from some source at
    * runtime.
    *
    * @param labelNames
    *   glob of names of the labels
    */
  def unsafeLabels(
      labelNames: Label.Name*
  ): BuildStep[F, L[F, A, Map[Label.Name, String]]] = unsafeLabels(labelNames.toIndexedSeq)

  /** Creates a metric whose label sizes _are_ checked at compile time. Takes a sized collection of label name and a
    * function converting some label object `B` to a sized collection of strings.
    *
    * This is useful where a single type `B` translates to multiple labels. Once invoked, this cannot be used with the
    * singular `.label` syntax.
    *
    * @tparam B
    *   type to convert into labels
    * @tparam N
    *   size of the label collection
    * @param labelNames
    *   sized collection of labels names
    * @param f
    *   function to convert `B` in to a sized collection of label values
    */
  def labels[B, N <: Nat](labelNames: Sized[IndexedSeq[Label.Name], N])(
      f: B => Sized[IndexedSeq[String], N]
  ): LabelsBuildStep[F, A, B, N, L] =
    new LabelsBuildStep(makeLabelledMetric, labelNames, f)
}

object MetricDsl {
  class WithCallbacks[F[_], A, A0, M[_[_], _], L[_[_], _, _]](
      makeMetric: Resource[F, M[F, A]],
      makeCallback: F[A0] => Resource[F, Unit],
      makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L],
      makeLabelledCallback: LabelledCallbackPartiallyApplied[F, A0]
  ) extends MetricDsl[F, A, M, L](makeMetric, makeLabelledMetric)
      with CallbackStep[F, A0] {
    override protected def buildCallback: F[A0] => Resource[F, Unit] = makeCallback

    override def unsafeLabels(
        labelNames: IndexedSeq[Label.Name]
    ): CallbackBuildStep[F, L[F, A, Map[Label.Name, String]], (A0, Map[Label.Name, String])] =
      new CallbackBuildStep[F, L[F, A, Map[Label.Name, String]], (A0, Map[Label.Name, String])](
        makeLabelledMetric(labelNames)(labels => labelNames.flatMap(labels.get)),
        cb => makeLabelledCallback(labelNames, cb)(labels => labelNames.flatMap(labels.get))
      )

    override def unsafeLabels(
        labelNames: Label.Name*
    ): CallbackBuildStep[F, L[F, A, Map[Label.Name, String]], (A0, Map[Label.Name, String])] = unsafeLabels(
      labelNames.toIndexedSeq
    )

    override def labels[B, N <: Nat](labelNames: Sized[IndexedSeq[Label.Name], N])(
        f: B => Sized[IndexedSeq[String], N]
    ): LabelsBuildStep.WithCallbacks[F, A, A0, B, N, L] =
      new LabelsBuildStep.WithCallbacks[F, A, A0, B, N, L](makeLabelledMetric, makeLabelledCallback, labelNames, f)

    override def label[B]: FirstLabelApply.WithCallbacks[F, A, A0, B, L] =
      new FirstLabelApply.WithCallbacks[F, A, A0, B, L] {
        override def apply(
            name: Label.Name,
            toString: B => String
        ): LabelledMetricDsl.WithCallbacks[F, A, A0, B, Nat._1, L] =
          new LabelledMetricDsl.WithCallbacks[F, A, A0, B, Nat._1, L](
            makeLabelledMetric,
            makeLabelledCallback,
            Sized(name),
            a => Sized(toString(a))
          )
      }
  }

  implicit class CounterSyntax[F[_]: MonadCancelThrow, A](dsl: MetricDsl[F, A, Counter, Counter.Labelled]) {
    def asOutcomeRecorder: BuildStep[F, OutcomeRecorder.Aux[F, A, Counter.Labelled]] = BuildStep(
      dsl
        .makeLabelledMetric[Status](IndexedSeq(Label.Name.outcomeStatus))(status => IndexedSeq(status.show))
        .map(OutcomeRecorder.fromCounter(_))
    )
  }

  implicit class GaugeSyntax[F[_]: MonadCancelThrow, A](dsl: MetricDsl[F, A, Gauge, Gauge.Labelled]) {
    def asOutcomeRecorder: BuildStep[F, OutcomeRecorder.Aux[F, A, Gauge.Labelled]] = BuildStep(
      dsl
        .makeLabelledMetric[Status](IndexedSeq(Label.Name.outcomeStatus))(status => IndexedSeq(status.show))
        .map(OutcomeRecorder.fromGauge(_))
    )
  }
}

abstract private[prometheus4cats] class BaseLabelsBuildStep[F[_], A, T, N <: Nat, L[_[_], _, _]](
    fa: Resource[F, L[F, A, T]]
) extends BuildStep[F, L[F, A, T]] {
  protected[internal] val makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L]
  protected[internal] val labelNames: Sized[IndexedSeq[Label.Name], N]
  protected[internal] val f: T => Sized[IndexedSeq[String], N]

  def contramapLabels[B](f: B => T): BaseLabelsBuildStep[F, A, B, N, L]

  override def build: Resource[F, L[F, A, T]] = fa
}

object BaseLabelsBuildStep {
  implicit class CounterSyntax[F[_]: MonadCancelThrow, A, T, N <: Nat](
      dsl: BaseLabelsBuildStep[F, A, T, N, Counter.Labelled]
  ) {
    def asOutcomeRecorder: BuildStep[F, OutcomeRecorder.Labelled.Aux[F, A, T, Counter.Labelled]] = BuildStep(
      dsl
        .makeLabelledMetric[(T, Status)](dsl.labelNames.unsized :+ Label.Name.outcomeStatus) { case (t, status) =>
          dsl.f(t).unsized :+ status.show
        }
        .map(OutcomeRecorder.Labelled.fromCounter(_))
    )

    def contramap[B](f: B => A): BuildStep[F, Counter.Labelled[F, B, T]] = dsl.map(_.contramap(f))
  }

  implicit class GaugeSyntax[F[_]: MonadCancelThrow, A, T, N <: Nat](
      dsl: BaseLabelsBuildStep[F, A, T, N, Gauge.Labelled]
  ) {
    def asOutcomeRecorder: BuildStep[F, OutcomeRecorder.Labelled.Aux[F, A, T, Gauge.Labelled]] = BuildStep(
      dsl
        .makeLabelledMetric[(T, Status)](dsl.labelNames.unsized :+ Label.Name.outcomeStatus) { case (t, status) =>
          dsl.f(t).unsized :+ status.show
        }
        .map(OutcomeRecorder.Labelled.fromGauge(_))
    )

    def contramap[B](f: B => A): BuildStep[F, Gauge.Labelled[F, B, T]] = dsl.map(_.contramap(f))
  }

  implicit class HistogramSyntax[F[_]: Functor, A, T, N <: Nat](
      dsl: BaseLabelsBuildStep[F, A, T, N, Histogram.Labelled]
  ) {
    def contramap[B](f: B => A): BuildStep[F, Histogram.Labelled[F, B, T]] = dsl.map(_.contramap(f))
  }

  implicit class SummarySyntax[F[_]: Functor, A, T, N <: Nat](
      dsl: BaseLabelsBuildStep[F, A, T, N, Summary.Labelled]
  ) {
    def contramap[B](f: B => A): BuildStep[F, Summary.Labelled[F, B, T]] = dsl.map(_.contramap(f))
  }
}

class LabelsBuildStep[F[_], A, T, N <: Nat, L[_[_], _, _]] private[internal] (
    protected[internal] val makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L],
    protected[internal] val labelNames: Sized[IndexedSeq[Label.Name], N],
    protected[internal] val f: T => Sized[IndexedSeq[String], N]
) extends BaseLabelsBuildStep[F, A, T, N, L](
      makeLabelledMetric(labelNames.unsized)(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t).unsized
      )
    ) {
  override def contramapLabels[B](f0: B => T): LabelsBuildStep[F, A, B, N, L] = new LabelsBuildStep[F, A, B, N, L](
    makeLabelledMetric,
    labelNames,
    b => f(f0(b))
  )
}

object LabelsBuildStep {
  class WithCallbacks[F[_], A, A0, T, N <: Nat, L[_[_], _, _]] private[internal] (
      makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L],
      makeLabelledCallback: LabelledCallbackPartiallyApplied[F, A0],
      labelNames: Sized[IndexedSeq[Label.Name], N],
      f: T => Sized[IndexedSeq[String], N]
  ) extends LabelsBuildStep[F, A, T, N, L](
        makeLabelledMetric,
        labelNames,
        f
      )
      with CallbackStep[F, (A0, T)] {
    override protected def buildCallback: F[(A0, T)] => Resource[F, Unit] = cb =>
      makeLabelledCallback(labelNames.unsized, cb)(f(_).unsized)

    override def contramapLabels[B](f0: B => T): LabelsBuildStep.WithCallbacks[F, A, A0, B, N, L] =
      new WithCallbacks(makeLabelledMetric, makeLabelledCallback, labelNames, b => f(f0(b)))
  }
}

class LabelledMetricDsl[F[_], A, T, N <: Nat, L[_[_], _, _]] private[internal] (
    protected[internal] val makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L],
    protected[internal] val labelNames: Sized[IndexedSeq[Label.Name], N],
    protected[internal] val f: T => Sized[IndexedSeq[String], N]
) extends BaseLabelsBuildStep[F, A, T, N, L](
      makeLabelledMetric(labelNames.unsized)(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t).unsized
      )
    ) {

  /** Sets a new label for the metric, the label type will be joined together with previous types in a tuple. Requires
    * either a `Show` instance for the label type, or a method converting the label value to a `String`.
    */
  def label[B]: LabelApply[F, A, T, N, B, L] =
    new LabelApply[F, A, T, N, B, L] {

      override def apply[C](
          name: Label.Name,
          toString: B => String
      )(implicit initLast: InitLast.Aux[T, B, C]): LabelledMetricDsl[F, A, C, Succ[N], L] = new LabelledMetricDsl(
        makeLabelledMetric,
        labelNames :+ name,
        c => f(initLast.init(c)) :+ toString(initLast.last(c))
      )

    }

  override def contramapLabels[B](f0: B => T): LabelledMetricDsl[F, A, B, N, L] = new LabelledMetricDsl(
    makeLabelledMetric,
    labelNames,
    b => f(f0(b))
  )
}
object LabelledMetricDsl {
  final class WithCallbacks[F[_], A, A0, T, N <: Nat, L[_[_], _, _]] private[internal] (
      makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L],
      makeLabelledCallback: LabelledCallbackPartiallyApplied[F, A0],
      labelNames: Sized[IndexedSeq[Label.Name], N],
      f: T => Sized[IndexedSeq[String], N]
  ) extends LabelledMetricDsl[F, A, T, N, L](makeLabelledMetric, labelNames, f)
      with CallbackStep[F, (A0, T)] {

    override protected def buildCallback: F[(A0, T)] => Resource[F, Unit] = cb =>
      makeLabelledCallback.apply(labelNames.unsized, cb)(f(_).unsized)

    /** @inheritdoc
      */
    override def label[B]: LabelApply.WithCallbacks[F, A, A0, T, N, B, L] =
      new LabelApply.WithCallbacks[F, A, A0, T, N, B, L] {

        override def apply[C](
            name: Label.Name,
            toString: B => String
        )(implicit initLast: InitLast.Aux[T, B, C]): WithCallbacks[F, A, A0, C, Succ[N], L] = new WithCallbacks(
          makeLabelledMetric,
          makeLabelledCallback,
          labelNames :+ name,
          c => f(initLast.init(c)) :+ toString(initLast.last(c))
        )

      }

    override def contramapLabels[B](f0: B => T): WithCallbacks[F, A, A0, B, N, L] =
      new WithCallbacks(makeLabelledMetric, makeLabelledCallback, labelNames, b => f(f0(b)))
  }
}

abstract class FirstLabelApply[F[_], A, B, L[_[_], _, _]] {

  def apply(name: Label.Name)(implicit show: Show[B]): LabelledMetricDsl[F, A, B, Nat._1, L] =
    apply(name, _.show)

  def apply(name: Label.Name, toString: B => String): LabelledMetricDsl[F, A, B, Nat._1, L]

}

object FirstLabelApply {

  abstract class WithCallbacks[F[_], A, A0, B, L[_[_], _, _]] extends FirstLabelApply[F, A, B, L] {

    override def apply(name: Label.Name)(implicit
        show: Show[B]
    ): LabelledMetricDsl.WithCallbacks[F, A, A0, B, Nat._1, L] =
      apply(name, _.show)

    override def apply(name: Label.Name, toString: B => String): LabelledMetricDsl.WithCallbacks[F, A, A0, B, Nat._1, L]

  }
}

class HelpStep[+A] private[prometheus4cats] (f: Metric.Help => A) {

  /** Sets the help string for the metric
    * @param help
    *   help message [[Metric.Help]]
    */
  def help(help: Metric.Help): A = f(help)

}

class TypeStep[+D[_]] private[prometheus4cats] (long: D[Long], double: D[Double]) {
  def ofLong: D[Long] = long
  def ofDouble: D[Double] = double
}

abstract class LabelApply[F[_], A, T, N <: Nat, B, L[_[_], _, _]] {

  def apply[C](name: Label.Name)(implicit
      show: Show[B],
      initLast: InitLast.Aux[T, B, C]
  ): LabelledMetricDsl[F, A, C, Succ[N], L] = apply(name, _.show)

  def apply[C](
      name: Label.Name,
      toString: B => String
  )(implicit initLast: InitLast.Aux[T, B, C]): LabelledMetricDsl[F, A, C, Succ[N], L]

}

object LabelApply {
  abstract class WithCallbacks[F[_], A, A0, T, N <: Nat, B, L[_[_], _, _]] extends LabelApply[F, A, T, N, B, L] {

    override def apply[C](name: Label.Name)(implicit
        show: Show[B],
        initLast: InitLast.Aux[T, B, C]
    ): LabelledMetricDsl.WithCallbacks[F, A, A0, C, Succ[N], L] = apply(name, _.show)

    override def apply[C](
        name: Label.Name,
        toString: B => String
    )(implicit initLast: InitLast.Aux[T, B, C]): LabelledMetricDsl.WithCallbacks[F, A, A0, C, Succ[N], L]

  }
}

private[prometheus4cats] trait LabelledMetricPartiallyApplied[F[_], A, L[_[_], _, _]] {
  def apply[B](labels: IndexedSeq[Label.Name])(f: B => IndexedSeq[String]): Resource[F, L[F, A, B]]
}

private[prometheus4cats] trait LabelledCallbackPartiallyApplied[F[_], A] {
  def apply[B](labels: IndexedSeq[Label.Name], callback: F[(A, B)])(f: B => IndexedSeq[String]): Resource[F, Unit]
}
