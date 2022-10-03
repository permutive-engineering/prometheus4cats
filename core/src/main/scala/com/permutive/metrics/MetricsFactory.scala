package com.permutive.metrics

import cats.{Applicative, Monad, ~>}
import com.permutive.metrics.Metric.CommonLabels
import com.permutive.metrics.internal._
import com.permutive.metrics.internal.counter.CounterDsl
import com.permutive.metrics.internal.gauge.GaugeDsl
import com.permutive.metrics.internal.histogram.BucketDsl

sealed abstract class MetricsFactory[F[_]](
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
    commonLabels: CommonLabels
) {
  def mapK[G[_]: Monad: RecordAttempt](fk: F ~> G): MetricsFactory[G] =
    new MetricsFactory[G](
      MetricsRegistry.mapK(registry, fk),
      prefix,
      suffix,
      commonLabels
    ) {}

  def gauge(name: Gauge.Name): HelpStep[GaugeDsl[F]] = new HelpStep(
    new GaugeDsl[F](registry, prefix, suffix, name, _, commonLabels)
  )

  def counter(name: Counter.Name): HelpStep[CounterDsl[F]] =
    new HelpStep[CounterDsl[F]](
      new CounterDsl[F](registry, prefix, suffix, name, _, commonLabels)
    )

  def histogram(name: Histogram.Name): HelpStep[BucketDsl[F]] =
    new HelpStep[BucketDsl[F]](
      new BucketDsl[F](registry, prefix, suffix, name, _, commonLabels)
    )
}

object MetricsFactory {
  def noop[F[_]: Applicative]: MetricsFactory[F] =
    new MetricsFactory[F](MetricsRegistry.noop, None, None, Map.empty) {}

  class Builder[F[_]] private[metrics] (
      prefix: Option[Metric.Prefix] = None,
      suffix: Option[Metric.Suffix] = None,
      commonLabels: CommonLabels = Map.empty
  ) {
    def withPrefix(prefix: Metric.Prefix): Builder[F] =
      new Builder[F](Some(prefix), suffix, commonLabels)

    def withSuffix(suffix: Metric.Suffix): Builder[F] =
      new Builder[F](prefix, Some(suffix), commonLabels)

    def addCommonLabel(name: Label.Name, value: String): Builder[F] =
      new Builder[F](prefix, suffix, commonLabels.updated(name, value))

    def withCommonLabels(labels: Map[Label.Name, String]): Builder[F] =
      new Builder[F](prefix, suffix, labels)

    def build(registry: MetricsRegistry[F]): MetricsFactory[F] =
      new MetricsFactory[F](registry, prefix, suffix, commonLabels) {}

    def noop(implicit F: Applicative[F]): MetricsFactory[F] =
      MetricsFactory.noop[F]
  }

  def builder[F[_]] = new Builder[F]()
}
