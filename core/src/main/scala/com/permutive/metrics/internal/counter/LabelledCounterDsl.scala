package com.permutive.metrics.internal.counter

import com.permutive.metrics._
import com.permutive.metrics.internal._

final class LabelledCounterDsl[F[_], T, N <: Nat] private[counter] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
    metric: Counter.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels,
    labelNames: Sized[IndexedSeq[Label.Name], N]
)(f: T => Sized[IndexedSeq[String], N])
    extends BuildStep[F, Counter.Labelled[F, T]](
      registry.createAndRegisterLabelledCounter(
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        labelNames.unsized
      )(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t).unsized
      )
    )
    with NextLabelsStep[F, T, N, LabelledCounterDsl] {

  /** @inheritdoc
    */
  override def label[B]: LabelApply[F, T, N, LabelledCounterDsl, B] =
    new LabelApply[F, T, N, LabelledCounterDsl, B] {

      override def apply[C: InitLast.Aux[T, B, *]](
          name: Label.Name,
          toString: B => String
      ): LabelledCounterDsl[F, C, Succ[N]] = new LabelledCounterDsl(
        registry,
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        labelNames :+ name
      )(c =>
        f(InitLast[T, B, C].init(c)) :+ toString(InitLast[T, B, C].last(c))
      )

    }

}
