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

import cats.{Applicative, Functor, ~>}
import prometheus4cats.Metric.CommonLabels
import prometheus4cats.internal._
import prometheus4cats.internal.histogram.BucketDsl
import prometheus4cats.internal.summary.SummaryDsl

sealed abstract class MetricFactory[F[_]](
    protected[prometheus4cats] val metricRegistry: MetricRegistry[F],
    val prefix: Option[Metric.Prefix],
    val commonLabels: CommonLabels
) {

  /** Given a natural transformation from `F` to `G`, transforms this [[MetricFactory]] from effect `F` to effect `G`.
    * The G constraint can also be satisfied by requiring a Functor[G].
    */
  def mapK[G[_]: Functor](fk: F ~> G): MetricFactory[G] =
    new MetricFactory[G](
      metricRegistry.mapK(fk),
      prefix,
      commonLabels
    ) {}

  type GaugeDsl[MDsl[_[_], _, _[_[_], _], _[_[_], _, _]], A] = HelpStep[MDsl[F, A, Gauge, Gauge.Labelled]]

  /** Starts creating a "gauge" metric.
    *
    * @example
    *   {{{ metrics.gauge("my_gauge").ofDouble.help("my gauge help").label[Int]("first_label")
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
          metricRegistry.createAndRegisterLongGauge(prefix, name, help, commonLabels),
          new LabelledMetricPartiallyApplied[F, Long, Gauge.Labelled] {
            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): F[Gauge.Labelled[F, Long, B]] =
              metricRegistry.createAndRegisterLabelledLongGauge(prefix, name, help, commonLabels, labels)(f)
          }
        )
      ),
      new HelpStep(help =>
        new MetricDsl(
          metricRegistry.createAndRegisterDoubleGauge(prefix, name, help, commonLabels),
          new LabelledMetricPartiallyApplied[F, Double, Gauge.Labelled] {
            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): F[Gauge.Labelled[F, Double, B]] =
              metricRegistry.createAndRegisterLabelledDoubleGauge(prefix, name, help, commonLabels, labels)(f)
          }
        )
      )
    )

  type CounterDsl[MDsl[_[_], _, _[_[_], _], _[_[_], _, _]], A] = HelpStep[MDsl[F, A, Counter, Counter.Labelled]]

  /** Starts creating a "counter" metric.
    *
    * @example
    *   {{{ metrics.counter("my_counter").ofLong.help("my counter help") .label[Int]("first_label")
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
          metricRegistry.createAndRegisterLongCounter(prefix, name, help, commonLabels),
          new LabelledMetricPartiallyApplied[F, Long, Counter.Labelled] {
            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): F[Counter.Labelled[F, Long, B]] =
              metricRegistry.createAndRegisterLabelledLongCounter(prefix, name, help, commonLabels, labels)(f)
          }
        )
      ),
      new HelpStep(help =>
        new MetricDsl(
          metricRegistry.createAndRegisterDoubleCounter(prefix, name, help, commonLabels),
          new LabelledMetricPartiallyApplied[F, Double, Counter.Labelled] {
            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): F[Counter.Labelled[F, Double, B]] =
              metricRegistry.createAndRegisterLabelledDoubleCounter(prefix, name, help, commonLabels, labels)(f)
          }
        )
      )
    )

  type HistogramDsl[MDsl[_[_], _, _[_[_], _], _[_[_], _, _]], A] =
    HelpStep[BucketDsl[MDsl[F, A, Histogram, Histogram.Labelled], A]]

  /** Starts creating a "histogram" metric.
    *
    * @example
    *   {{{ metrics.histogram("my_histogram").ofDouble.help("my counter help").buckets(1.0, 2.0)
    *   .label[Int]("first_label").label[String]("second_label").label[Boolean]("third_label") .build }}}
    * @param name
    *   [[Histogram.Name]] value
    * @return
    *   Histogram builder
    */
  def histogram(name: Histogram.Name): TypeStep[HistogramDsl[MetricDsl, *]] =
    new TypeStep[HistogramDsl[MetricDsl, *]](
      new HelpStep(help =>
        new BucketDsl[MetricDsl[F, Long, Histogram, Histogram.Labelled], Long](buckets =>
          new MetricDsl(
            metricRegistry.createAndRegisterLongHistogram(prefix, name, help, commonLabels, buckets),
            new LabelledMetricPartiallyApplied[F, Long, Histogram.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Histogram.Labelled[F, Long, B]] =
                metricRegistry
                  .createAndRegisterLabelledLongHistogram(prefix, name, help, commonLabels, labels, buckets)(f)
            }
          )
        )
      ),
      new HelpStep(help =>
        new BucketDsl[MetricDsl[F, Double, Histogram, Histogram.Labelled], Double](buckets =>
          new MetricDsl(
            metricRegistry.createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets),
            new LabelledMetricPartiallyApplied[F, Double, Histogram.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Histogram.Labelled[F, Double, B]] =
                metricRegistry.createAndRegisterLabelledDoubleHistogram(
                  prefix,
                  name,
                  help,
                  commonLabels,
                  labels,
                  buckets
                )(f)
            }
          )
        )
      )
    )

  type SummaryDslLambda[A] = HelpStep[SummaryDsl.Base[F, A]]

  def summary(name: Summary.Name): TypeStep[SummaryDslLambda] =
    new TypeStep[SummaryDslLambda](
      new HelpStep(help =>
        new SummaryDsl[F, Long](
          makeSummary = (quantiles, maxAge, ageBuckets) =>
            metricRegistry
              .createAndRegisterLongSummary(prefix, name, help, commonLabels, quantiles, maxAge, ageBuckets),
          makeLabelledSummary = (quantiles, maxAge, ageBuckets) =>
            new LabelledMetricPartiallyApplied[F, Long, Summary.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Summary.Labelled[F, Long, B]] =
                metricRegistry.createAndRegisterLabelledLongSummary(
                  prefix,
                  name,
                  help,
                  commonLabels,
                  labels,
                  quantiles,
                  maxAge,
                  ageBuckets
                )(f)
            }
        )
      ),
      new HelpStep(help =>
        new SummaryDsl[F, Double](
          makeSummary = (quantiles, maxAge, ageBuckets) =>
            metricRegistry
              .createAndRegisterDoubleSummary(prefix, name, help, commonLabels, quantiles, maxAge, ageBuckets),
          makeLabelledSummary = (quantiles, maxAge, ageBuckets) =>
            new LabelledMetricPartiallyApplied[F, Double, Summary.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Summary.Labelled[F, Double, B]] =
                metricRegistry.createAndRegisterLabelledDoubleSummary(
                  prefix,
                  name,
                  help,
                  commonLabels,
                  labels,
                  quantiles,
                  maxAge,
                  ageBuckets
                )(f)
            }
        )
      )
    )

  /** Starts creating an "info" metric.
    *
    * @example
    *   {{{metrics.info("app_info").help("my counter help").build}}}
    * @param name
    *   [[Info.Name]] value
    * @return
    *   Info builder
    */
  def info(name: Info.Name): HelpStep[BuildStep[F, Info[F, Map[Label.Name, String]]]] =
    new HelpStep(help => BuildStep(metricRegistry.createAndRegisterInfo(prefix, name, help)))

  /** Creates a new instance of [[MetricFactory]] without a [[Metric.Prefix]] set
    */
  def dropPrefix: MetricFactory[F] = new MetricFactory[F](metricRegistry, None, commonLabels) {}

  /** Creates a new instance of [[MetricFactory]] with the given [[Metric.Prefix]] set
    */
  def withPrefix(prefix: Metric.Prefix): MetricFactory[F] =
    new MetricFactory[F](metricRegistry, Some(prefix), commonLabels) {}

  /** Creates a new instance of [[MetricFactory]] with any [[Metric.CommonLabels]]
    */
  def dropCommonLabels: MetricFactory[F] = new MetricFactory[F](metricRegistry, prefix, CommonLabels.empty) {}

  /** Creates a new instance of [[MetricFactory]] with the provided [[Metric.CommonLabels]]
    */
  def withCommonLabels(commonLabels: CommonLabels): MetricFactory[F] =
    new MetricFactory[F](metricRegistry, prefix, commonLabels) {}
}

