package com.permutive.metrics.internal.histogram

import cats.data.NonEmptySeq
import com.permutive.metrics._
import com.permutive.metrics.internal._

final class HistogramDsl[F[_]] private[metrics] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
    metric: Histogram.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels,
    buckets: NonEmptySeq[Double]
) extends BuildStep[F, Histogram[F]](
      registry
        .createAndRegisterHistogram(
          prefix,
          suffix,
          metric,
          help,
          commonLabels,
          buckets
        )
    )
    with FirstLabelStep[F, LabelledHistogramDsl]
    with UnsafeLabelsStep[F, Histogram.Labelled] {

  /** @inheritdoc
    */
  override def label[A]: FirstLabelApply[F, LabelledHistogramDsl, A] =
    (name, toString) =>
      new LabelledHistogramDsl(
        registry,
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        Sized(name),
        buckets
      )(a => Sized(toString(a)))

  override def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, Histogram.Labelled[F, Map[Label.Name, String]]] =
    new BuildStep[F, Histogram.Labelled[F, Map[Label.Name, String]]](
      registry.createAndRegisterLabelledHistogram(
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        labelNames,
        buckets
      )(labels => labelNames.flatMap(labels.get))
    )

}
