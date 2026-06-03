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

import cats.Applicative
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource
import cats.~>

import prometheus4cats.Metric.CommonLabels
import prometheus4cats.internal._
import prometheus4cats.internal.histogram.BucketDsl
import prometheus4cats.internal.summary.SummaryDsl

/** A factory for creating and registering Prometheus metrics using a fluent DSL.
  *
  * [[MetricFactory]] provides a type-safe builder interface for creating counters, gauges, histograms, summaries, and
  * info metrics. Metrics are registered against a [[MetricRegistry]] which handles the actual storage and exposure of
  * metric values.
  *
  * @example
  *   {{{
  * val factory: MetricFactory[IO] = ???
  *
  * val counter: Resource[IO, Counter[IO, Double, Unit]] =
  *   factory.counter("my_counter")
  *     .help("Total number of requests")
  *     .build
  *   }}}
  *
  * @tparam F
  *   the effect type
  * @param metricRegistry
  *   the underlying [[MetricRegistry]] used to register metrics
  * @param prefix
  *   optional prefix to prepend to all metric names created by this factory
  * @param commonLabels
  *   labels to add to all metrics created by this factory
  */
sealed abstract class MetricFactory[F[_]](
    val metricRegistry: MetricRegistry[F],
    val prefix: Option[Metric.Prefix],
    val commonLabels: CommonLabels
) {

  /** Given a natural transformation from `F` to `G`, transforms this [[MetricFactory]] from effect `F` to effect `G`.
    * The G constraint can also be satisfied by requiring a Functor[G].
    */
  def mapK[G[_]](fk: F ~> G)(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): MetricFactory[G] =
    new MetricFactory[G](
      metricRegistry.mapK(fk),
      prefix,
      commonLabels
    ) {}

  type GaugeDsl[MDsl[_[_], _, _[_[_], _, _]], A] = HelpStep[MDsl[F, A, Gauge]]

  /** Starts creating a "gauge" metric.
    *
    * @example
    *   {{{ metrics.gauge("my_gauge").help("my gauge help").label[Int]("first_label")
    *   .label[String]("second_label").label[Boolean]("third_label") .build }}}
    * @param name
    *   [[Gauge.Name]] value
    * @return
    *   Gauge builder
    */
  def gauge(name: Gauge.Name): TypeStep[GaugeDsl[MetricDsl, *]] =
    new TypeStep[GaugeDsl[MetricDsl, *]](
      new HelpStep(help =>
        new MetricDsl(
          new LabelledMetricPartiallyApplied[F, Long, Gauge] {

            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): Resource[F, Gauge[F, Long, B]] =
              metricRegistry.createAndRegisterLongGauge(prefix, name, help, commonLabels, labels)(f)

          }
        )
      ),
      new HelpStep(help =>
        new MetricDsl(
          new LabelledMetricPartiallyApplied[F, Double, Gauge] {

            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): Resource[F, Gauge[F, Double, B]] =
              metricRegistry.createAndRegisterDoubleGauge(prefix, name, help, commonLabels, labels)(f)

          }
        )
      )
    )

  type CounterDsl[MDsl[_[_], _, _[_[_], _, _]], A] = HelpStep[MDsl[F, A, Counter]]

  /** Starts creating a "counter" metric.
    *
    * @example
    *   {{{ metrics.counter("my_counter").help("my counter help") .label[Int]("first_label")
    *   .label[String]("second_label") .label[Boolean]("third_label") .build }}}
    * @param name
    *   [[Counter.Name]] value
    * @return
    *   Counter builder
    */
  def counter(name: Counter.Name): TypeStep[CounterDsl[MetricDsl, *]] =
    new TypeStep[CounterDsl[MetricDsl, *]](
      new HelpStep(help =>
        new MetricDsl(
          new LabelledMetricPartiallyApplied[F, Long, Counter] {

            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): Resource[F, Counter[F, Long, B]] =
              metricRegistry.createAndRegisterLongCounter(prefix, name, help, commonLabels, labels)(f)

          }
        )
      ),
      new HelpStep(help =>
        new MetricDsl(
          new LabelledMetricPartiallyApplied[F, Double, Counter] {

            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): Resource[F, Counter[F, Double, B]] =
              metricRegistry.createAndRegisterDoubleCounter(prefix, name, help, commonLabels, labels)(f)

          }
        )
      )
    )

  type HistogramDsl[MDsl[_[_], _, _[_[_], _, _]], A] = HelpStep[BucketDsl[MDsl[F, A, Histogram], A]]

  /** Type alias for the histogram DSL chain returned by [[histogram]]. After `.buckets(...)` the chain produces a value
    * extending both `MetricDsl[F, A, Histogram]` and the `HistogramMetricDsl` mixin (which adds `.withNative` for
    * dual-mode promotion).
    */
  type HistogramWithNativeDsl[A] = HelpStep[BucketDsl[HistogramMetricDsl[F, A], A]]

  /** Starts creating a "histogram" metric.
    *
    * After declaring buckets, the resulting builder accepts an optional `.withNative` step that promotes the histogram
    * from classic-only (default) to dual-mode emission (classic + native exponential — the NHCB-friendly configuration
    * that preserves curated bucket boundaries while also enabling native histogram benefits).
    *
    * @example
    *   {{{
    * // Classic-only (existing behaviour):
    * metrics.histogram("http_dur").help("...").buckets(0.005, 0.01, 0.025, 0.05, 0.1, 0.5, 1, 5, 10).build
    *
    * // Dual-mode (NHCB-friendly): same buckets, plus native exponential.
    * metrics.histogram("http_dur").help("...")
    *   .buckets(0.005, 0.01, 0.025, 0.05, 0.1, 0.5, 1, 5, 10)
    *   .withNative
    *   .build
    *
    * // Dual-mode with custom native tuning.
    * metrics.histogram("http_dur").help("...")
    *   .buckets(0.005, 0.01, ...)
    *   .withNative(NativeHistogram.Default.withInitialSchema(6))
    *   .build
    *   }}}
    * @param name
    *   [[Histogram.Name]] value
    * @return
    *   Histogram builder
    */
  def histogram(name: Histogram.Name): TypeStep[HistogramWithNativeDsl] =
    new TypeStep[HistogramWithNativeDsl](
      new HelpStep(help =>
        new BucketDsl[HistogramMetricDsl[F, Long], Long](buckets =>
          new HistogramMetricDsl.Plain[F, Long](
            classicMakeMetric = new LabelledMetricPartiallyApplied[F, Long, Histogram] {

              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): Resource[F, Histogram[F, Long, B]] =
                metricRegistry
                  .createAndRegisterLongHistogram(prefix, name, help, commonLabels, labels, buckets)(f)

            },
            makeWithNativeMetric = (nativeCfg: NativeHistogram) =>
              new LabelledMetricPartiallyApplied[F, Long, Histogram] {

                override def apply[B](
                    labels: IndexedSeq[Label.Name]
                )(f: B => IndexedSeq[String]): Resource[F, Histogram[F, Long, B]] =
                  metricRegistry.createAndRegisterLongHistogramWithNative(
                    prefix, name, help, commonLabels, labels, buckets, nativeCfg
                  )(f)

              }
          )
        )
      ),
      new HelpStep(help =>
        new BucketDsl[HistogramMetricDsl[F, Double], Double](buckets =>
          new HistogramMetricDsl.Plain[F, Double](
            classicMakeMetric = new LabelledMetricPartiallyApplied[F, Double, Histogram] {

              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): Resource[F, Histogram[F, Double, B]] =
                metricRegistry.createAndRegisterDoubleHistogram(
                  prefix, name, help, commonLabels, labels, buckets
                )(f)

            },
            makeWithNativeMetric = (nativeCfg: NativeHistogram) =>
              new LabelledMetricPartiallyApplied[F, Double, Histogram] {

                override def apply[B](
                    labels: IndexedSeq[Label.Name]
                )(f: B => IndexedSeq[String]): Resource[F, Histogram[F, Double, B]] =
                  metricRegistry.createAndRegisterDoubleHistogramWithNative(
                    prefix, name, help, commonLabels, labels, buckets, nativeCfg
                  )(f)

              }
          )
        )
      )
    )

  type NativeHistogramWithNativeDsl[A] = HelpStep[MetricDsl[F, A, Histogram]]

  /** Starts creating a "native histogram" metric.
    *
    * Native histograms (sometimes called sparse or exponential histograms) automatically allocate buckets sized by an
    * exponential schema, so consumers do not pre-declare bucket boundaries. They typically reduce metric cardinality
    * compared to a classic histogram with explicit buckets.
    *
    * Native histograms are emitted only over Prometheus protobuf scrape negotiation. Ensure your Prometheus server
    * supports native histograms (Prometheus 2.40+) and that `ServiceMonitor.spec.scrapeProtocols` includes
    * `PrometheusProto`.
    *
    * @note
    *   Native histograms are `Double`-only by design — there is no `.ofLong` step, since the underlying buckets are
    *   real-valued regardless of input type. Consumers observing integer-typed values should convert at the call site
    *   (e.g. `.contramap(_.toDouble)`).
    *
    * @example
    *   {{{
    * metrics.nativeHistogram("my_histogram").help("...").label[Int]("first_label").build
    *
    * // with custom tuning:
    * metrics
    *   .nativeHistogram("my_histogram", NativeHistogram.Default.withInitialSchema(6))
    *   .help("...")
    *   .label[Int]("first_label")
    *   .build
    *   }}}
    *
    * @param name
    *   [[Histogram.Name]] value
    * @param config
    *   tuning parameters for the native histogram. Defaults to [[NativeHistogram.Default]].
    * @return
    *   Native histogram builder
    */
  def nativeHistogram(
      name: Histogram.Name,
      config: NativeHistogram = NativeHistogram.Default
  ) = new TypeStep[NativeHistogramWithNativeDsl](
    new HelpStep(help =>
      new MetricDsl(
        new LabelledMetricPartiallyApplied[F, Long, Histogram] {

          override def apply[B](
              labels: IndexedSeq[Label.Name]
          )(f: B => IndexedSeq[String]): Resource[F, Histogram[F, Long, B]] =
            metricRegistry.createAndRegisterLongNativeHistogram(
              prefix, name, help, commonLabels, labels, config
            )(f)

        }
      )
    ),
    new HelpStep(help =>
      new MetricDsl(
        new LabelledMetricPartiallyApplied[F, Double, Histogram] {

          override def apply[B](
              labels: IndexedSeq[Label.Name]
          )(f: B => IndexedSeq[String]): Resource[F, Histogram[F, Double, B]] =
            metricRegistry.createAndRegisterDoubleNativeHistogram(
              prefix, name, help, commonLabels, labels, config
            )(f)

        }
      )
    )
  )

  type SummaryDslLambda[A] = HelpStep[SummaryDsl.Base[F, A]]

  /** Starts creating a "summary" metric.
    *
    * @example
    *   {{{
    * metrics.summary("my_summary")
    *   .help("Request latency distribution")
    *   .quantile(0.5, 0.05)
    *   .quantile(0.9, 0.01)
    *   .label[String]("endpoint")
    *   .build
    *   }}}
    * @param name
    *   [[Summary.Name]] value
    * @return
    *   Summary builder
    */
  def summary(name: Summary.Name): TypeStep[SummaryDslLambda] =
    new TypeStep[SummaryDslLambda](
      new HelpStep(help =>
        new SummaryDsl[F, Long](
          makeSummary = (quantiles, maxAge, ageBuckets) =>
            new LabelledMetricPartiallyApplied[F, Long, Summary] {

              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): Resource[F, Summary[F, Long, B]] =
                metricRegistry.createAndRegisterLongSummary(
                  prefix, name, help, commonLabels, labels, quantiles, maxAge, ageBuckets
                )(f)

            }
        )
      ),
      new HelpStep(help =>
        new SummaryDsl[F, Double](
          makeSummary = (quantiles, maxAge, ageBuckets) =>
            new LabelledMetricPartiallyApplied[F, Double, Summary] {

              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): Resource[F, Summary[F, Double, B]] =
                metricRegistry.createAndRegisterDoubleSummary(
                  prefix, name, help, commonLabels, labels, quantiles, maxAge, ageBuckets
                )(f)

            }
        )
      )
    )

  /** Starts creating an "info" metric.
    *
    * Info metrics declare their labels at build time using the same `.label[T](...)` / `.labels[T](...)` /
    * `.labelsFrom[T]` builders that counters, gauges, histograms, and summaries already use. Calling `.build` without
    * any label declarations returns an `Info[F, Unit]` that emits as `name 1` with no labels.
    *
    * @example
    *   {{{
    * metrics.info("build_info").help("build info").labelsFrom[BuildInfo].build
    *
    * metrics.info("build_info").help("build info")
    *   .label[String]("version")
    *   .label[String]("commit")
    *   .build
    *
    * metrics.info("app_info").help("...").build  // no-label form, emits `app_info 1`
    *   }}}
    *
    * @param name
    *   [[Info.Name]] value
    * @return
    *   Info builder
    */
  def info(name: Info.Name): HelpStep[MetricDsl[F, Unit, InfoL]] =
    new HelpStep(help =>
      new MetricDsl(
        new LabelledMetricPartiallyApplied[F, Unit, InfoL] {

          override def apply[B](
              labels: IndexedSeq[Label.Name]
          )(f: B => IndexedSeq[String]): Resource[F, Info[F, B]] =
            metricRegistry.createAndRegisterInfo(prefix, name, help, labels)(f)

        }
      )
    )

  /** Creates a new instance of [[MetricFactory]] without a [[Metric.Prefix]] set */
  def dropPrefix: MetricFactory[F] = new MetricFactory[F](metricRegistry, None, commonLabels) {}

  /** Creates a new instance of [[MetricFactory]] with the given [[Metric.Prefix]] set */
  def withPrefix(prefix: Metric.Prefix): MetricFactory[F] =
    new MetricFactory[F](metricRegistry, Some(prefix), commonLabels) {}

  /** Creates a new instance of [[MetricFactory]] with any [[Metric.CommonLabels]] */
  def dropCommonLabels: MetricFactory[F] = new MetricFactory[F](metricRegistry, prefix, CommonLabels.empty) {}

  /** Creates a new instance of [[MetricFactory]] with the provided [[Metric.CommonLabels]] */
  def withCommonLabels(commonLabels: CommonLabels): MetricFactory[F] =
    new MetricFactory[F](metricRegistry, prefix, commonLabels) {}

}

