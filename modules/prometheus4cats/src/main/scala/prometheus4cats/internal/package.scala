/*
 * Copyright 2022-2026 Permutive Ltd. <https://permutive.com>
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

import scala.concurrent.duration.FiniteDuration

import cats.Contravariant
import cats.FlatMap
import cats.Show
import cats.effect.kernel.Clock
import cats.effect.kernel.MonadCancelThrow
import cats.effect.kernel.Resource
import cats.syntax.all._

import prometheus4cats.OutcomeRecorder.Status
import prometheus4cats.internal.histogram.BucketDsl
import prometheus4cats.internal.summary.SummaryDsl
import prometheus4cats._
import prometheus4cats.internal.InitLast.Aux

trait BuildStep[F[_], A] { self =>

  /** Builds the metric */
  def build: Resource[F, A]

  /** Unsafely builds the metric, but does not deallocate */
  def unsafeBuild(implicit F: MonadCancelThrow[F]): F[A] = build.allocated.map(_._1)

  def map[B](f: A => B): BuildStep[F, B] = new BuildStep[F, B] {

    override def build: Resource[F, B] = self.build.map(f)

  }

}

object BuildStep {

  private[prometheus4cats] def apply[F[_], A](fa: Resource[F, A]): BuildStep[F, A] = new BuildStep[F, A] {

    override def build: Resource[F, A] = fa

  }

  implicit class DoubleGaugeSyntax[F[_]](bs: BuildStep[F, Gauge[F, Double, Unit]]) {

    def asTimer: BuildStep[F, Timer.Aux[F, Unit, Gauge]] =
      bs.map(Timer.fromGauge[F, Unit])

    def asCurrentTimeRecorder(implicit F: FlatMap[F], clock: Clock[F]): BuildStep[F, CurrentTimeRecorder[F, Unit]] =
      asCurrentTimeRecorder(_.toSeconds.toDouble)

    def asCurrentTimeRecorder(
        f: FiniteDuration => Double
    )(implicit F: FlatMap[F], clock: Clock[F]): BuildStep[F, CurrentTimeRecorder[F, Unit]] =
      bs.map(CurrentTimeRecorder.fromDoubleGauge(_)(f))

  }

  implicit class LongGaugeSyntax[F[_]](bs: BuildStep[F, Gauge[F, Long, Unit]]) {

    def asCurrentTimeRecorder(implicit F: FlatMap[F], clock: Clock[F]): BuildStep[F, CurrentTimeRecorder[F, Unit]] =
      asCurrentTimeRecorder(_.toSeconds)

    def asCurrentTimeRecorder(
        f: FiniteDuration => Long
    )(implicit F: FlatMap[F], clock: Clock[F]): BuildStep[F, CurrentTimeRecorder[F, Unit]] =
      bs.map(CurrentTimeRecorder.fromLongGauge(_)(f))

  }

  implicit class DoubleHistogramSyntax[F[_]](bs: BuildStep[F, Histogram[F, Double, Unit]]) {

    def asTimer: BuildStep[F, Timer.Aux[F, Unit, Histogram]] =
      bs.map(Timer.fromHistogram[F, Unit])

  }

  implicit class DoubleLabelledGaugeSyntax[F[_], A](
      bs: BuildStep[F, Gauge[F, Double, A]]
  ) {

    def asTimer: BuildStep[F, Timer.Aux[F, A, Gauge]] =
      bs.map(Timer.fromGauge[F, A])

    def asCurrentTimeRecorder(implicit
        F: FlatMap[F],
        clock: Clock[F]
    ): BuildStep[F, CurrentTimeRecorder[F, A]] = asCurrentTimeRecorder(
      _.toSeconds.toDouble
    )

    def asCurrentTimeRecorder(
        f: FiniteDuration => Double
    )(implicit F: FlatMap[F], clock: Clock[F]): BuildStep[F, CurrentTimeRecorder[F, A]] =
      bs.map(CurrentTimeRecorder.fromDoubleGauge(_)(f))

  }

