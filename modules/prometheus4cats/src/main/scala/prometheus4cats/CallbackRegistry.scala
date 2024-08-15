/*
 * Copyright 2022 Permutive
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package prometheus4cats

import cats.data.NonEmptyList
import cats.data.NonEmptySeq
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource
import cats.~>

/** Trait for registering callbacks against different backends. May be implemented by anyone for use with
  * [[MetricFactory.WithCallbacks]]
  */
trait CallbackRegistry[F[_]] {

  /** Register a labelled counter value that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Counter.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @param callback
    *   Some effectful operation that returns a [[cats.data.NonEmptyList]] of [[scala.Double]] and label value tuples
    * @return
    *   An empty side effect to indicate that the callback has been registered
    */
  def registerDoubleCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit]

  /** Register a labelled counter value that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Counter.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @param callback
    *   Some effectful operation that returns a [[cats.data.NonEmptyList]] of [[scala.Long]] and label value tuples
    * @return
    *   An empty side effect to indicate that the callback has been registered
    */
  def registerLongCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Long, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit]

  /** Register a labelled gauge value that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Counter.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @param callback
    *   Some effectful operation that returns a [[cats.data.NonEmptyList]] of [[scala.Double]] and label value tuples
    * @return
    *   An empty side effect to indicate that the callback has been registered
    */
  def registerDoubleGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit]

  /** Register a labelled gauge value that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Counter.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @param callback
    *   Some effectful operation that returns a [[cats.data.NonEmptyList]] of [[scala.Long]] and label value tuples
    * @return
    *   An empty side effect to indicate that the callback has been registered
    */
  def registerLongGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Long, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit]

  /** Register a labelled histogram value that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Counter.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @param callback
    *   Some effectful operation that returns a [[cats.data.NonEmptyList]] of [[Histogram.Value]] parameterised with
    *   [[scala.Double]] and label value tuples
    * @return
    *   An empty side effect to indicate that the callback has been registered
    */
  def registerDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      callback: F[NonEmptyList[(Histogram.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit]

  /** Register a labelled histogram value that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Counter.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @param callback
    *   Some effectful operation that returns a [[cats.data.NonEmptyList]] of [[Histogram.Value]] parameterised with
    *   [[scala.Long]] and label value tuples
    * @return
    *   An empty side effect to indicate that the callback has been registered
    */
  def registerLongHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long],
      callback: F[NonEmptyList[(Histogram.Value[Long], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit]

  /** Register a labelled summary value that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Summary.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param callback
    *   Some effectful operation that returns a [[cats.data.NonEmptyList]] of [[Summary.Value]] parameterised with
    *   [[scala.Double]] and label value tuples
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Summary]] wrapped in whatever side effect that was performed in registering it
    */
  def registerDoubleSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Summary.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit]

  /** Register a labelled summary value that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Summary.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param callback
    *   Some effectful operation that returns a [[cats.data.NonEmptyList]] of [[Summary.Value]] parameterised with
    *   [[scala.Long]] and label value tuples
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Summary]] wrapped in whatever side effect that was performed in registering it
    */
  def registerLongSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Summary.Value[Long], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit]

  def registerMetricCollectionCallback(
      prefix: Option[Metric.Prefix],
      commonLabels: Metric.CommonLabels,
      callback: F[MetricCollection]
  ): Resource[F, Unit]

  /** Given a natural transformation from `F` to `G` and from `G` to `F`, transforms this [[CallbackRegistry]] from
    * effect `F` to effect `G`
    */
  final def imapK[G[_]](fk: F ~> G, gk: G ~> F)(implicit
      F: MonadCancel[F, _],
      G: MonadCancel[G, _]
  ): CallbackRegistry[G] = CallbackRegistry.imapK(this, fk, gk)

}

object CallbackRegistry {

