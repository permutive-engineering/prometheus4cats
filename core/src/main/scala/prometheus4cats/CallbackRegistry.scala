package prometheus4cats

import cats.Applicative

trait CallbackRegistry[F[_]] {
  protected[prometheus4cats] def registerDoubleCounterCallback(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Double]
  ): F[Unit]

  protected[prometheus4cats] def registerLongCounterCallback(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Long]
  ): F[Unit]

  protected[prometheus4cats] def registerLabelledDoubleCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Double, A)]
  )(f: A => IndexedSeq[String]): F[Unit]

  protected[prometheus4cats] def registerLabelledLongCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Long, A)]
  )(f: A => IndexedSeq[String]): F[Unit]

  protected[prometheus4cats] def registerDoubleGaugeCallback(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Double]
  ): F[Unit]

  protected[prometheus4cats] def registerLongGaugeCallback(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Long]
  ): F[Unit]

  protected[prometheus4cats] def registerLabelledDoubleGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Double, A)]
  )(f: A => IndexedSeq[String]): F[Unit]

  protected[prometheus4cats] def registerLabelledLongGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Long, A)]
  )(f: A => IndexedSeq[String]): F[Unit]

  protected[prometheus4cats] def registerDoubleHistogramCallback(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Histogram.Value[Double]]
  ): F[Unit]

  protected[prometheus4cats] def registerLongHistogramCallback(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Histogram.Value[Long]]
  ): F[Unit]

  protected[prometheus4cats] def registerLabelledDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Histogram.Value[Double], A)]
  )(f: A => IndexedSeq[String]): F[Unit]

  protected[prometheus4cats] def registerLabelledLongHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Histogram.Value[Long], A)]
  )(f: A => IndexedSeq[String]): F[Unit]

}

object CallbackRegistry {
  def noop[F[_]](implicit F: Applicative[F]): CallbackRegistry[F] = new CallbackRegistry[F] {
    override protected[prometheus4cats] def registerDoubleCounterCallback(
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        callback: F[Double]
    ): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLongCounterCallback(
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        callback: F[Long]
    ): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLabelledDoubleCounterCallback[A](
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[(Double, A)]
    )(f: A => IndexedSeq[String]): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLabelledLongCounterCallback[A](
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[(Long, A)]
    )(f: A => IndexedSeq[String]): F[Unit] = F.unit

    override protected[prometheus4cats] def registerDoubleGaugeCallback(
        prefix: Option[Metric.Prefix],
        name: Gauge.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        callback: F[Double]
    ): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLongGaugeCallback(
        prefix: Option[Metric.Prefix],
        name: Gauge.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        callback: F[Long]
    ): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLabelledDoubleGaugeCallback[A](
        prefix: Option[Metric.Prefix],
        name: Gauge.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[(Double, A)]
    )(f: A => IndexedSeq[String]): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLabelledLongGaugeCallback[A](
        prefix: Option[Metric.Prefix],
        name: Gauge.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[(Long, A)]
    )(f: A => IndexedSeq[String]): F[Unit] = F.unit

    override protected[prometheus4cats] def registerDoubleHistogramCallback(
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        callback: F[Histogram.Value[Double]]
    ): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLongHistogramCallback(
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        callback: F[Histogram.Value[Long]]
    ): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLabelledDoubleHistogramCallback[A](
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[(Histogram.Value[Double], A)]
    )(f: A => IndexedSeq[String]): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLabelledLongHistogramCallback[A](
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[(Histogram.Value[Long], A)]
    )(f: A => IndexedSeq[String]): F[Unit] = F.unit
  }
}