  implicit class LongLabelledGaugeSyntax[F[_], A](
      bs: BuildStep[F, Gauge[F, Long, A]]
  ) {

    def asCurrentTimeRecorder(implicit
        F: FlatMap[F],
        clock: Clock[F]
    ): BuildStep[F, CurrentTimeRecorder[F, A]] = asCurrentTimeRecorder(_.toSeconds)

    def asCurrentTimeRecorder(
        f: FiniteDuration => Long
    )(implicit F: FlatMap[F], clock: Clock[F]): BuildStep[F, CurrentTimeRecorder[F, A]] =
      bs.map(CurrentTimeRecorder.fromLongGauge(_)(f))

  }

  implicit class DoubleLabelledHistogramSyntax[F[_], A](
      bs: BuildStep[F, Histogram[F, Double, A]]
  ) {

    def asTimer: BuildStep[F, Timer.Aux[F, A, Histogram]] =
      bs.map(Timer.fromHistogram[F, A])

  }

  implicit class DoubleSummarySyntax[F[_]](bs: BuildStep[F, Summary[F, Double, Unit]]) {

    def asTimer: BuildStep[F, Timer.Aux[F, Unit, Summary]] =
      bs.map(Timer.fromSummary[F, Unit])

  }

  implicit class DoubleLabelledSummarySyntax[F[_], A](
      bs: BuildStep[F, Summary[F, Double, A]]
  ) {

    def asTimer: BuildStep[F, Timer.Aux[F, A, Summary]] =
      bs.map(Timer.fromSummary[F, A])

  }

  type Aux[F[_], M[_], A] = BuildStep[F, M[A]]

  implicit def auxLabelsContravariant[F[_], M[_]: LabelsContravariant]: LabelsContravariant[Aux[F, M, *]] =
    new LabelsContravariant[Aux[F, M, *]] {

      override def contramapLabels[A, B](fa: Aux[F, M, A])(f: B => A): Aux[F, M, B] =
        fa.map(LabelsContravariant[M].contramapLabels(_)(f))

    }

  implicit class LabelsContravariantSyntax[F[_], M[_]: LabelsContravariant, A](bs: BuildStep[F, M[A]]) {

    def contramapLabels[B](f: B => A): BuildStep[F, M[B]] = bs.map(LabelsContravariant[M].contramapLabels(_)(f))

  }

  implicit def auxContravariant[F[_], M[_]: Contravariant]: Contravariant[Aux[F, M, *]] =
    new Contravariant[Aux[F, M, *]] {

      override def contramap[A, B](fa: Aux[F, M, A])(f: B => A): Aux[F, M, B] = fa.map(_.contramap(f))

    }

  implicit class ContravariantSyntax[F[_], M[_]: Contravariant, A](bs: BuildStep[F, M[A]]) {

    def contramap[B](f: B => A): BuildStep[F, M[B]] = bs.map(_.contramap(f))

  }

}

/** Mixin adding `.withNative` for promoting a classic-only histogram declaration into a dual-mode (classic + native
  * exponential) one. The classic bucket boundaries supplied via `.buckets(...)` are preserved as-is; native exponential
  * is added with the supplied [[NativeHistogram]] config (defaults if omitted).
  *
  * Calling `.withNative` again on a promoted DSL is not supported (dual-mode IS the maximum). Order in the chain:
  * `.buckets(...)` → optional `.withNative` → `.label(...)` → `.build`.
  */
trait HistogramWithNativeOps[F[_], A] {

  protected def makeWithNativeMetric: NativeHistogram => LabelledMetricPartiallyApplied[F, A, Histogram]

  /** Promote this histogram to dual-mode (classic + native exponential), using [[NativeHistogram.Default]] tuning. */
  def withNative: MetricDsl[F, A, Histogram] = withNative(NativeHistogram.Default)

  /** Promote this histogram to dual-mode (classic + native exponential), with the supplied native tuning config. */
  def withNative(cfg: NativeHistogram): MetricDsl[F, A, Histogram] =
    new MetricDsl(makeWithNativeMetric(cfg))

}

object HistogramMetricDsl {

