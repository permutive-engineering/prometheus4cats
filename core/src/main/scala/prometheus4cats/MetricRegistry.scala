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
import cats.{Applicative, ~>}
import prometheus4cats.Counter.Labelled
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
  ): Resource[F, Counter[F, Double]]

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
  ): Resource[F, Counter[F, Long]]

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
    *   a [[Counter.Labelled]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledDoubleCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter.Labelled[F, Double, A]]

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
    *   a [[Counter.Labelled]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter.Labelled[F, Long, A]]

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
  ): Resource[F, Gauge[F, Double]]

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
  ): Resource[F, Gauge[F, Long]]

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
    *   a [[Gauge.Labelled]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledDoubleGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge.Labelled[F, Double, A]]

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
    *   a [[Gauge.Labelled]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge.Labelled[F, Long, A]]

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
  ): Resource[F, Histogram[F, Double]]

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
  ): Resource[F, Histogram[F, Long]]

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
    *   a [[Histogram.Labelled]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram.Labelled[F, Double, A]]

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
    *   a [[Histogram.Labelled]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterLabelledLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram.Labelled[F, Long, A]]

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
  ): Resource[F, Summary[F, Double]]

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
  ): Resource[F, Summary[F, Long]]

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
    *   a [[Summary.Labelled]] wrapped in whatever side effect that was performed in registering it
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
  )(f: A => IndexedSeq[String]): Resource[F, Summary.Labelled[F, Double, A]]

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
    *   a [[Summary.Labelled]] wrapped in whatever side effect that was performed in registering it
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
  )(f: A => IndexedSeq[String]): Resource[F, Summary.Labelled[F, Long, A]]

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

  trait WithExemplars[F[_]] extends MetricRegistry[F] {

    /** Create and register an exemplar counter that records [[scala.Double]] values against a metrics registry
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
      *   a [[Counter.Exemplar]] wrapped in whatever side effect that was performed in registering it
      */
    def createAndRegisterLongExemplarCounter(
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels
    ): Resource[F, Counter.Exemplar[F, Long]]

    /** Create and register an exemplar counter that records [[scala.Long]] values against a metrics registry
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
      *   a [[Counter.Exemplar]] wrapped in whatever side effect that was performed in registering it
      */
    def createAndRegisterDoubleExemplarCounter(
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels
    ): Resource[F, Counter.Exemplar[F, Double]]

    /** Create and register a labelled exemplar counter that records [[scala.Double]] values against a metrics registry
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
      *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must
      *   be paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
      * @return
      *   a [[Counter.Labelled.Exemplar]] wrapped in whatever side effect that was performed in registering it
      */
    def createAndRegisterLabelledDoubleExemplarCounter[A](
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name]
    )(f: A => IndexedSeq[String]): Resource[F, Counter.Labelled.Exemplar[F, Double, A]]

    /** Create and register a labelled exemplar counter that records [[scala.Long]] values against a metrics registry
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
      *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must
      *   be paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
      * @return
      *   a [[Counter.Labelled.Exemplar]] wrapped in whatever side effect that was performed in registering it
      */
    def createAndRegisterLabelledLongExemplarCounter[A](
        prefix: Option[Metric.Prefix],
        name: Counter.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name]
    )(f: A => IndexedSeq[String]): Resource[F, Counter.Labelled.Exemplar[F, Long, A]]

    /** Create and register an exemplar histogram that records [[scala.Double]] values against a metrics registry
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
      *   a [[Histogram.Exemplar]] wrapped in whatever side effect that was performed in registering it
      */
    def createAndRegisterDoubleExemplarHistogram(
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        buckets: NonEmptySeq[Double]
    ): Resource[F, Histogram.Exemplar[F, Double]]

    /** Create and register an exemplar histogram that records [[scala.Long]] values against a metrics registry
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
      *   a [[Histogram.Labelled]] wrapped in whatever side effect that was performed in registering it
      */
    def createAndRegisterLongExemplarHistogram(
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        buckets: NonEmptySeq[Long]
    ): Resource[F, Histogram.Exemplar[F, Long]]

    /** Create and register a labelled exemplar histogram against a metrics registry
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
      *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must
      *   be paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
      * @return
      *   a [[Histogram.Labelled.Exemplar]] wrapped in whatever side effect that was performed in registering it
      */
    def createAndRegisterLabelledDoubleExemplarHistogram[A](
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        buckets: NonEmptySeq[Double]
    )(f: A => IndexedSeq[String]): Resource[F, Histogram.Labelled.Exemplar[F, Double, A]]

    /** Create and register a labelled exemplar histogram against a metrics registry
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
      *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must
      *   be paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
      * @return
      *   a [[Histogram.Labelled.Exemplar]] wrapped in whatever side effect that was performed in registering it
      */
    def createAndRegisterLabelledLongExemplarHistogram[A](
        prefix: Option[Metric.Prefix],
        name: Histogram.Name,
        help: Metric.Help,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        buckets: NonEmptySeq[Long]
    )(f: A => IndexedSeq[String]): Resource[F, Histogram.Labelled.Exemplar[F, Long, A]]

    override def mapK[G[_]](
        fk: F ~> G
    )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): MetricRegistry.WithExemplars[G] =
      WithExemplars.mapK(this, fk)
  }

  object WithExemplars {
    def noop[F[_]](implicit F: Applicative[F]): MetricRegistry.WithExemplars[F] =
      new DoubleMetricRegistry.WithExemplars[F] {

        override def createAndRegisterDoubleExemplarCounter(
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: Metric.CommonLabels
        ): Resource[F, Counter.Exemplar[F, Double]] = Resource.pure(Counter.Exemplar.noop)

        override def createAndRegisterDoubleCounter(
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels
        ): Resource[F, Counter[F, Double]] = Resource.pure(Counter.noop)

        override def createAndRegisterLabelledDoubleCounter[A](
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name]
        )(f: A => IndexedSeq[String]): Resource[F, Counter.Labelled[F, Double, A]] =
          Resource.pure(Counter.Labelled.noop)

        override def createAndRegisterLabelledDoubleExemplarCounter[A](
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name]
        )(f: A => IndexedSeq[String]): Resource[F, Labelled.Exemplar[F, Double, A]] =
          Resource.pure(Counter.Labelled.Exemplar.noop)

        override def createAndRegisterDoubleGauge(
            prefix: Option[Metric.Prefix],
            name: Gauge.Name,
            help: Metric.Help,
            commonLabels: CommonLabels
        ): Resource[F, Gauge[F, Double]] =
          Resource.pure(Gauge.noop)

        override def createAndRegisterLabelledDoubleGauge[A](
            prefix: Option[Metric.Prefix],
            name: Gauge.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name]
        )(f: A => IndexedSeq[String]): Resource[F, Gauge.Labelled[F, Double, A]] =
          Resource.pure(Gauge.Labelled.noop)

        override def createAndRegisterDoubleHistogram(
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            buckets: NonEmptySeq[Double]
        ): Resource[F, Histogram[F, Double]] = Resource.pure(Histogram.noop)

        override def createAndRegisterDoubleExemplarHistogram(
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            buckets: NonEmptySeq[Double]
        ): Resource[F, Histogram.Exemplar[F, Double]] = Resource.pure(Histogram.Exemplar.noop)

        override def createAndRegisterLabelledDoubleHistogram[A](
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name],
            buckets: NonEmptySeq[Double]
        )(f: A => IndexedSeq[String]): Resource[F, Histogram.Labelled[F, Double, A]] =
          Resource.pure(Histogram.Labelled.noop)

        override def createAndRegisterLabelledDoubleExemplarHistogram[A](
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name],
            buckets: NonEmptySeq[Double]
        )(f: A => IndexedSeq[String]): Resource[F, Histogram.Labelled.Exemplar[F, Double, A]] =
          Resource.pure(Histogram.Labelled.Exemplar.noop)

        override def createAndRegisterDoubleSummary(
            prefix: Option[Metric.Prefix],
            name: Summary.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            quantiles: Seq[QuantileDefinition],
            maxAge: FiniteDuration,
            ageBuckets: Summary.AgeBuckets
        ): Resource[F, Summary[F, Double]] = Resource.pure(Summary.noop)

        override def createAndRegisterLabelledDoubleSummary[A](
            prefix: Option[Metric.Prefix],
            name: Summary.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name],
            quantiles: Seq[QuantileDefinition],
            maxAge: FiniteDuration,
            ageBuckets: Summary.AgeBuckets
        )(f: A => IndexedSeq[String]): Resource[F, Summary.Labelled[F, Double, A]] =
          Resource.pure(Summary.Labelled.noop)

        override def createAndRegisterInfo(
            prefix: Option[Metric.Prefix],
            name: Info.Name,
            help: Metric.Help
        ): Resource[F, Info[F, Map[Label.Name, String]]] = Resource.pure(Info.noop)
      }

    private[prometheus4cats] def mapK[F[_], G[_]](
        self: MetricRegistry.WithExemplars[F],
        fk: F ~> G
    )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): MetricRegistry.WithExemplars[G] =
      new MetricRegistry.WithExemplars[G] {
        override def createAndRegisterDoubleCounter(
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels
        ): Resource[G, Counter[G, Double]] =
          self.createAndRegisterDoubleCounter(prefix, name, help, commonLabels).mapK(fk).map(_.mapK(fk))

        override def createAndRegisterLabelledDoubleCounter[A](
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name]
        )(f: A => IndexedSeq[String]): Resource[G, Counter.Labelled[G, Double, A]] =
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

        override def createAndRegisterDoubleExemplarCounter(
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels
        ): Resource[G, Counter.Exemplar[G, Double]] =
          self.createAndRegisterDoubleExemplarCounter(prefix, name, help, commonLabels).mapK(fk).map(_.mapK(fk))

        override def createAndRegisterLabelledDoubleExemplarCounter[A](
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name]
        )(f: A => IndexedSeq[String]): Resource[G, Counter.Labelled.Exemplar[G, Double, A]] =
          self
            .createAndRegisterLabelledDoubleExemplarCounter(
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
        ): Resource[G, Gauge[G, Double]] =
          self
            .createAndRegisterDoubleGauge(prefix, name, help, commonLabels)
            .mapK(fk)
            .map(_.mapK(fk))

        override def createAndRegisterLongGauge(
            prefix: Option[Metric.Prefix],
            name: Gauge.Name,
            help: Metric.Help,
            commonLabels: CommonLabels
        ): Resource[G, Gauge[G, Long]] =
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
        )(f: A => IndexedSeq[String]): Resource[G, Gauge.Labelled[G, Double, A]] =
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
        ): Resource[G, Histogram[G, Double]] =
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
        )(f: A => IndexedSeq[String]): Resource[G, Histogram.Labelled[G, Double, A]] =
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

        override def createAndRegisterDoubleExemplarHistogram(
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            buckets: NonEmptySeq[Double]
        ): Resource[G, Histogram.Exemplar[G, Double]] =
          self
            .createAndRegisterDoubleExemplarHistogram(
              prefix,
              name,
              help,
              commonLabels,
              buckets
            )
            .mapK(fk)
            .map(_.mapK(fk))

        override def createAndRegisterLabelledDoubleExemplarHistogram[A](
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name],
            buckets: NonEmptySeq[Double]
        )(f: A => IndexedSeq[String]): Resource[G, Histogram.Labelled.Exemplar[G, Double, A]] =
          self
            .createAndRegisterLabelledDoubleExemplarHistogram(
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
        ): Resource[G, Counter[G, Long]] =
          self.createAndRegisterLongCounter(prefix, name, help, commonLabels).mapK(fk).map(_.mapK(fk))

        override def createAndRegisterLabelledLongCounter[A](
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name]
        )(f: A => IndexedSeq[String]): Resource[G, Counter.Labelled[G, Long, A]] =
          self
            .createAndRegisterLabelledLongCounter(prefix, name, help, commonLabels, labelNames)(f)
            .mapK(fk)
            .map(_.mapK(fk))

        override def createAndRegisterLongExemplarCounter(
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels
        ): Resource[G, Counter.Exemplar[G, Long]] =
          self.createAndRegisterLongExemplarCounter(prefix, name, help, commonLabels).mapK(fk).map(_.mapK(fk))

        override def createAndRegisterLabelledLongExemplarCounter[A](
            prefix: Option[Metric.Prefix],
            name: Counter.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name]
        )(f: A => IndexedSeq[String]): Resource[G, Counter.Labelled.Exemplar[G, Long, A]] =
          self
            .createAndRegisterLabelledLongExemplarCounter(prefix, name, help, commonLabels, labelNames)(f)
            .mapK(fk)
            .map(_.mapK(fk))

        override def createAndRegisterLabelledLongGauge[A](
            prefix: Option[Metric.Prefix],
            name: Gauge.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name]
        )(f: A => IndexedSeq[String]): Resource[G, Gauge.Labelled[G, Long, A]] =
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
        ): Resource[G, Histogram[G, Long]] =
          self.createAndRegisterLongHistogram(prefix, name, help, commonLabels, buckets).mapK(fk).map(_.mapK(fk))

        override def createAndRegisterLabelledLongHistogram[A](
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name],
            buckets: NonEmptySeq[Long]
        )(f: A => IndexedSeq[String]): Resource[G, Histogram.Labelled[G, Long, A]] =
          self
            .createAndRegisterLabelledLongHistogram(prefix, name, help, commonLabels, labelNames, buckets)(f)
            .mapK(fk)
            .map(_.mapK(fk))

        override def createAndRegisterLongExemplarHistogram(
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            buckets: NonEmptySeq[Long]
        ): Resource[G, Histogram.Exemplar[G, Long]] =
          self
            .createAndRegisterLongExemplarHistogram(prefix, name, help, commonLabels, buckets)
            .mapK(fk)
            .map(_.mapK(fk))

        override def createAndRegisterLabelledLongExemplarHistogram[A](
            prefix: Option[Metric.Prefix],
            name: Histogram.Name,
            help: Metric.Help,
            commonLabels: CommonLabels,
            labelNames: IndexedSeq[Label.Name],
            buckets: NonEmptySeq[Long]
        )(f: A => IndexedSeq[String]): Resource[G, Histogram.Labelled.Exemplar[G, Long, A]] =
          self
            .createAndRegisterLabelledLongExemplarHistogram(prefix, name, help, commonLabels, labelNames, buckets)(f)
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
        ): Resource[G, Summary[G, Double]] =
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
        ): Resource[G, Summary[G, Long]] =
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
        )(f: A => IndexedSeq[String]): Resource[G, Summary.Labelled[G, Double, A]] =
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
        )(f: A => IndexedSeq[String]): Resource[G, Summary.Labelled[G, Long, A]] =
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

  def noop[F[_]: Applicative]: MetricRegistry[F] = WithExemplars.noop[F]

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
      ): Resource[G, Counter[G, Double]] =
        self.createAndRegisterDoubleCounter(prefix, name, help, commonLabels).mapK(fk).map(_.mapK(fk))

      override def createAndRegisterLabelledDoubleCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Counter.Labelled[G, Double, A]] =
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
      ): Resource[G, Gauge[G, Double]] =
        self
          .createAndRegisterDoubleGauge(prefix, name, help, commonLabels)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): Resource[G, Gauge[G, Long]] =
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
      )(f: A => IndexedSeq[String]): Resource[G, Gauge.Labelled[G, Double, A]] =
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
      ): Resource[G, Histogram[G, Double]] =
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
      )(f: A => IndexedSeq[String]): Resource[G, Histogram.Labelled[G, Double, A]] =
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
      ): Resource[G, Counter[G, Long]] =
        self.createAndRegisterLongCounter(prefix, name, help, commonLabels).mapK(fk).map(_.mapK(fk))

      override def createAndRegisterLabelledLongCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Counter.Labelled[G, Long, A]] =
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
      )(f: A => IndexedSeq[String]): Resource[G, Gauge.Labelled[G, Long, A]] =
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
      ): Resource[G, Histogram[G, Long]] =
        self.createAndRegisterLongHistogram(prefix, name, help, commonLabels, buckets).mapK(fk).map(_.mapK(fk))

      override def createAndRegisterLabelledLongHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Long]
      )(f: A => IndexedSeq[String]): Resource[G, Histogram.Labelled[G, Long, A]] =
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
      ): Resource[G, Summary[G, Double]] =
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
      ): Resource[G, Summary[G, Long]] =
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
      )(f: A => IndexedSeq[String]): Resource[G, Summary.Labelled[G, Double, A]] =
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
      )(f: A => IndexedSeq[String]): Resource[G, Summary.Labelled[G, Long, A]] =
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