  def noop[F[_]]: CallbackRegistry[F] = new CallbackRegistry[F] {

    override def registerDoubleCounterCallback[A](
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[NonEmptyList[(Double, A)]]
    )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

    override def registerLongCounterCallback[A](
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[NonEmptyList[(Long, A)]]
    )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

    override def registerDoubleGaugeCallback[A](
        prefix: Option[Metric.Prefix],
        name: Gauge.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[NonEmptyList[(Double, A)]]
    )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

    override def registerLongGaugeCallback[A](
        prefix: Option[Metric.Prefix],
        name: Gauge.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[NonEmptyList[(Long, A)]]
    )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

    override def registerDoubleHistogramCallback[A](
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        buckets: NonEmptySeq[Double],
        callback: F[NonEmptyList[(Histogram.Value[Double], A)]]
    )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

    override def registerLongHistogramCallback[A](
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        buckets: NonEmptySeq[Long],
        callback: F[NonEmptyList[(Histogram.Value[Long], A)]]
    )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

    override def registerDoubleSummaryCallback[A](
        prefix: Option[Metric.Prefix],
        name: Summary.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[NonEmptyList[(Summary.Value[Double], A)]]
    )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

    override def registerLongSummaryCallback[A](
        prefix: Option[Metric.Prefix],
        name: Summary.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        callback: F[NonEmptyList[(Summary.Value[Long], A)]]
    )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

    override def registerMetricCollectionCallback(
        prefix: Option[Metric.Prefix],
        commonLabels: Metric.CommonLabels,
        callback: F[MetricCollection]
    ): Resource[F, Unit] = Resource.unit

  }

  def imapK[F[_], G[_]](
      self: CallbackRegistry[F],
      fk: F ~> G,
      gk: G ~> F
  )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): CallbackRegistry[G] =
    new CallbackRegistry[G] {

      override def registerDoubleCounterCallback[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[NonEmptyList[(Double, A)]]
      )(f: A => IndexedSeq[String]): Resource[G, Unit] = self
        .registerDoubleCounterCallback(prefix, name, help, commonLabels, labelNames, gk(callback))(f)
        .mapK(fk)

      override def registerLongCounterCallback[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[NonEmptyList[(Long, A)]]
      )(f: A => IndexedSeq[String]): Resource[G, Unit] =
        self.registerLongCounterCallback(prefix, name, help, commonLabels, labelNames, gk(callback))(f).mapK(fk)

      override def registerDoubleGaugeCallback[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[NonEmptyList[(Double, A)]]
      )(f: A => IndexedSeq[String]): Resource[G, Unit] =
        self.registerDoubleGaugeCallback(prefix, name, help, commonLabels, labelNames, gk(callback))(f).mapK(fk)

      override def registerLongGaugeCallback[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[NonEmptyList[(Long, A)]]
      )(f: A => IndexedSeq[String]): Resource[G, Unit] =
        self.registerLongGaugeCallback(prefix, name, help, commonLabels, labelNames, gk(callback))(f).mapK(fk)

      override def registerDoubleHistogramCallback[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double],
          callback: G[NonEmptyList[(Histogram.Value[Double], A)]]
      )(f: A => IndexedSeq[String]): Resource[G, Unit] =
        self
          .registerDoubleHistogramCallback(
            prefix, name, help, commonLabels, labelNames, buckets, gk(callback)
          )(f)
          .mapK(fk)

      override def registerLongHistogramCallback[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Long],
          callback: G[NonEmptyList[(Histogram.Value[Long], A)]]
      )(f: A => IndexedSeq[String]): Resource[G, Unit] =
        self
          .registerLongHistogramCallback(
            prefix, name, help, commonLabels, labelNames, buckets, gk(callback)
          )(f)
          .mapK(fk)

      override def registerDoubleSummaryCallback[A](
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[NonEmptyList[(Summary.Value[Double], A)]]
      )(f: A => IndexedSeq[String]): Resource[G, Unit] =
        self
          .registerDoubleSummaryCallback(
            prefix, name, help, commonLabels, labelNames, gk(callback)
          )(f)
          .mapK(fk)

      override def registerLongSummaryCallback[A](
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          callback: G[NonEmptyList[(Summary.Value[Long], A)]]
      )(f: A => IndexedSeq[String]): Resource[G, Unit] =
        self
          .registerLongSummaryCallback(
            prefix, name, help, commonLabels, labelNames, gk(callback)
          )(f)
          .mapK(fk)

      override def registerMetricCollectionCallback(
          prefix: Option[Metric.Prefix],
          commonLabels: Metric.CommonLabels,
          callback: G[MetricCollection]
      ): Resource[G, Unit] = self.registerMetricCollectionCallback(prefix, commonLabels, gk(callback)).mapK(fk)

    }

}