  /** Concrete histogram DSL for the base [[MetricFactory]] path: a `MetricDsl[F, A, Histogram]` plus `.withNative`. */
  final class Plain[F[_], A] private[prometheus4cats] (
      classicMakeMetric: LabelledMetricPartiallyApplied[F, A, Histogram],
      protected val makeWithNativeMetric: NativeHistogram => LabelledMetricPartiallyApplied[F, A, Histogram]
  ) extends MetricDsl[F, A, Histogram](classicMakeMetric)
      with HistogramWithNativeOps[F, A]

}

class MetricDsl[F[_], A, L[_[_], _, _]] private[prometheus4cats] (
    private[internal] val makeMetric: LabelledMetricPartiallyApplied[F, A, L]
) extends BuildStep[F, L[F, A, Unit]] {

  override def build: Resource[F, L[F, A, Unit]] =
    makeMetric.apply(IndexedSeq.empty)((_: Unit) => IndexedSeq.empty)

  /** Sets the first label of the metric. Requires either a `Show` instance for the label type, or a method converting
    * the label value to a `String`.
    */
  def label[B]: FirstLabelApply[F, A, B, L] =
    (name, toString) => new LabelledMetricDsl(makeMetric, IndexedSeq(name), a => IndexedSeq(toString(a)))

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
      makeMetric(
        labelNames
      )(labels => labelNames.collect(labels))
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

  /** Sets the first labels for a metric coming from a single type. Takes a collection of label name and a function
    * converting some label object `B` to a string pairs.
    *
    * This is useful where a single type `B` translates to multiple labels.
    *
    * @tparam B
    *   type to convert into labels
    * @tparam N
    *   size of the label collection
    * @param labelNames
    *   collection of labels name & function to convert `B` in to a label value pairs
    */
  def labels[B](labels: (Label.Name, B => Label.Value)*): LabelledMetricDsl[F, A, B, L] = {
    val labelNames  = labels.toIndexedSeq.map(_._1)
    val labelValues = labels.toIndexedSeq.map(_._2)

    new LabelledMetricDsl(makeMetric, labelNames, b => labelValues.map(_(b).value))
  }

  def labelsFrom[B](implicit encoder: Label.Encoder[B]): LabelledMetricDsl[F, A, B, L] = labels(encoder.toLabels: _*)

}

object MetricDsl {

  implicit class CounterSyntax[F[_], A](dsl: MetricDsl[F, A, Counter]) {

    def asOutcomeRecorder: BuildStep[F, OutcomeRecorder.Aux[F, A, Unit, Counter]] =
      BuildStep(
        dsl
          .makeMetric[Status](IndexedSeq(Label.Name.outcomeStatus))(status => IndexedSeq(status.show))
          .map(_.contramapLabels[(Unit, OutcomeRecorder.Status)](_._2))
          .map(OutcomeRecorder.fromCounter)
      )

    def contramap[B](f: B => A): BuildStep[F, Counter[F, B, Unit]] = dsl.map(_.contramap(f))

  }

  implicit class GaugeSyntax[F[_], A](dsl: MetricDsl[F, A, Gauge]) {

    def asOutcomeRecorder(implicit F: MonadCancelThrow[F]): BuildStep[F, OutcomeRecorder.Aux[F, A, Unit, Gauge]] =
      BuildStep(
        dsl
          .makeMetric[Status](IndexedSeq(Label.Name.outcomeStatus))(status => IndexedSeq(status.show))
          .map(_.contramapLabels[(Unit, OutcomeRecorder.Status)](_._2))
          .map(OutcomeRecorder.fromGauge(_))
      )

    def contramap[B](f: B => A): BuildStep[F, Gauge[F, B, Unit]] = dsl.map(_.contramap(f))

  }

}

abstract class BaseLabelsBuildStep[F[_], A, T, L[_[_], _, _]](
    fa: Resource[F, L[F, A, T]]
) extends BuildStep[F, L[F, A, T]] {

  protected[internal] val makeMetric: LabelledMetricPartiallyApplied[F, A, L]

  protected[internal] val labelNames: IndexedSeq[Label.Name]

  protected[internal] val f: T => IndexedSeq[String]

  def contramapLabels[B](f: B => T): BaseLabelsBuildStep[F, A, B, L]

  override def build: Resource[F, L[F, A, T]] = fa

}