object MetricFactory {

  /** Subtype of [[MetricFactory]] that can register metric callbacks with the DSL
    *
    * @note
    *   Calling [[MetricFactory.WithCallbacks.mapK]] will return a [[MetricFactory]] only. To change the type of `F` and
    *   return a [[MetricFactory.WithCallbacks]] you must you [[MetricFactory.WithCallbacks.imapK]].
    */
  sealed abstract class WithCallbacks[F[_]](
      override protected[prometheus4cats] val metricRegistry: MetricRegistry[F],
      private val callbackRegistry: CallbackRegistry[F],
      prefix: Option[Metric.Prefix],
      commonLabels: CommonLabels
  ) extends MetricFactory[F](metricRegistry, prefix, commonLabels) {

    /** Given a natural transformation from `F` to `G` and from `G` to `F`, transforms this
      * [[MetricFactory.WithCallbacks]] from effect `F` to effect `G`. The G constraint can also be satisfied by
      * requiring a Functor[G].
      */
    def imapK[G[_]: Functor](fk: F ~> G, gk: G ~> F): WithCallbacks[G] = new WithCallbacks[G](
      metricRegistry.mapK(fk),
      callbackRegistry.imapK(fk, gk),
      prefix,
      commonLabels
    ) {}

    type SimpleCallbackDsl[G[_], A, M[_[_], _], L[_[_], _, _]] = MetricDsl.WithCallbacks[G, A, A, M, L]

    /** @inheritdoc
      */
    override def gauge(name: Gauge.Name): TypeStep[GaugeDsl[SimpleCallbackDsl, *]] =
      new TypeStep[GaugeDsl[SimpleCallbackDsl, *]](
        new HelpStep(help =>
          new MetricDsl.WithCallbacks(
            metricRegistry.createAndRegisterLongGauge(prefix, name, help, commonLabels),
            cb => callbackRegistry.registerLongGaugeCallback(prefix, name, help, commonLabels, cb),
            new LabelledMetricPartiallyApplied[F, Long, Gauge.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Gauge.Labelled[F, Long, B]] =
                metricRegistry.createAndRegisterLabelledLongGauge(prefix, name, help, commonLabels, labels)(f)
            },
            new LabelledCallbackPartiallyApplied[F, Long] {
              override def apply[B](labels: IndexedSeq[Label.Name], callback: F[(Long, B)])(
                  f: B => IndexedSeq[String]
              ): F[Unit] =
                callbackRegistry.registerLabelledLongGaugeCallback(prefix, name, help, commonLabels, labels, callback)(
                  f
                )
            }
          )
        ),
        new HelpStep(help =>
          new MetricDsl.WithCallbacks(
            metricRegistry.createAndRegisterDoubleGauge(prefix, name, help, commonLabels),
            cb => callbackRegistry.registerDoubleGaugeCallback(prefix, name, help, commonLabels, cb),
            new LabelledMetricPartiallyApplied[F, Double, Gauge.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Gauge.Labelled[F, Double, B]] =
                metricRegistry.createAndRegisterLabelledDoubleGauge(prefix, name, help, commonLabels, labels)(f)
            },
            new LabelledCallbackPartiallyApplied[F, Double] {
              override def apply[B](labels: IndexedSeq[Label.Name], callback: F[(Double, B)])(
                  f: B => IndexedSeq[String]
              ): F[Unit] =
                callbackRegistry.registerLabelledDoubleGaugeCallback(
                  prefix,
                  name,
                  help,
                  commonLabels,
                  labels,
                  callback
                )(
                  f
                )
            }
          )
        )
      )

    /** @inheritdoc
      */
    override def counter(name: Counter.Name): TypeStep[CounterDsl[SimpleCallbackDsl, *]] =
      new TypeStep[CounterDsl[SimpleCallbackDsl, *]](
        new HelpStep(help =>
          new MetricDsl.WithCallbacks(
            metricRegistry.createAndRegisterLongCounter(prefix, name, help, commonLabels),
            cb => callbackRegistry.registerLongCounterCallback(prefix, name, help, commonLabels, cb),
            new LabelledMetricPartiallyApplied[F, Long, Counter.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Counter.Labelled[F, Long, B]] =
                metricRegistry.createAndRegisterLabelledLongCounter(prefix, name, help, commonLabels, labels)(f)
            },
            new LabelledCallbackPartiallyApplied[F, Long] {
              override def apply[B](labels: IndexedSeq[Label.Name], callback: F[(Long, B)])(
                  f: B => IndexedSeq[String]
              ): F[Unit] =
                callbackRegistry
                  .registerLabelledLongCounterCallback(prefix, name, help, commonLabels, labels, callback)(
                    f
                  )
            }
          )
        ),
        new HelpStep(help =>
          new MetricDsl.WithCallbacks(
            metricRegistry.createAndRegisterDoubleCounter(prefix, name, help, commonLabels),
            cb => callbackRegistry.registerDoubleCounterCallback(prefix, name, help, commonLabels, cb),
            new LabelledMetricPartiallyApplied[F, Double, Counter.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Counter.Labelled[F, Double, B]] =
                metricRegistry.createAndRegisterLabelledDoubleCounter(prefix, name, help, commonLabels, labels)(f)
            },
            new LabelledCallbackPartiallyApplied[F, Double] {
              override def apply[B](labels: IndexedSeq[Label.Name], callback: F[(Double, B)])(
                  f: B => IndexedSeq[String]
              ): F[Unit] =
                callbackRegistry.registerLabelledDoubleCounterCallback(
                  prefix,
                  name,
                  help,
                  commonLabels,
                  labels,
                  callback
                )(
                  f
                )
            }
          )
        )
      )

    type HistogramCallbackDsl[G[_], A, M[_[_], _], L[_[_], _, _]] =
      MetricDsl.WithCallbacks[G, A, Histogram.Value[A], M, L]

    /** @inheritdoc
      */
    override def histogram(name: Histogram.Name): TypeStep[HistogramDsl[HistogramCallbackDsl, *]] =
      new TypeStep[HistogramDsl[HistogramCallbackDsl, *]](
        new HelpStep(help =>
          new BucketDsl[MetricDsl.WithCallbacks[F, Long, Histogram.Value[Long], Histogram, Histogram.Labelled], Long](
            buckets =>
              new MetricDsl.WithCallbacks(
                metricRegistry.createAndRegisterLongHistogram(prefix, name, help, commonLabels, buckets),
                cb => callbackRegistry.registerLongHistogramCallback(prefix, name, help, commonLabels, buckets, cb),
                new LabelledMetricPartiallyApplied[F, Long, Histogram.Labelled] {
                  override def apply[B](
                      labels: IndexedSeq[Label.Name]
                  )(f: B => IndexedSeq[String]): F[Histogram.Labelled[F, Long, B]] =
                    metricRegistry
                      .createAndRegisterLabelledLongHistogram(prefix, name, help, commonLabels, labels, buckets)(f)
                },
                new LabelledCallbackPartiallyApplied[F, Histogram.Value[Long]] {
                  override def apply[B](labels: IndexedSeq[Label.Name], callback: F[(Histogram.Value[Long], B)])(
                      f: B => IndexedSeq[String]
                  ): F[Unit] =
                    callbackRegistry
                      .registerLabelledLongHistogramCallback(
                        prefix,
                        name,
                        help,
                        commonLabels,
                        labels,
                        buckets,
                        callback
                      )(
                        f
                      )
                }
              )
          )
        ),
        new HelpStep(help =>
          new BucketDsl[
            MetricDsl.WithCallbacks[F, Double, Histogram.Value[Double], Histogram, Histogram.Labelled],
            Double
          ](buckets =>
            new MetricDsl.WithCallbacks(
              metricRegistry.createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets),
              cb => callbackRegistry.registerDoubleHistogramCallback(prefix, name, help, commonLabels, buckets, cb),
              new LabelledMetricPartiallyApplied[F, Double, Histogram.Labelled] {
                override def apply[B](
                    labels: IndexedSeq[Label.Name]
                )(f: B => IndexedSeq[String]): F[Histogram.Labelled[F, Double, B]] =
                  metricRegistry
                    .createAndRegisterLabelledDoubleHistogram(prefix, name, help, commonLabels, labels, buckets)(f)
              },
              new LabelledCallbackPartiallyApplied[F, Histogram.Value[Double]] {
                override def apply[B](labels: IndexedSeq[Label.Name], callback: F[(Histogram.Value[Double], B)])(
                    f: B => IndexedSeq[String]
                ): F[Unit] =
                  callbackRegistry
                    .registerLabelledDoubleHistogramCallback(
                      prefix,
                      name,
                      help,
                      commonLabels,
                      labels,
                      buckets,
                      callback
                    )(
                      f
                    )
              }
            )
          )
        )
      )

    type SummaryCallbackDsl[A] =
      HelpStep[SummaryDsl.WithCallbacks[F, A, Summary.Value[A]]]

    override def summary(name: Summary.Name): TypeStep[SummaryCallbackDsl] =
      new TypeStep[SummaryCallbackDsl](
        new HelpStep(help =>
          new SummaryDsl.WithCallbacks[F, Long, Summary.Value[Long]](
            makeSummary = (quantiles, maxAge, ageBuckets) =>
              metricRegistry
                .createAndRegisterLongSummary(prefix, name, help, commonLabels, quantiles, maxAge, ageBuckets),
            makeSummaryCallback = callbackRegistry.registerLongSummaryCallback(prefix, name, help, commonLabels, _),
            makeLabelledSummary = (quantiles, maxAge, ageBuckets) =>
              new LabelledMetricPartiallyApplied[F, Long, Summary.Labelled] {
                override def apply[B](
                    labels: IndexedSeq[Label.Name]
                )(f: B => IndexedSeq[String]): F[Summary.Labelled[F, Long, B]] =
                  metricRegistry.createAndRegisterLabelledLongSummary(
                    prefix,
                    name,
                    help,
                    commonLabels,
                    labels,
                    quantiles,
                    maxAge,
                    ageBuckets
                  )(f)
              },
            makeLabelledSummaryCallback = new LabelledCallbackPartiallyApplied[F, Summary.Value[Long]] {
              override def apply[B](labels: IndexedSeq[Label.Name], callback: F[(Summary.Value[Long], B)])(
                  f: B => IndexedSeq[String]
              ): F[Unit] =
                callbackRegistry.registerLabelledLongSummaryCallback(
                  prefix,
                  name,
                  help,
                  commonLabels,
                  labels,
                  callback
                )(f)
            }
          )
        ),
        new HelpStep(help =>
          new SummaryDsl.WithCallbacks[F, Double, Summary.Value[Double]](
            makeSummary = (quantiles, maxAge, ageBuckets) =>
              metricRegistry
                .createAndRegisterDoubleSummary(prefix, name, help, commonLabels, quantiles, maxAge, ageBuckets),
            makeSummaryCallback = callbackRegistry.registerDoubleSummaryCallback(prefix, name, help, commonLabels, _),
            makeLabelledSummary = (quantiles, maxAge, ageBuckets) =>
              new LabelledMetricPartiallyApplied[F, Double, Summary.Labelled] {
                override def apply[B](
                    labels: IndexedSeq[Label.Name]
                )(f: B => IndexedSeq[String]): F[Summary.Labelled[F, Double, B]] =
                  metricRegistry.createAndRegisterLabelledDoubleSummary(
                    prefix,
                    name,
                    help,
                    commonLabels,
                    labels,
                    quantiles,
                    maxAge,
                    ageBuckets
                  )(f)
              },
            makeLabelledSummaryCallback = new LabelledCallbackPartiallyApplied[F, Summary.Value[Double]] {
              override def apply[B](labels: IndexedSeq[Label.Name], callback: F[(Summary.Value[Double], B)])(
                  f: B => IndexedSeq[String]
              ): F[Unit] =
                callbackRegistry.registerLabelledDoubleSummaryCallback(
                  prefix,
                  name,
                  help,
                  commonLabels,
                  labels,
                  callback
                )(f)
            }
          )
        )
      )

    /** @inheritdoc
      */
    override def dropPrefix: MetricFactory.WithCallbacks[F] =
      new MetricFactory.WithCallbacks[F](metricRegistry, callbackRegistry, None, commonLabels) {}

    /** @inheritdoc
      */
    override def withPrefix(prefix: Metric.Prefix): MetricFactory.WithCallbacks[F] =
      new MetricFactory.WithCallbacks[F](metricRegistry, callbackRegistry, Some(prefix), commonLabels) {}

    /** @inheritdoc
      */
    override def dropCommonLabels: MetricFactory.WithCallbacks[F] =
      new MetricFactory.WithCallbacks[F](metricRegistry, callbackRegistry, prefix, CommonLabels.empty) {}

    /** @inheritdoc
      */
    override def withCommonLabels(commonLabels: CommonLabels): MetricFactory.WithCallbacks[F] =
      new MetricFactory.WithCallbacks[F](metricRegistry, callbackRegistry, prefix, commonLabels) {}
  }

  object WithCallbacks {
    def noop[F[_]: Applicative]: WithCallbacks[F] =
      new WithCallbacks[F](MetricRegistry.noop, CallbackRegistry.noop, None, CommonLabels.empty) {}
  }

  /** Create an instance of [[MetricFactory]] that performs no operations
    */
  def noop[F[_]: Applicative]: MetricFactory[F] =
    new MetricFactory[F](
      MetricRegistry.noop,
      None,
      CommonLabels.empty
    ) {}

  /** Builder for [[MetricFactory]]
    */
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

    /** Build a [[MetricFactory]] from a [[MetricRegistry]] and separate [[CallbackRegistry]]
      *
      * @param metricRegistry
      *   [[MetricRegistry]] with which to register new metrics created by the built [[MetricFactory]]
      * @param callbackRegistry
      *   [[CallbackRegistry]] with which to register new metrics created by the built [[MetricFactory]]
      * @return
      *   a new [[MetricFactory.WithCallbacks]] instance
      */
    def build[F[_]](
        metricRegistry: MetricRegistry[F],
        callbackRegistry: CallbackRegistry[F]
    ): MetricFactory.WithCallbacks[F] =
      new MetricFactory.WithCallbacks[F](metricRegistry, callbackRegistry, prefix, commonLabels) {}

    /** Build a [[MetricFactory]] from a [[MetricRegistry with CallbackRegistry]]
      *
      * @param metricRegistry
      *   [[[MetricRegistry with CallbackRegistry]] with which to register new metrics and callbacks created by the
      *   built [[MetricFactory]]
      * @return
      *   a new [[MetricFactory.WithCallbacks]] instance
      */
    def build[F[_]](metricRegistry: MetricRegistry[F] with CallbackRegistry[F]): MetricFactory.WithCallbacks[F] =
      new MetricFactory.WithCallbacks[F](metricRegistry, metricRegistry, prefix, commonLabels) {}

    /** Build a [[MetricFactory]] from an existing [[MetricFactory]] and [[CallbackRegistry]]
      *
      * @param metricFactory
      *   [[MetricFactory]] from which to obtain a [[MetricRegistry]]
      * @param callbackRegistry
      *   [[CallbackRegistry]] with which to register new metrics created by the built [[MetricFactory]]
      * @return
      *   a new [[MetricFactory.WithCallbacks]] instance
      */
    def build[F[_]](
        metricFactory: MetricFactory[F],
        callbackRegistry: CallbackRegistry[F]
    ): MetricFactory.WithCallbacks[F] =
      new MetricFactory.WithCallbacks[F](metricFactory.metricRegistry, callbackRegistry, prefix, commonLabels) {}

    /** Build a [[MetricFactory]] the performs no operations
      *
      * @return
      *   a new [[MetricFactory]] instance that performs no operations
      */
    def noop[F[_]: Applicative]: MetricFactory.WithCallbacks[F] =
      MetricFactory.WithCallbacks.noop[F]
  }

  /** Construct a [[MetricFactory]] using [[MetricFactory.Builder]]
    */
  def builder = new Builder()
}