object MetricFactory {

  /** Source-compat alias for v5 callers. Callback support was dropped in v6; this is now indistinguishable from
    * [[MetricFactory]]. Existing references like `MetricFactory.WithCallbacks[IO]` continue to type-check but no longer
    * unlock a callback DSL — `.callback(...)` is gone. Migrate to [[MetricFactory]] at your convenience; this alias will
    * be removed in a future release.
    */
  type WithCallbacks[F[_]] = MetricFactory[F]

  /** Create an instance of [[MetricFactory]] that performs no operations */
  def noop[F[_]: Applicative]: MetricFactory[F] =
    new MetricFactory[F](
      MetricRegistry.noop,
      None,
      CommonLabels.empty
    ) {}

  /** Builder for [[MetricFactory]] */
  class Builder private[prometheus4cats] (
      prefix: Option[Metric.Prefix] = None,
      commonLabels: CommonLabels = CommonLabels.empty
  ) {

    /** Add a prefix to all metrics created by the [[MetricFactory]]
      *
      * @param prefix
      *   [[Metric.Prefix]]
      */
    def withPrefix(prefix: Metric.Prefix): Builder =
      new Builder(Some(prefix), commonLabels)

    /** Add the given labels to all metrics created by the [[MetricFactory]]
      *
      * @param labels
      *   [[Metric.CommonLabels]]
      */
    def withCommonLabels(labels: CommonLabels): Builder =
      new Builder(prefix, labels)

    /** Build a [[MetricFactory]] from a [[MetricRegistry]]
      *
      * @param metricRegistry
      *   [[MetricRegistry]] with which to register new metrics created by the built [[MetricFactory]]
      * @return
      *   a new [[MetricFactory]] instance
      */
    def build[F[_]](metricRegistry: MetricRegistry[F]): MetricFactory[F] =
      new MetricFactory[F](metricRegistry, prefix, commonLabels) {}

    /** Build a [[MetricFactory]] the performs no operations
      *
      * @return
      *   a new [[MetricFactory]] instance that performs no operations
      */
    def noop[F[_]: Applicative]: MetricFactory[F] = MetricFactory.noop[F]

  }

  /** Construct a [[MetricFactory]] using [[MetricFactory.Builder]] */
  def builder = new Builder()

}