object BaseLabelsBuildStep {

  implicit final class CounterSyntax[F[_], A, T](
      dsl: BaseLabelsBuildStep[F, A, T, Counter]
  ) {

    def asOutcomeRecorder: BuildStep[F, OutcomeRecorder.Aux[F, A, T, Counter]] = BuildStep(
      dsl
        .makeMetric[(T, Status)](dsl.labelNames :+ Label.Name.outcomeStatus) { case (t, status) =>
          dsl.f(t) :+ status.show
        }
        .map(OutcomeRecorder.fromCounter)
    )

    def contramap[B](f: B => A): BuildStep[F, Counter[F, B, T]] = dsl.map(_.contramap(f))

  }

  implicit final class GaugeSyntax[F[_], A, T](
      dsl: BaseLabelsBuildStep[F, A, T, Gauge]
  ) {

    def asOutcomeRecorder(implicit
        F: MonadCancelThrow[F]
    ): BuildStep[F, OutcomeRecorder.Aux[F, A, T, Gauge]] = BuildStep(
      dsl
        .makeMetric[(T, Status)](dsl.labelNames :+ Label.Name.outcomeStatus) { case (t, status) =>
          dsl.f(t) :+ status.show
        }
        .map(OutcomeRecorder.fromGauge(_))
    )

    def contramap[B](f: B => A): BuildStep[F, Gauge[F, B, T]] = dsl.map(_.contramap(f))

  }

  implicit final class HistogramSyntax[F[_], A, T](
      dsl: BaseLabelsBuildStep[F, A, T, Histogram]
  ) {

    def contramap[B](f: B => A): BuildStep[F, Histogram[F, B, T]] = dsl.map(_.contramap(f))

  }

  implicit final class SummarySyntax[F[_], A, T](
      dsl: BaseLabelsBuildStep[F, A, T, Summary]
  ) {

    def contramap[B](f: B => A): BuildStep[F, Summary[F, B, T]] = dsl.map(_.contramap(f))

  }

}

class LabelsBuildStep[F[_], A, T, L[_[_], _, _]] private[internal] (
    protected[internal] val makeMetric: LabelledMetricPartiallyApplied[F, A, L],
    protected[internal] val labelNames: IndexedSeq[Label.Name],
    protected[internal] val f: T => IndexedSeq[String]
) extends BaseLabelsBuildStep[F, A, T, L](
      makeMetric(labelNames)(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t)
      )
    ) {

  override def contramapLabels[B](f0: B => T): LabelsBuildStep[F, A, B, L] = new LabelsBuildStep[F, A, B, L](
    makeMetric,
    labelNames,
    b => f(f0(b))
  )

}

