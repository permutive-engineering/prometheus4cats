package com.permutive.metrics

import cats.data.NonEmptySeq
import cats.{Applicative, Monad, ~>}
import com.permutive.metrics.Metric.CommonLabels
import cats.syntax.functor._

trait MetricsRegistry[F[_]] {
  def createAndRegisterCounter(
      prefix: Option[Metric.Prefix],
      suffix: Option[Metric.Suffix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Counter[F]]

  def createAndRegisterLabelledCounter[A](
      prefix: Option[Metric.Prefix],
      suffix: Option[Metric.Suffix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, A]]

  def createAndRegisterGauge(
      prefix: Option[Metric.Prefix],
      suffix: Option[Metric.Suffix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Gauge[F]]

  def createAndRegisterLabelledGauge[A](
      prefix: Option[Metric.Prefix],
      suffix: Option[Metric.Suffix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, A]]

  def createAndRegisterHistogram(
      prefix: Option[Metric.Prefix],
      suffix: Option[Metric.Suffix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double]
  ): F[Histogram[F]]

  def createAndRegisterLabelledHistogram[A](
      prefix: Option[Metric.Prefix],
      suffix: Option[Metric.Suffix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, A]]
}

object MetricsRegistry {
  def noop[F[_]](implicit F: Applicative[F]): MetricsRegistry[F] =
    new MetricsRegistry[F] {
      override def createAndRegisterCounter(
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): F[Counter[F]] = F.pure(Counter.noop)

      override def createAndRegisterLabelledCounter[A](
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, A]] =
        F.pure(Counter.Labelled.noop)

      override def createAndRegisterGauge(
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): F[Gauge[F]] =
        F.pure(Gauge.noop)

      override def createAndRegisterLabelledGauge[A](
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, A]] =
        F.pure(Gauge.Labelled.noop)

      override def createAndRegisterHistogram(
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Double]
      ): F[Histogram[F]] = F.pure(Histogram.noop)

      override def createAndRegisterLabelledHistogram[A](
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, A]] =
        F.pure(Histogram.Labelled.noop)
    }

  private[metrics] def mapK[F[_], G[_]: Monad: RecordAttempt](
      self: MetricsRegistry[F],
      fk: F ~> G
  ): MetricsRegistry[G] =
    new MetricsRegistry[G] {
      override def createAndRegisterCounter(
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): G[Counter[G]] = fk(
        self.createAndRegisterCounter(prefix, suffix, name, help, commonLabels)
      ).map(_.mapK(fk))

      override def createAndRegisterLabelledCounter[A](
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): G[Counter.Labelled[G, A]] = fk(
        self.createAndRegisterLabelledCounter(
          prefix,
          suffix,
          name,
          help,
          commonLabels,
          labelNames
        )(f)
      ).map(_.mapK(fk))

      override def createAndRegisterGauge(
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): G[Gauge[G]] = fk(
        self.createAndRegisterGauge(prefix, suffix, name, help, commonLabels)
      ).map(_.mapK(fk))

      override def createAndRegisterLabelledGauge[A](
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): G[Gauge.Labelled[G, A]] = fk(
        self.createAndRegisterLabelledGauge(
          prefix,
          suffix,
          name,
          help,
          commonLabels,
          labelNames
        )(f)
      ).map(_.mapK(fk))

      override def createAndRegisterHistogram(
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Double]
      ): G[Histogram[G]] = fk(
        self.createAndRegisterHistogram(
          prefix,
          suffix,
          name,
          help,
          commonLabels,
          buckets
        )
      ).map(_.mapK(fk))

      override def createAndRegisterLabelledHistogram[A](
          prefix: Option[Metric.Prefix],
          suffix: Option[Metric.Suffix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): G[Histogram.Labelled[G, A]] = fk(
        self.createAndRegisterLabelledHistogram(
          prefix,
          suffix,
          name,
          help,
          commonLabels,
          labelNames,
          buckets
        )(f)
      ).map(_.mapK(fk))
    }
}