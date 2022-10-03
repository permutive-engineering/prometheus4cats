package com.permutive.metrics.internal.gauge

import com.permutive.metrics._
import com.permutive.metrics.internal._

final class GaugeDsl[F[_]] private[metrics] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
    metric: Gauge.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels
) extends BuildStep[F, Gauge[F]](
      registry
        .createAndRegisterGauge(prefix, suffix, metric, help, commonLabels)
    )
    with FirstLabelStep[F, LabelledGaugeDsl]
    with UnsafeLabelsStep[F, Gauge.Labelled] {

  /** @inheritdoc
    */
  override def label[A]: FirstLabelApply[F, LabelledGaugeDsl, A] =
    (name, toString) =>
      new LabelledGaugeDsl(
        registry,
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        Sized(name),
        a => Sized(toString(a))
      )

  def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, Gauge.Labelled[F, Map[Label.Name, String]]] =
    new BuildStep[F, Gauge.Labelled[F, Map[Label.Name, String]]](
      registry.createAndRegisterLabelledGauge(
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        labelNames
      )(labels => labelNames.flatMap(labels.get))
    )

}