class LabelledMetricDsl[F[_], A, T, L[_[_], _, _]] private[internal] (
    protected[internal] val makeMetric: LabelledMetricPartiallyApplied[F, A, L],
    protected[internal] val labelNames: IndexedSeq[Label.Name],
    protected[internal] val f: T => IndexedSeq[String]
) extends BaseLabelsBuildStep[F, A, T, L](
      makeMetric(labelNames)(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t)
      )
    ) {

  /** Sets a new label for the metric, the label type will be joined together with previous types in a tuple. Requires
    * either a `Show` instance for the label type, or a method converting the label value to a `String`.
    */
  def label[B]: LabelApply[F, A, T, B, L] =
    new LabelApply[F, A, T, B, L] {

      override def apply[C](
          name: Label.Name,
          toString: B => String
      )(implicit initLast: InitLast.Aux[T, B, C]): LabelledMetricDsl[F, A, C, L] = new LabelledMetricDsl(
        makeMetric,
        labelNames :+ name,
        c => f(initLast.init(c)) :+ toString(initLast.last(c))
      )

    }

  /** Sets a new set of labels for the metric coming from a single type. Takes a collection of label name and a function
    * converting some label object `B` to a string pairs.
    *
    * This is useful where a single type `B` translates to multiple labels.
    *
    * @tparam B
    *   type to convert into labels
    * @tparam N
    *   size of the label collection
    * @param labelNames
    *   collection of labels name & function to convert `B` in to a label value pairs
    */
  def labels[B]: LabelsApply[F, A, T, B, L] =
    new LabelsApply[F, A, T, B, L] {

      override def apply[C](
          labels: (Label.Name, B => Label.Value)*
      )(implicit initLast: InitLast.Aux[T, B, C]): LabelledMetricDsl[F, A, C, L] = new LabelledMetricDsl(
        makeMetric,
        labelNames ++ labels.map(_._1),
        c => f(initLast.init(c)) ++ labels.map(_._2(initLast.last(c)).value)
      )

    }

  def labelsFrom[B]: LabelsFromApply[F, A, T, B, L] = new LabelsFromApply[F, A, T, B, L] {

    override def apply[C](implicit encoder: Label.Encoder[B], initLast: Aux[T, B, C]): LabelledMetricDsl[F, A, C, L] = {
      val labels = encoder.toLabels

      new LabelledMetricDsl(
        makeMetric,
        labelNames ++ labels.map(_._1),
        c => f(initLast.init(c)) ++ labels.map(_._2(initLast.last(c)).value)
      )
    }

  }

  override def contramapLabels[B](f0: B => T): LabelledMetricDsl[F, A, B, L] = new LabelledMetricDsl(
    makeMetric,
    labelNames,
    b => f(f0(b))
  )

}

abstract private[internal] class FirstLabelApply[F[_], A, B, L[_[_], _, _]] {

  def apply(name: Label.Name)(implicit show: Show[B]): LabelledMetricDsl[F, A, B, L] =
    apply(name, _.show)

  def apply(name: Label.Name, toString: B => String): LabelledMetricDsl[F, A, B, L]

}

class HelpStep[+A] private[prometheus4cats] (f: Metric.Help => A) {

  /** Sets the help string for the metric
    * @param help
    *   help message [[Metric.Help]]
    */
  def help(help: Metric.Help): A = f(help)

}

/** Per-metric-kind replacement for the v6 `TypeStep[D[_]]` shim. Each factory method (`counter`, `gauge`, `histogram`,
  * `summary`) returns a concrete subclass of [[HelpStep]] specialised to the `Double`-valued DSL, so `.help(...)`
  * chains directly without an `.ofDouble` step. The deprecated `.ofLong` / `.ofDouble` accessors are preserved on
  * the concrete factory steps below for one release as plain methods — no higher-kinded implicit conversion is
  * needed, so consumer call sites compile without `-language:higherKinds`.
  */
class CounterFactoryStep[F[_]] private[prometheus4cats] (
    longDsl: HelpStep[MetricDsl[F, Long, Counter]],
    doubleDsl: HelpStep[MetricDsl[F, Double, Counter]]
) extends HelpStep[MetricDsl[F, Double, Counter]](h => doubleDsl.help(h)) {

  @deprecated("Double is now the default — call .help directly", "6.0.0")
  def ofDouble: HelpStep[MetricDsl[F, Double, Counter]] = doubleDsl

  @deprecated("Use .help(...).contramap[Long](_.toDouble) on the resulting DSL", "6.0.0")
  def ofLong: HelpStep[MetricDsl[F, Long, Counter]] = longDsl

}

class GaugeFactoryStep[F[_]] private[prometheus4cats] (
    longDsl: HelpStep[MetricDsl[F, Long, Gauge]],
    doubleDsl: HelpStep[MetricDsl[F, Double, Gauge]]
) extends HelpStep[MetricDsl[F, Double, Gauge]](h => doubleDsl.help(h)) {

  @deprecated("Double is now the default — call .help directly", "6.0.0")
  def ofDouble: HelpStep[MetricDsl[F, Double, Gauge]] = doubleDsl

  @deprecated("Use .help(...).contramap[Long](_.toDouble) on the resulting DSL", "6.0.0")
  def ofLong: HelpStep[MetricDsl[F, Long, Gauge]] = longDsl

}

