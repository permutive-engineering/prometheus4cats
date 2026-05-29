/*
 * Copyright 2022-2026 Permutive Ltd. <https://permutive.com>
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

import scala.concurrent.duration.FiniteDuration

import cats.Applicative
import cats.data.NonEmptySeq
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource
import cats.~>

import prometheus4cats.Metric.CommonLabels
import prometheus4cats.Summary.QuantileDefinition
import prometheus4cats.util.DoubleMetricRegistry

/** Trait for registering metrics against different backends. May be implemented by anyone for use with
  * [[MetricFactory]]
  */
trait MetricRegistry[F[_]] {

  /** The type of the underlying registry implementation. */
  type Underlying

  /** Returns the underlying registry implementation.
    *
    * This can be used to access implementation-specific functionality, such as the `CollectorRegistry` when using
    * `JavaMetricRegistry`.
    */
  def underlying: Underlying

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
  def createAndRegisterDoubleCounter[A](
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
  def createAndRegisterLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter[F, Long, A]]

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
  def createAndRegisterDoubleGauge[A](
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
  def createAndRegisterLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge[F, Long, A]]

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
  def createAndRegisterDoubleHistogram[A](
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
  def createAndRegisterLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Long, A]]

  /** Create and register a labelled histogram that emits BOTH classic and native histogram representations from a
    * single declaration.
    *
    * This is the NHCB-friendly mode: consumers preserve their curated bucket boundaries via the classic representation,
    * AND the metric also exposes a native exponential histogram. Prometheus 2.49+ can convert the classic form to NHCB
    * at scrape time via `convert_classic_histograms_to_nhcb`, giving consumers true NHCB without sacrificing bucket
    * intent.
    *
    * Backends that don't support native histograms should signal that via an error raised from the returned
    * [[cats.effect.kernel.Resource]] (the simpleclient backend cannot emit native histograms).
    */
  def createAndRegisterDoubleHistogramWithNative[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      nativeHistogram: NativeHistogram
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]]

  /** [[scala.Long]] variant of [[createAndRegisterDoubleHistogramWithNative]]. */
  def createAndRegisterLongHistogramWithNative[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long],
      nativeHistogram: NativeHistogram
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Long, A]]

  /** Create and register a labelled native histogram that records [[scala.Double]] values against a metrics registry.
    *
    * Native histograms (sometimes called sparse or exponential histograms) automatically allocate buckets sized by an
    * exponential schema, so consumers do not pre-declare bucket boundaries. They typically reduce metric cardinality
    * compared to a classic histogram with explicit buckets.
    *
    * Native histograms are emitted only over Prometheus protobuf scrape negotiation, and require a Prometheus server
    * recent enough to ingest them. Backends that do not support native histograms should signal that via an error
    * raised from the returned [[cats.effect.kernel.Resource]].
    *
    * @note
    *   There is no `Long` variant of this method by design. Native histogram buckets are computed exponentially over
    *   the real line, so the underlying representation is always `Double`. Consumers observing integer-typed values
    *   should convert at the call site (e.g. `.contramap(_.toDouble)`).
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
    * @param config
    *   tuning parameters for the native histogram (schema, max bucket count, reset duration, zero-threshold bounds)
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   a [[Histogram]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterDoubleNativeHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      config: NativeHistogram
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]]

  /** [[scala.Long]] variant of [[createAndRegisterDoubleNativeHistogram]]. Backends are expected to honour this by
    * registering a Double-typed native histogram underneath and converting Long observations to Double at the call
    * site; the native histogram's exponential bucket layout doesn't care about the original numeric type.
    */
  def createAndRegisterLongNativeHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      config: NativeHistogram
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
  def createAndRegisterDoubleSummary[A](
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
  def createAndRegisterLongSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[F, Summary[F, Long, A]]

  /** Create and register an info metric against a metrics registry.
    *
    * Info metrics in Prometheus carry stable identity metadata (typically `build_info{version,commit,...} 1`). Label
    * names are declared up front and the runtime values are positional, matching the upstream `prometheus-metrics-core`
    * 1.x API and the way every other metric type in this trait works.
    *
    * The wire format (`name{labels...} 1`) is unchanged from the v5 API; only the Scala-side construction shape
    * differs.
    *
    * @param prefix
    *   optional [[Metric.Prefix]] to be prepended to the metric name
    * @param name
    *   [[Info.Name]] metric name
    * @param help
    *   [[Metric.Help]] string to describe the metric
    * @param labelNames
    *   an [[scala.IndexedSeq]] of [[Label.Name]]s to annotate the metric with
    * @param f
    *   a function from `A` to an [[scala.IndexedSeq]] of [[java.lang.String]] that provides label values, which must be
    *   paired with their corresponding name in the [[scala.IndexedSeq]] of [[Label.Name]]s
    * @return
    *   an [[Info]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterInfo[A](
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Info[F, A]]

  def mapK[G[_]](fk: F ~> G)(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): MetricRegistry[G] =
    MetricRegistry.mapK(this, fk)

}

object MetricRegistry {

  def noop[F[_]](implicit F: Applicative[F]): MetricRegistry[F] =
    new DoubleMetricRegistry[F] {

      type Underlying = Unit

      def underlying: Unit = ()

      def createAndRegisterDoubleCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[F, Counter[F, Double, A]] =
        Resource.pure(Counter.noop)

      override def createAndRegisterDoubleGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[F, Gauge[F, Double, A]] =
        Resource.pure(Gauge.noop)

      override def createAndRegisterDoubleHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] =
        Resource.pure(Histogram.noop)

      override def createAndRegisterDoubleNativeHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          config: NativeHistogram
      )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] =
        Resource.pure(Histogram.noop)

      override def createAndRegisterLongNativeHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          config: NativeHistogram
      )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Long, A]] =
        Resource.pure(Histogram.noop)

      override def createAndRegisterDoubleHistogramWithNative[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double],
          nativeHistogram: NativeHistogram
      )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] =
        Resource.pure(Histogram.noop)

      override def createAndRegisterDoubleSummary[A](
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

      override def createAndRegisterInfo[A](
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[F, Info[F, A]] = Resource.pure(Info.noop)

    }

  private[prometheus4cats] def mapK[F[_], G[_]](
      self: MetricRegistry[F],
      fk: F ~> G
  )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): MetricRegistry[G] =
    new MetricRegistry[G] {

      type Underlying = self.Underlying

      def underlying: Underlying = self.underlying

      override def createAndRegisterDoubleCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Counter[G, Double, A]] =
        self
          .createAndRegisterDoubleCounter(
            prefix, name, help, commonLabels, labelNames
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterDoubleGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Gauge[G, Double, A]] =
        self
          .createAndRegisterDoubleGauge(
            prefix, name, help, commonLabels, labelNames
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterDoubleHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): Resource[G, Histogram[G, Double, A]] =
        self
          .createAndRegisterDoubleHistogram(
            prefix, name, help, commonLabels, labelNames, buckets
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterDoubleNativeHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          config: NativeHistogram
      )(f: A => IndexedSeq[String]): Resource[G, Histogram[G, Double, A]] =
        self
          .createAndRegisterDoubleNativeHistogram(
            prefix, name, help, commonLabels, labelNames, config
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongNativeHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          config: NativeHistogram
      )(f: A => IndexedSeq[String]): Resource[G, Histogram[G, Long, A]] =
        self
          .createAndRegisterLongNativeHistogram(
            prefix, name, help, commonLabels, labelNames, config
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterDoubleHistogramWithNative[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double],
          nativeHistogram: NativeHistogram
      )(f: A => IndexedSeq[String]): Resource[G, Histogram[G, Double, A]] =
        self
          .createAndRegisterDoubleHistogramWithNative(
            prefix, name, help, commonLabels, labelNames, buckets, nativeHistogram
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongHistogramWithNative[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Long],
          nativeHistogram: NativeHistogram
      )(f: A => IndexedSeq[String]): Resource[G, Histogram[G, Long, A]] =
        self
          .createAndRegisterLongHistogramWithNative(prefix, name, help, commonLabels, labelNames, buckets,
            nativeHistogram)(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Counter[G, Long, A]] =
        self
          .createAndRegisterLongCounter(prefix, name, help, commonLabels, labelNames)(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Gauge[G, Long, A]] =
        self
          .createAndRegisterLongGauge(prefix, name, help, commonLabels, labelNames)(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Long]
      )(f: A => IndexedSeq[String]): Resource[G, Histogram[G, Long, A]] =
        self
          .createAndRegisterLongHistogram(prefix, name, help, commonLabels, labelNames, buckets)(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterDoubleSummary[A](
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
          .createAndRegisterDoubleSummary(
            prefix, name, help, commonLabels, labelNames, quantiles, maxAge, ageBuckets
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterLongSummary[A](
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
          .createAndRegisterLongSummary(
            prefix, name, help, commonLabels, labelNames, quantiles, maxAge, ageBuckets
          )(f)
          .mapK(fk)
          .map(_.mapK(fk))

      override def createAndRegisterInfo[A](
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): Resource[G, Info[G, A]] =
        self.createAndRegisterInfo(prefix, name, help, labelNames)(f).mapK(fk).map(_.mapK(fk))

    }

}
