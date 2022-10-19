package prometheus4cats.util

import cats.Functor
import cats.data.NonEmptySeq
import cats.syntax.functor._
import prometheus4cats._

trait DoubleMetricsRegistry[F[_]] extends MetricsRegistry[F] {
  implicit protected val F: Functor[F]

  override protected[prometheus4cats] def createAndRegisterLongCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Counter[F, Long]] = createAndRegisterDoubleCounter(prefix, name, help, commonLabels).map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLabelledLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleCounter(prefix, name, help, commonLabels, labelNames)(f).map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLongGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Gauge[F, Long]] = createAndRegisterDoubleGauge(prefix, name, help, commonLabels).map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLabelledLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleGauge(prefix, name, help, commonLabels, labelNames)(f).map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLongHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Long]
  ): F[Histogram[F, Long]] =
    createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets.map(_.toDouble))
      .map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLabelledLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleHistogram(prefix, name, help, commonLabels, labelNames, buckets.map(_.toDouble))(f)
      .map(
        _.contramap(_.toDouble)
      )
}
