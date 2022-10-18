package prometheus4cats

import cats.data.NonEmptySeq
import cats.{Applicative, ~>}

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
      buckets: NonEmptySeq[Double],
      callback: F[Histogram.Value[Double]]
  ): F[Unit]

  protected[prometheus4cats] def registerLongHistogramCallback(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Long],
      callback: F[Histogram.Value[Long]]
  ): F[Unit]

  protected[prometheus4cats] def registerLabelledDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      callback: F[(Histogram.Value[Double], A)]
  )(f: A => IndexedSeq[String]): F[Unit]

  protected[prometheus4cats] def registerLabelledLongHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long],
      callback: F[(Histogram.Value[Long], A)]
  )(f: A => IndexedSeq[String]): F[Unit]

  final def imapK[G[_]](fk: F ~> G, gk: G ~> F): CallbackRegistry[G] = CallbackRegistry.imapK(this, fk, gk)
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
        buckets: NonEmptySeq[Double],
        callback: F[Histogram.Value[Double]]
    ): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLongHistogramCallback(
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        buckets: NonEmptySeq[Long],
        callback: F[Histogram.Value[Long]]
    ): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLabelledDoubleHistogramCallback[A](
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        buckets: NonEmptySeq[Double],
        callback: F[(Histogram.Value[Double], A)]
    )(f: A => IndexedSeq[String]): F[Unit] = F.unit

    override protected[prometheus4cats] def registerLabelledLongHistogramCallback[A](
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        buckets: NonEmptySeq[Long],
        callback: F[(Histogram.Value[Long], A)]
    )(f: A => IndexedSeq[String]): F[Unit] = F.unit
  }

  def imapK[F[_], G[_]](self: CallbackRegistry[F], fk: F ~> G, gk: G ~> F): CallbackRegistry[G] =
    new CallbackRegistry[G] {
      override protected[prometheus4cats] def registerDoubleCounterCallback(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          callback: G[Double]
      ): G[Unit] = fk(self.registerDoubleCounterCallback(prefix, name, help, commonLabels, gk(callback)))

      override protected[prometheus4cats] def registerLongCounterCallback(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          callback: G[Long]
      ): G[Unit] = fk(self.registerLongCounterCallback(prefix, name, help, commonLabels, gk(callback)))

      override protected[prometheus4cats] def registerLabelledDoubleCounterCallback[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[(Double, A)]
      )(f: A => IndexedSeq[String]): G[Unit] = fk(
        self.registerLabelledDoubleCounterCallback(prefix, name, help, commonLabels, labelNames, gk(callback))(f)
      )

      override protected[prometheus4cats] def registerLabelledLongCounterCallback[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[(Long, A)]
      )(f: A => IndexedSeq[String]): G[Unit] = fk(
        self.registerLabelledLongCounterCallback(prefix, name, help, commonLabels, labelNames, gk(callback))(f)
      )

      override protected[prometheus4cats] def registerDoubleGaugeCallback(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          callback: G[Double]
      ): G[Unit] = fk(self.registerDoubleGaugeCallback(prefix, name, help, commonLabels, gk(callback)))

      override protected[prometheus4cats] def registerLongGaugeCallback(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          callback: G[Long]
      ): G[Unit] = fk(self.registerLongGaugeCallback(prefix, name, help, commonLabels, gk(callback)))

      override protected[prometheus4cats] def registerLabelledDoubleGaugeCallback[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[(Double, A)]
      )(f: A => IndexedSeq[String]): G[Unit] = fk(
        self.registerLabelledDoubleGaugeCallback(prefix, name, help, commonLabels, labelNames, gk(callback))(f)
      )

      override protected[prometheus4cats] def registerLabelledLongGaugeCallback[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[(Long, A)]
      )(f: A => IndexedSeq[String]): G[Unit] = fk(
        self.registerLabelledLongGaugeCallback(prefix, name, help, commonLabels, labelNames, gk(callback))(f)
      )

      override protected[prometheus4cats] def registerDoubleHistogramCallback(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          buckets: NonEmptySeq[Double],
          callback: G[Histogram.Value[Double]]
      ): G[Unit] = fk(self.registerDoubleHistogramCallback(prefix, name, help, commonLabels, buckets, gk(callback)))

      override protected[prometheus4cats] def registerLongHistogramCallback(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          buckets: NonEmptySeq[Long],
          callback: G[Histogram.Value[Long]]
      ): G[Unit] = fk(self.registerLongHistogramCallback(prefix, name, help, commonLabels, buckets, gk(callback)))

      override protected[prometheus4cats] def registerLabelledDoubleHistogramCallback[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double],
          callback: G[(Histogram.Value[Double], A)]
      )(f: A => IndexedSeq[String]): G[Unit] = fk(
        self.registerLabelledDoubleHistogramCallback(
          prefix,
          name,
          help,
          commonLabels,
          labelNames,
          buckets,
          gk(callback)
        )(f)
      )

      override protected[prometheus4cats] def registerLabelledLongHistogramCallback[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Long],
          callback: G[(Histogram.Value[Long], A)]
      )(f: A => IndexedSeq[String]): G[Unit] = fk(
        self.registerLabelledLongHistogramCallback(
          prefix,
          name,
          help,
          commonLabels,
          labelNames,
          buckets,
          gk(callback)
        )(f)
      )
    }
}
