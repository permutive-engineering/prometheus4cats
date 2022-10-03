package com.permutive.metrics.internal.counter

import com.permutive.metrics._
import com.permutive.metrics.internal._

final class CounterDsl[F[_]] private[metrics] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
    metric: Counter.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels
) extends BuildStep[F, Counter[F]](
      registry
        .createAndRegisterCounter(prefix, suffix, metric, help, commonLabels)
    )
    with FirstLabelStep[F, LabelledCounterDsl]
    with UnsafeLabelsStep[F, Counter.Labelled] {

  /** @inheritdoc
    */
  override def label[A]: FirstLabelApply[F, LabelledCounterDsl, A] =
    (name, toString) =>
      new LabelledCounterDsl(
        registry,
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        Sized(name),
        a => Sized(toString(a))
      )

  override def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, Counter.Labelled[F, Map[Label.Name, String]]] =
    new BuildStep[F, Counter.Labelled[F, Map[Label.Name, String]]](
      registry.createAndRegisterLabelledCounter(
        prefix,
        suffix,
        metric,
        help,
        commonLabels,
        labelNames
      )(labels => labelNames.flatMap(labels.get))
    )
}
