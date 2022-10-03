package com.permutive.metrics.internal.gauge

import com.permutive.metrics._
import com.permutive.metrics.internal._

final class LabelledGaugeDsl[F[_], T, N <: Nat] private[gauge] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
    metric: Gauge.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels,
    labelNames: Sized[IndexedSeq[Label.Name], N],
    f: T => Sized[IndexedSeq[String], N]
) extends BuildStep[F, Gauge.Labelled[F, T]](
      registry.createAndRegisterLabelledGauge(
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
    with NextLabelsStep[F, T, N, LabelledGaugeDsl] {

  /** @inheritdoc
    */
  override def label[B]: LabelApply[F, T, N, LabelledGaugeDsl, B] =
    new LabelApply[F, T, N, LabelledGaugeDsl, B] {

      override def apply[C: InitLast.Aux[T, B, *]](
          name: Label.Name,
          toString: B => String
      ): LabelledGaugeDsl[F, C, Succ[N]] = new LabelledGaugeDsl(
        registry,
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        labelNames :+ name,
        c => f(InitLast[T, B, C].init(c)) :+ toString(InitLast[T, B, C].last(c))
      )

    }

}
