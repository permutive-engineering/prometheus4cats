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

package openmetrics4s.internal

import cats.{Contravariant, Functor, Show}
import cats.effect.kernel.Resource
import cats.syntax.all._
import openmetrics4s._

class BuildStep[F[_], A] private[openmetrics4s] (fa: F[A]) {

  /** Builds the metric */
  def build: F[A] = fa

  /** Builds the metric, wrapping the effect in a `Resource` */
  def resource: Resource[F, A] = Resource.eval(build)

  def map[B](f: A => B)(implicit F: Functor[F]): BuildStep[F, B] = new BuildStep[F, B](fa.map(f))

}

object BuildStep {
  implicit class GaugeTimerSyntax[F[_]: Functor: Record](bs: BuildStep[F, Gauge[F, Double]]) {
    def asTimer: BuildStep[F, Timer.Aux[F, Double, Gauge]] = bs.map(Timer.fromGauge[F])
  }

  implicit class HistogramTimerSyntax[F[_]: Functor: Record](bs: BuildStep[F, Histogram[F, Double]]) {
    def asTimer: BuildStep[F, Timer.Aux[F, Double, Histogram]] = bs.map(Timer.fromHistogram[F])
  }

  implicit class LabelledGaugeTimerSyntax[F[_]: Functor: RecordAttempt, A](
      bs: BuildStep[F, Gauge.Labelled[F, Double, A]]
  ) {
    def asTimer: BuildStep[F, Timer.Labelled.Aux[F, A, Double, Gauge.Labelled]] =
      bs.map(Timer.Labelled.fromGauge[F, A])
  }

  implicit class LabelledHistogramTimerSyntax[F[_]: Functor: RecordAttempt, A](
      bs: BuildStep[F, Histogram.Labelled[F, Double, A]]
  ) {
    def asTimer: BuildStep[F, Timer.Labelled.Aux[F, A, Double, Histogram.Labelled]] =
      bs.map(Timer.Labelled.fromHistogram[F, A])
  }

  type Aux[F[_], M[_], A] = BuildStep[F, M[A]]

  implicit def auxContravariant[F[_]: Functor, M[_]: Contravariant]: Contravariant[Aux[F, M, *]] =
    new Contravariant[Aux[F, M, *]] {
      override def contramap[A, B](fa: Aux[F, M, A])(f: B => A): Aux[F, M, B] =
        fa.map(_.contramap(f))
    }

  implicit class ContravariantSyntax[F[_]: Functor, M[_]: Contravariant, A](bs: BuildStep[F, M[A]]) {
    def contramap[B](f: B => A): F[M[B]] = bs.build.map(_.contramap(f))
  }
}

private[internal] trait FirstLabelStep[F[_], A, L[_[_], _, _]] {

  /** Sets the first label of the metric. Requires either a `Show` instance for the label type, or a method converting
    * the label value to a `String`.
    */
  def label[B]: FirstLabelApply[F, A, B, L]

}

final class MetricDsl[F[_], A, M[_[_], _], L[_[_], _, _]] private[openmetrics4s] (
    makeMetric: F[M[F, A]],
    makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L]
) extends BuildStep[F, M[F, A]](makeMetric)
    with FirstLabelStep[F, A, L]
    with UnsafeLabelsStep[F, A, L] {

  /** @inheritdoc
    */
  override def label[B]: FirstLabelApply[F, A, B, L] =
    (name, toString) =>
      new LabelledMetricDsl(
        makeLabelledMetric,
        Sized(name),
        a => Sized(toString(a))
      )

  override def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, L[F, A, Map[Label.Name, String]]] =
    new BuildStep[F, L[F, A, Map[Label.Name, String]]](
      makeLabelledMetric(
        labelNames
      )(labels => labelNames.flatMap(labels.get))
    )
}

final class LabelledMetricDsl[F[_], A, T, N <: Nat, L[_[_], _, _]] private[internal] (
    makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L],
    labelNames: Sized[IndexedSeq[Label.Name], N],
    f: T => Sized[IndexedSeq[String], N]
) extends BuildStep[F, L[F, A, T]](
      makeLabelledMetric(labelNames.unsized)(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t).unsized
      )
    )
    with NextLabelsStep[F, A, T, N, L] {

  /** @inheritdoc
    */
  override def label[B]: LabelApply[F, A, T, N, B, L] =
    new LabelApply[F, A, T, N, B, L] {

      override def apply[C: InitLast.Aux[T, B, *]](
          name: Label.Name,
          toString: B => String
      ): LabelledMetricDsl[F, A, C, Succ[N], L] = new LabelledMetricDsl(
        makeLabelledMetric,
        labelNames :+ name,
        c => f(InitLast[T, B, C].init(c)) :+ toString(InitLast[T, B, C].last(c))
      )

    }

}

private[internal] trait UnsafeLabelsStep[F[_], A, L[_[_], _, _]] {

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
  ): BuildStep[F, L[F, A, Map[Label.Name, String]]]

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
}

abstract class FirstLabelApply[F[_], A, B, L[_[_], _, _]] {

  def apply(name: Label.Name)(implicit show: Show[B]): LabelledMetricDsl[F, A, B, Nat._1, L] =
    apply(name, _.show)

  def apply(name: Label.Name, toString: B => String): LabelledMetricDsl[F, A, B, Nat._1, L]

}

class HelpStep[A] private[openmetrics4s] (f: Metric.Help => A) {

  /** Sets the help string for the metric
    * @param help
    *   help message [[Metric.Help]]
    */
  def help(help: Metric.Help): A = f(help)

}

class TypeStep[D[_]] private[openmetrics4s] (long: D[Long], double: D[Double]) {
  def ofLong: D[Long] = long
  def ofDouble: D[Double] = double
}

abstract class LabelApply[F[_], A, T, N <: Nat, B, L[_[_], _, _]] {

  def apply[C: InitLast.Aux[T, B, *]](name: Label.Name)(implicit
      show: Show[B]
  ): LabelledMetricDsl[F, A, C, Succ[N], L] = apply(name, _.show)

  def apply[C: InitLast.Aux[T, B, *]](
      name: Label.Name,
      toString: B => String
  ): LabelledMetricDsl[F, A, C, Succ[N], L]

}

private[openmetrics4s] trait NextLabelsStep[F[_], A, T, N <: Nat, L[_[_], _, _]] {

  /** Sets a new label for the metric, the label type will be joined together with previous types in a tuple. Requires
    * either a `Show` instance for the label type, or a method converting the label value to a `String`.
    */
  def label[B]: LabelApply[F, A, T, N, B, L]

}

private[openmetrics4s] trait LabelledMetricPartiallyApplied[F[_], A, L[_[_], _, _]] {
  def apply[B](labels: IndexedSeq[Label.Name])(f: B => IndexedSeq[String]): F[L[F, A, B]]
}
