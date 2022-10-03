package com.permutive.metrics.internal

import cats.Show
import cats.effect.kernel.Resource
import cats.syntax.all._
import com.permutive.metrics._

class BuildStep[F[_], A] private[metrics] (fa: F[A]) {

  /** Builds the metric */
  def build: F[A] = fa

  /** Builds the metric, wrapping the effect in a `Resource` */
  def resource: Resource[F, A] = Resource.eval(build)

}

private[internal] trait FirstLabelStep[F[_], S[_[_], _, _ <: Nat]] {

  /** Sets the first label of the metric. Requires either a `Show` instance for
    * the label type, or a method converting the label value to a `String`.
    */
  def label[A]: FirstLabelApply[F, S, A]

}

private[internal] trait UnsafeLabelsStep[F[_], S[_[_], _]] {
  def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, S[F, Map[Label.Name, String]]]
}

abstract class FirstLabelApply[F[_], S[_[_], _, _ <: Nat], A] {

  def apply(name: Label.Name)(implicit show: Show[A]): S[F, A, Nat._1] =
    apply(name, _.show)

  def apply(name: Label.Name, toString: A => String): S[F, A, Nat._1]

}

class HelpStep[A] private[metrics] (f: Metric.Help => A) {

  /** Sets the help string for the metric */
  def help(help: Metric.Help): A = f(help)

}

abstract class LabelApply[F[_], T, N <: Nat, S[_[_], _, _ <: Nat], B] {

  def apply[C: InitLast.Aux[T, B, *]](name: Label.Name)(implicit
      show: Show[B]
  ): S[F, C, Succ[N]] = apply(name, _.show)

  def apply[C: InitLast.Aux[T, B, *]](
      name: Label.Name,
      toString: B => String
  ): S[F, C, Succ[N]]

}

private[metrics] trait NextLabelsStep[F[_], T, N <: Nat, S[_[_], _, _ <: Nat]] {

  /** Sets a new label for the metric, the label type will be joined together
    * with previous types in a tuple. Requires either a `Show` instance for the
    * label type, or a method converting the label value to a `String`.
    */
  def label[B]: LabelApply[F, T, N, S, B]

}
