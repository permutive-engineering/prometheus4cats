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
import prometheus4cats.internal.{HelpStep, LabelledMetricPartiallyApplied, MetricDsl, TypeStep}

sealed abstract class MetricsFactory[F[_]](
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    commonLabels: CommonLabels
) {
  def mapK[G[_]: Functor](fk: F ~> G): MetricsFactory[G] =
    new MetricsFactory[G](
      MetricsRegistry.mapK(registry, fk),
      prefix,
      commonLabels
    ) {}

  type GaugeDsl[A] = HelpStep[MetricDsl[F, A, Gauge, Gauge.Labelled]]

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
  def gauge(name: Gauge.Name): TypeStep[GaugeDsl] =
    new TypeStep[GaugeDsl](
      new HelpStep(help =>
        new MetricDsl(
          registry.createAndRegisterLongGauge(prefix, name, help, commonLabels),
          new LabelledMetricPartiallyApplied[F, Long, Gauge.Labelled] {
            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): F[Gauge.Labelled[F, Long, B]] =
              registry.createAndRegisterLabelledLongGauge(prefix, name, help, commonLabels, labels)(f)
          }
        )
      ),
      new HelpStep(help =>
        new MetricDsl(
          registry.createAndRegisterDoubleGauge(prefix, name, help, commonLabels),
          new LabelledMetricPartiallyApplied[F, Double, Gauge.Labelled] {
            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): F[Gauge.Labelled[F, Double, B]] =
              registry.createAndRegisterLabelledDoubleGauge(prefix, name, help, commonLabels, labels)(f)
          }
        )
      )
    )

  type CounterDsl[A] = HelpStep[MetricDsl[F, A, Counter, Counter.Labelled]]

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
  def counter(name: Counter.Name): TypeStep[CounterDsl] =
    new TypeStep[CounterDsl](
      new HelpStep(help =>
        new MetricDsl(
          registry.createAndRegisterLongCounter(prefix, name, help, commonLabels),
          new LabelledMetricPartiallyApplied[F, Long, Counter.Labelled] {
            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): F[Counter.Labelled[F, Long, B]] =
              registry.createAndRegisterLabelledLongCounter(prefix, name, help, commonLabels, labels)(f)
          }
        )
      ),
      new HelpStep(help =>
        new MetricDsl(
          registry.createAndRegisterDoubleCounter(prefix, name, help, commonLabels),
          new LabelledMetricPartiallyApplied[F, Double, Counter.Labelled] {
            override def apply[B](
                labels: IndexedSeq[Label.Name]
            )(f: B => IndexedSeq[String]): F[Counter.Labelled[F, Double, B]] =
              registry.createAndRegisterLabelledDoubleCounter(prefix, name, help, commonLabels, labels)(f)
          }
        )
      )
    )

  type HistogramDsl[A] = HelpStep[BucketDsl[MetricDsl[F, A, Histogram, Histogram.Labelled], A]]

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
  def histogram(name: Histogram.Name): TypeStep[HistogramDsl] =
    new TypeStep[HistogramDsl](
      new HelpStep(help =>
        new BucketDsl[MetricDsl[F, Long, Histogram, Histogram.Labelled], Long](buckets =>
          new MetricDsl(
            registry.createAndRegisterLongHistogram(prefix, name, help, commonLabels, buckets),
            new LabelledMetricPartiallyApplied[F, Long, Histogram.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Histogram.Labelled[F, Long, B]] =
                registry.createAndRegisterLabelledLongHistogram(prefix, name, help, commonLabels, labels, buckets)(f)
            }
          )
        )
      ),
      new HelpStep(help =>
        new BucketDsl[MetricDsl[F, Double, Histogram, Histogram.Labelled], Double](buckets =>
          new MetricDsl(
            registry.createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets),
            new LabelledMetricPartiallyApplied[F, Double, Histogram.Labelled] {
              override def apply[B](
                  labels: IndexedSeq[Label.Name]
              )(f: B => IndexedSeq[String]): F[Histogram.Labelled[F, Double, B]] =
                registry.createAndRegisterLabelledDoubleHistogram(prefix, name, help, commonLabels, labels, buckets)(f)
            }
          )
        )
      )
    )

  /** Creates a new instance of [[MetricsFactory]] without a [[Metric.Prefix]] set
    */
  def dropPrefix: MetricsFactory[F] = new MetricsFactory[F](registry, None, commonLabels) {}

  /** Creates a new instance of [[MetricsFactory]] with the given [[Metric.Prefix]] set
    */
  def withPrefix(prefix: Metric.Prefix): MetricsFactory[F] =
    new MetricsFactory[F](registry, Some(prefix), commonLabels) {}

  /** Creates a new instance of [[MetricsFactory]] with any [[Metric.CommonLabels]]
    */
  def dropCommonLabels: MetricsFactory[F] = new MetricsFactory[F](registry, prefix, CommonLabels.empty) {}

  /** Creates a new instance of [[MetricsFactory]] with the provided [[Metric.CommonLabels]]
    */
  def withCommonLabels(commonLabels: CommonLabels): MetricsFactory[F] =
    new MetricsFactory[F](registry, prefix, commonLabels) {}
}

object MetricsFactory {

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
      * @param registry
      *   [[MetricsRegistry]] with which to register new metrics created by the built [[MetricsFactory]]
      * @return
      *   a new [[MetricsFactory]] instance
      */
    def build[F[_]](registry: MetricsRegistry[F]): MetricsFactory[F] =
      new MetricsFactory[F](registry, prefix, commonLabels) {}

    /** Build a [[MetricsFactory]] the performs no operations
      *
      * @return
      *   a new [[MetricsFactory]] instance that performs no operations
      */
    def noop[F[_]: Applicative]: MetricsFactory[F] =
      MetricsFactory.noop[F]
  }

  /** Construct a [[MetricsFactory]] using [[MetricsFactory.Builder]]
    */
  def builder = new Builder()
}
