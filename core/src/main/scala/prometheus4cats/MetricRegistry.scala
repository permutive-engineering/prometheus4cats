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

import cats.data.NonEmptySeq
import cats.effect.kernel.{MonadCancel, Resource}
import cats.{Monad, ~>}
import prometheus4cats.Metric.CommonLabels
import prometheus4cats.Summary.QuantileDefinition
import prometheus4cats.util.DoubleMetricRegistry

import scala.concurrent.duration.FiniteDuration

/** Trait for registering metrics against different backends. May be implemented by anyone for use with
  * [[MetricFactory]]
  */
trait MetricRegistry[F[_]] {

  /** Create and register a counter that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Counter.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @return
    *   a [[Counter]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterDoubleCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Counter[F, Double, Unit]]

  /** Create and register a counter that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Counter.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @return
    *   a [[Counter]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLongCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Counter[F, Long, Unit]]

  /** Create and register a labelled counter that records [[scala.Double]] values against a metrics registry
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
    * @return
    *   a [[Counter]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledDoubleCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter[F, Double, A]]

  /** Create and register a labelled counter that records [[scala.Long]] values against a metrics registry
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
    * @return
    *   a [[Counter]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter[F, Long, A]]

  /** Create and register a gauge that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Gauge.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @return
    *   a [[Gauge]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterDoubleGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Gauge[F, Double, Unit]]

  /** Create and register a gauge that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Gauge.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @return
    *   a [[Gauge]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLongGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Gauge[F, Long, Unit]]

  /** Create and register a labelled gauge that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Gauge.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Gauge]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledDoubleGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge[F, Double, A]]

  /** Create and register a labelled gauge that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Gauge.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Gauge]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge[F, Long, A]]

  /** Create and register a histogram that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Histogram.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param buckets
    *   a [[cats.data.NonEmptySeq]] of [[scala.Double]]s representing bucket values for the histogram
    * @return
    *   a [[Histogram]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterDoubleHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double]
  ): Resource[F, Histogram[F, Double, Unit]]

  /** Create and register a histogram that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Histogram.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param buckets
    *   a [[cats.data.NonEmptySeq]] of [[scala.Double]]s representing bucket values for the histogram
    * @return
    *   a [[Histogram]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLongHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Long]
  ): Resource[F, Histogram[F, Long, Unit]]

  /** Create and register a labelled histogram against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Histogram.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param buckets
    *   a [[cats.data.NonEmptySeq]] of [[scala.Double]]s representing bucket values for the histogram
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Histogram]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]]

  /** Create and register a labelled histogram against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Histogram.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param buckets
    *   a [[cats.data.NonEmptySeq]] of [[scala.Double]]s representing bucket values for the histogram
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Histogram]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Long, A]]

  /** Create and register a summary that records [[scala.Double]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Summary.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param quantiles
    *   a [[scala.Seq]] of [[Summary.QuantileDefinition]]s representing bucket values for the summary. Quantiles are
    *   expensive to calculate, so this may be empty.
    * @param maxAge
    *   a [[scala.concurrent.duration.FiniteDuration]] indicating a window over which the summary should be calculate.
    *   Typically, you don't want to have a [[Summary]] representing the entire runtime of the application, but you want
    *   to look at a reasonable time interval. [[Summary]] metrics should implement a configurable sliding time window.
    * @param ageBuckets
    *   how many intervals there should be in a given time window defined by `maxAge`. For example, if a time window of
    *   10 minutes and 5 age buckets, i.e. the time window is 10 minutes wide, and we slide it forward every 2 minutes.
    * @return
    *   a [[Summary]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterDoubleSummary(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      quantiles: Seq[QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  ): Resource[F, Summary[F, Double, Unit]]

  /** Create and register a summary that records [[scala.Long]] values against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Summary.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param commonLabels
    *   [[Metric.CommonLabels]] map of common labels to be added to the metric
    * @param quantiles
    *   a [[scala.Seq]] of [[Summary.QuantileDefinition]]s representing bucket values for the summary. Quantiles are
    *   expensive to calculate, so this may be empty.
    * @param maxAge
    *   a [[scala.concurrent.duration.FiniteDuration]] indicating a window over which the summary should be calculate.
    *   Typically, you don't want to have a [[Summary]] representing the entire runtime of the application, but you want
    *   to look at a reasonable time interval. [[Summary]] metrics should implement a configurable sliding time window.
    * @param ageBuckets
    *   how many intervals there should be in a given time window defined by `maxAge`. For example, if a time window of
    *   10 minutes and 5 age buckets, i.e. the time window is 10 minutes wide, and we slide it forward every 2 minutes.
    * @return
    *   a [[Summary]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLongSummary(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      quantiles: Seq[QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  ): Resource[F, Summary[F, Long, Unit]]

  /** Create and register a summary that records [[scala.Double]] values against a metrics registry
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
    * @param quantiles
    *   a [[scala.Seq]] of [[Summary.QuantileDefinition]]s representing bucket values for the summary. Quantiles are
    *   expensive to calculate, so this may be empty.
    * @param maxAge
    *   a [[scala.concurrent.duration.FiniteDuration]] indicating a window over which the summary should be calculate.
    *   Typically, you don't want to have a [[Summary]] representing the entire runtime of the application, but you want
    *   to look at a reasonable time interval. [[Summary]] metrics should implement a configurable sliding time window.
    * @param ageBuckets
    *   how many intervals there should be in a given time window defined by `maxAge`. For example, if a time window of
    *   10 minutes and 5 age buckets, i.e. the time window is 10 minutes wide, and we slide it forward every 2 minutes.
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Summary]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledDoubleSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[F, Summary[F, Double, A]]

  /** Create and register a summary that records [[scala.Long]] values against a metrics registry
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
    * @param quantiles
    *   a [[scala.Seq]] of [[Summary.QuantileDefinition]]s representing bucket values for the summary. Quantiles are
    *   expensive to calculate, so this may be empty.
    * @param maxAge
    *   a [[scala.concurrent.duration.FiniteDuration]] indicating a window over which the summary should be calculate.
    *   Typically, you don't want to have a [[Summary]] representing the entire runtime of the application, but you want
    *   to look at a reasonable time interval. [[Summary]] metrics should implement a configurable sliding time window.
    * @param ageBuckets
    *   how many intervals there should be in a given time window defined by `maxAge`. For example, if a time window of
    *   10 minutes and 5 age buckets, i.e. the time window is 10 minutes wide, and we slide it forward every 2 minutes.
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Summary]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledLongSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[F, Summary[F, Long, A]]

  /** Create and register an info metric against a metrics registry
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Histogram.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @return
    *   a [[Info]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterInfo(
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help
  ): Resource[F, Info[F, Map[Label.Name, String]]]

  def mapK[G[_]](fk: F ~> G)(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): MetricRegistry[G] =
    MetricRegistry.mapK(this, fk)
}

object MetricRegistry {

  def noop[F[_]](implicit F: Monad[F]): MetricRegistry[F] =
    new DoubleMetricRegistry[F] {

      override def createAndRegisterDoubleCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): Resource[F, Counter[F, Double, Unit]] = Resource.pure(Counter.noop)

      override def createAndRegisterLabelledDoubleCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[F, Counter[F, Double, A]] =
        Resource.pure(Counter.noop)

      override def createAndRegisterDoubleGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): Resource[F, Gauge[F, Double, Unit]] =
        Resource.pure(Gauge.noop)

      override def createAndRegisterLabelledDoubleGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[F, Gauge[F, Double, A]] =
        Resource.pure(Gauge.noop)

      override def createAndRegisterDoubleHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Double]
      ): Resource[F, Histogram[F, Double, Unit]] = Resource.pure(Histogram.noop)

      override def createAndRegisterLabelledDoubleHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] =
        Resource.pure(Histogram.noop)

      override def createAndRegisterDoubleSummary(
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          quantiles: Seq[QuantileDefinition],
          maxAge: FiniteDuration,
          ageBuckets: Summary.AgeBuckets
      ): Resource[F, Summary[F, Double, Unit]] = Resource.pure(Summary.noop)

      override def createAndRegisterLabelledDoubleSummary[A](
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          quantiles: Seq[QuantileDefinition],
          maxAge: FiniteDuration,
          ageBuckets: Summary.AgeBuckets
      )(f: A => IndexedSeq[String]): Resource[F, Summary[F, Double, A]] =
        Resource.pure(Summary.noop)

      override def createAndRegisterInfo(
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help
      ): Resource[F, Info[F, Map[Label.Name, String]]] = Resource.pure(Info.noop)
    }

  private[prometheus4cats] def mapK[F[_], G[_]](
      self: MetricRegistry[F],
      fk: F ~> G
  )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): MetricRegistry[G] =
    new MetricRegistry[G] {
      override def createAndRegisterDoubleCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): Resource[G, Counter[G, Double, Unit]] =
        self.createAndRegisterDoubleCounter(prefix, name, help, commonLabels).mapK(fk).map(_.mapK(fk))

      override def createAndRegisterLabelledDoubleCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Counter[G, Double, A]] =
        self
          .createAndRegisterLabelledDoubleCounter(
            prefix,
            name,
            help,
            commonLabels,
            labelNames
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterDoubleGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): Resource[G, Gauge[G, Double, Unit]] =
        self
          .createAndRegisterDoubleGauge(prefix, name, help, commonLabels)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): Resource[G, Gauge[G, Long, Unit]] =
        self
          .createAndRegisterLongGauge(prefix, name, help, commonLabels)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLabelledDoubleGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Gauge[G, Double, A]] =
        self
          .createAndRegisterLabelledDoubleGauge(
            prefix,
            name,
            help,
            commonLabels,
            labelNames
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterDoubleHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Double]
      ): Resource[G, Histogram[G, Double, Unit]] =
        self
          .createAndRegisterDoubleHistogram(
            prefix,
            name,
            help,
            commonLabels,
            buckets
          )
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLabelledDoubleHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): Resource[G, Histogram[G, Double, A]] =
        self
          .createAndRegisterLabelledDoubleHistogram(
            prefix,
            name,
            help,
            commonLabels,
            labelNames,
            buckets
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): Resource[G, Counter[G, Long, Unit]] =
        self.createAndRegisterLongCounter(prefix, name, help, commonLabels).mapK(fk).map(_.mapK(fk))

      override def createAndRegisterLabelledLongCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Counter[G, Long, A]] =
        self
          .createAndRegisterLabelledLongCounter(prefix, name, help, commonLabels, labelNames)(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLabelledLongGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Gauge[G, Long, A]] =
        self
          .createAndRegisterLabelledLongGauge(prefix, name, help, commonLabels, labelNames)(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Long]
      ): Resource[G, Histogram[G, Long, Unit]] =
        self.createAndRegisterLongHistogram(prefix, name, help, commonLabels, buckets).mapK(fk).map(_.mapK(fk))

      override def createAndRegisterLabelledLongHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Long]
      )(f: A => IndexedSeq[String]): Resource[G, Histogram[G, Long, A]] =
        self
          .createAndRegisterLabelledLongHistogram(prefix, name, help, commonLabels, labelNames, buckets)(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterDoubleSummary(
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          quantiles: Seq[QuantileDefinition],
          maxAge: FiniteDuration,
          ageBuckets: Summary.AgeBuckets
      ): Resource[G, Summary[G, Double, Unit]] =
        self
          .createAndRegisterDoubleSummary(prefix, name, help, commonLabels, quantiles, maxAge, ageBuckets)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongSummary(
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          quantiles: Seq[QuantileDefinition],
          maxAge: FiniteDuration,
          ageBuckets: Summary.AgeBuckets
      ): Resource[G, Summary[G, Long, Unit]] =
        self
          .createAndRegisterLongSummary(prefix, name, help, commonLabels, quantiles, maxAge, ageBuckets)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLabelledDoubleSummary[A](
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          quantiles: Seq[QuantileDefinition],
          maxAge: FiniteDuration,
          ageBuckets: Summary.AgeBuckets
      )(f: A => IndexedSeq[String]): Resource[G, Summary[G, Double, A]] =
        self
          .createAndRegisterLabelledDoubleSummary(
            prefix,
            name,
            help,
            commonLabels,
            labelNames,
            quantiles,
            maxAge,
            ageBuckets
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLabelledLongSummary[A](
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          quantiles: Seq[QuantileDefinition],
          maxAge: FiniteDuration,
          ageBuckets: Summary.AgeBuckets
      )(f: A => IndexedSeq[String]): Resource[G, Summary[G, Long, A]] =
        self
          .createAndRegisterLabelledLongSummary(
            prefix,
            name,
            help,
            commonLabels,
            labelNames,
            quantiles,
            maxAge,
            ageBuckets
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterInfo(
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help
      ): Resource[G, Info[G, Map[Label.Name, String]]] =
        self.createAndRegisterInfo(prefix, name, help).mapK(fk).map(_.mapK(fk))

    }
}
