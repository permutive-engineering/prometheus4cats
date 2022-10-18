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
import prometheus4cats.internal.histogram.BucketDsl
import prometheus4cats.internal.{
  HelpStep,
  LabelledCallbackPartiallyApplied,
  LabelledMetricPartiallyApplied,
  MetricDsl,
  TypeStep
}

sealed abstract class MetricsFactory[F[_]](
    val metricRegistry: MetricsRegistry[F],
    val prefix: Option[Metric.Prefix],
    val commonLabels: CommonLabels
) {
  def mapK[G[_]: Functor](fk: F ~> G): MetricsFactory[G] =
    new MetricsFactory[G](
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

  /** Creates a new instance of [[MetricsFactory]] without a [[Metric.Prefix]] set
    */
  def dropPrefix: MetricsFactory[F] = new MetricsFactory[F](metricRegistry, None, commonLabels) {}

  /** Creates a new instance of [[MetricsFactory]] with the given [[Metric.Prefix]] set
    */
  def withPrefix(prefix: Metric.Prefix): MetricsFactory[F] =
    new MetricsFactory[F](metricRegistry, Some(prefix), commonLabels) {}

  /** Creates a new instance of [[MetricsFactory]] with any [[Metric.CommonLabels]]
    */
  def dropCommonLabels: MetricsFactory[F] = new MetricsFactory[F](metricRegistry, prefix, CommonLabels.empty) {}

  /** Creates a new instance of [[MetricsFactory]] with the provided [[Metric.CommonLabels]]
    */
  def withCommonLabels(commonLabels: CommonLabels): MetricsFactory[F] =
    new MetricsFactory[F](metricRegistry, prefix, commonLabels) {}
}

object MetricsFactory {

  sealed abstract class WithCallbacks[F[_]](
      metricRegistry: MetricsRegistry[F],
      val callbackRegistry: CallbackRegistry[F],
      prefix: Option[Metric.Prefix],
      commonLabels: CommonLabels
  ) extends MetricsFactory[F](metricRegistry, prefix, commonLabels) {

    def imapK[G[_]: Functor](fk: F ~> G, gk: G ~> F): WithCallbacks[G] = new WithCallbacks[G](
      metricRegistry.mapK(fk),
      callbackRegistry.imapK(fk, gk),
      prefix,
      commonLabels
    ) {}

    type SimpleCallbackDsl[G[_], A, M[_[_], _], L[_[_], _, _]] = MetricDsl.WithCallbacks[G, A, A, M, L]

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

    /** Creates a new instance of [[MetricsFactory]] without a [[Metric.Prefix]] set
      */
    override def dropPrefix: MetricsFactory[F] =
      new MetricsFactory.WithCallbacks[F](metricRegistry, callbackRegistry, None, commonLabels) {}

    /** Creates a new instance of [[MetricsFactory]] with the given [[Metric.Prefix]] set
      */
    override def withPrefix(prefix: Metric.Prefix): MetricsFactory[F] =
      new MetricsFactory.WithCallbacks[F](metricRegistry, callbackRegistry, Some(prefix), commonLabels) {}

    /** Creates a new instance of [[MetricsFactory]] with any [[Metric.CommonLabels]]
      */
    override def dropCommonLabels: MetricsFactory[F] =
      new MetricsFactory.WithCallbacks[F](metricRegistry, callbackRegistry, prefix, CommonLabels.empty) {}

    /** Creates a new instance of [[MetricsFactory]] with the provided [[Metric.CommonLabels]]
      */
    override def withCommonLabels(commonLabels: CommonLabels): MetricsFactory[F] =
      new MetricsFactory.WithCallbacks[F](metricRegistry, callbackRegistry, prefix, commonLabels) {}
  }

  object WithCallbacks {
    def noop[F[_]: Applicative]: WithCallbacks[F] =
      new WithCallbacks[F](MetricsRegistry.noop, CallbackRegistry.noop, None, CommonLabels.empty) {}
  }

  /** Create an instance of [[MetricsFactory]] that performs no operations
    */
  def noop[F[_]: Applicative]: MetricsFactory[F] =
    new MetricsFactory[F](
      MetricsRegistry.noop,
      None,
      CommonLabels.empty
    ) {}

  /** Builder for [[MetricsFactory]]
    */
  class Builder private[prometheus4cats] (
      prefix: Option[Metric.Prefix] = None,
      commonLabels: CommonLabels = CommonLabels.empty
  ) {

    /** Add a prefix to all metrics created by the [[MetricsFactory]]
      * @param prefix
      *   [[Metric.Prefix]]
      */
    def withPrefix(prefix: Metric.Prefix): Builder =
      new Builder(Some(prefix), commonLabels)

    /** Add the given labels to all metrics created by the [[MetricsFactory]]
      *
      * @param labels
      *   [[Metric.CommonLabels]]
      */
    def withCommonLabels(labels: CommonLabels): Builder =
      new Builder(prefix, labels)

    /** Build a [[MetricsFactory]] from a [[MetricsRegistry]]
      *
      * @param metricRegistry
      *   [[MetricsRegistry]] with which to register new metrics created by the built [[MetricsFactory]]
      * @return
      *   a new [[MetricsFactory]] instance
      */
    def build[F[_]](metricRegistry: MetricsRegistry[F]): MetricsFactory[F] =
      new MetricsFactory[F](metricRegistry, prefix, commonLabels) {}

    /** Build a [[MetricsFactory]] from a [[MetricsRegistry]]
      *
      * @param metricRegistry
      *   [[MetricsRegistry]] with which to register new metrics created by the built [[MetricsFactory]]
      * @return
      *   a new [[MetricsFactory]] instance
      */
    def build[F[_]](
        metricRegistry: MetricsRegistry[F],
        callbacks: CallbackRegistry[F]
    ): MetricsFactory.WithCallbacks[F] =
      new MetricsFactory.WithCallbacks[F](metricRegistry, callbacks, prefix, commonLabels) {}

    /** Build a [[MetricsFactory]] from a [[MetricsRegistry]]
      *
      * @param metricRegistry
      *   [[MetricsRegistry]] with which to register new metrics created by the built [[MetricsFactory]]
      * @return
      *   a new [[MetricsFactory]] instance
      */
    def build[F[_]](metricRegistry: MetricsRegistry[F] with CallbackRegistry[F]): MetricsFactory.WithCallbacks[F] =
      new MetricsFactory.WithCallbacks[F](metricRegistry, metricRegistry, prefix, commonLabels) {}

    /** Build a [[MetricsFactory]] the performs no operations
      *
      * @return
      *   a new [[MetricsFactory]] instance that performs no operations
      */
    def noop[F[_]: Applicative]: MetricsFactory.WithCallbacks[F] =
      MetricsFactory.WithCallbacks.noop[F]
  }

  /** Construct a [[MetricsFactory]] using [[MetricsFactory.Builder]]
    */
  def builder = new Builder()
}