class HistogramFactoryStep[F[_]] private[prometheus4cats] (
    longDsl: HelpStep[BucketDsl[HistogramMetricDsl[F, Long], Long]],
    doubleDsl: HelpStep[BucketDsl[HistogramMetricDsl[F, Double], Double]]
) extends HelpStep[BucketDsl[HistogramMetricDsl[F, Double], Double]](h => doubleDsl.help(h)) {

  @deprecated("Double is now the default — call .help directly", "6.0.0")
  def ofDouble: HelpStep[BucketDsl[HistogramMetricDsl[F, Double], Double]] = doubleDsl

  @deprecated("Use .help(...).buckets(...).contramap[Long](_.toDouble) on the resulting DSL", "6.0.0")
  def ofLong: HelpStep[BucketDsl[HistogramMetricDsl[F, Long], Long]] = longDsl

}

/** Native histograms are `Double`-only in spirit — buckets are real-valued regardless of input
  * type — but the deprecated `.ofLong` / `.ofDouble` accessors are preserved here for one release
  * to keep source-compat with v5-style call sites. Callers should drop the `.ofDouble` step (the
  * default `.help` path resolves to the same `Double` DSL) and replace `.ofLong` with
  * `.help(...).contramap[Long](_.toDouble)`.
  */
class NativeHistogramFactoryStep[F[_]] private[prometheus4cats] (
    longDsl: HelpStep[MetricDsl[F, Long, Histogram]],
    doubleDsl: HelpStep[MetricDsl[F, Double, Histogram]]
) extends HelpStep[MetricDsl[F, Double, Histogram]](h => doubleDsl.help(h)) {

  @deprecated("Double is now the default — call .help directly", "6.0.0")
  def ofDouble: HelpStep[MetricDsl[F, Double, Histogram]] = doubleDsl

  @deprecated("Use .help(...).contramap[Long](_.toDouble) on the resulting DSL", "6.0.0")
  def ofLong: HelpStep[MetricDsl[F, Long, Histogram]] = longDsl

}

class SummaryFactoryStep[F[_]] private[prometheus4cats] (
    longDsl: HelpStep[SummaryDsl.Base[F, Long]],
    doubleDsl: HelpStep[SummaryDsl.Base[F, Double]]
) extends HelpStep[SummaryDsl.Base[F, Double]](h => doubleDsl.help(h)) {

  @deprecated("Double is now the default — call .help directly", "6.0.0")
  def ofDouble: HelpStep[SummaryDsl.Base[F, Double]] = doubleDsl

  @deprecated("Use .help(...).contramap[Long](_.toDouble) on the resulting DSL", "6.0.0")
  def ofLong: HelpStep[SummaryDsl.Base[F, Long]] = longDsl

}

abstract class LabelApply[F[_], A, T, B, L[_[_], _, _]] {

  def apply[C](name: Label.Name)(implicit
      show: Show[B],
      initLast: InitLast.Aux[T, B, C]
  ): LabelledMetricDsl[F, A, C, L] = apply(name, _.show)

  def apply[C](
      name: Label.Name,
      toString: B => String
  )(implicit initLast: InitLast.Aux[T, B, C]): LabelledMetricDsl[F, A, C, L]

}

abstract class LabelsApply[F[_], A, T, B, L[_[_], _, _]] {

  def apply[C](labels: (Label.Name, B => Label.Value)*)(implicit
      initLast: InitLast.Aux[T, B, C]
  ): LabelledMetricDsl[F, A, C, L]

}

abstract class LabelsFromApply[F[_], A, T, B, L[_[_], _, _]] {

  def apply[C](implicit encoder: Label.Encoder[B], initLast: InitLast.Aux[T, B, C]): LabelledMetricDsl[F, A, C, L]

}

trait LabelledMetricPartiallyApplied[F[_], A, L[_[_], _, _]] {

  def apply[B](labels: IndexedSeq[Label.Name])(f: B => IndexedSeq[String]): Resource[F, L[F, A, B]]

}
