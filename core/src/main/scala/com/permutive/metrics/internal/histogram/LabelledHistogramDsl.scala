package com.permutive.metrics.internal.histogram

import cats.data.NonEmptySeq
import com.permutive.metrics._
import com.permutive.metrics.internal._

final class LabelledHistogramDsl[F[_], T, N <: Nat] private[histogram] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
    metric: Histogram.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels,
    labelNames: Sized[IndexedSeq[Label.Name], N],
    buckets: NonEmptySeq[Double]
)(f: T => Sized[IndexedSeq[String], N])
    extends BuildStep[F, Histogram.Labelled[F, T]](
      registry.createAndRegisterLabelledHistogram(
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        labelNames.unsized,
        buckets
      )(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t).unsized
      )
    )
    with NextLabelsStep[F, T, N, LabelledHistogramDsl] {

  /** @inheritdoc
    */
  override def label[B]: LabelApply[F, T, N, LabelledHistogramDsl, B] =
    new LabelApply[F, T, N, LabelledHistogramDsl, B] {

      override def apply[C: InitLast.Aux[T, B, *]](
          name: Label.Name,
          toString: B => String
      ): LabelledHistogramDsl[F, C, Succ[N]] = new LabelledHistogramDsl(
        registry,
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        labelNames :+ name,
        buckets
      )(c =>
        f(InitLast[T, B, C].init(c)) :+ toString(InitLast[T, B, C].last(c))
      )

    }

}
