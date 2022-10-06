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

package openmetrics4s

import cats.{Applicative, Monad, ~>}
import openmetrics4s.Metric.CommonLabels
import openmetrics4s.internal.HelpStep
import openmetrics4s.internal.counter.CounterDsl
import openmetrics4s.internal.gauge.GaugeDsl
import openmetrics4s.internal.histogram.BucketDsl

sealed abstract class MetricsFactory[F[_]](
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    commonLabels: CommonLabels
) {
  def mapK[G[_]: Monad: RecordAttempt](fk: F ~> G): MetricsFactory[G] =
    new MetricsFactory[G](
      MetricsRegistry.mapK(registry, fk),
      prefix,
      commonLabels
    ) {}

  /** Starts creating a "gauge" metric.
    *
    * @example
    *   {{{ metrics.gauge("my_gauge") .help("my gauge help") .label[Int]("first_label") .label[String]("second_label")
    *   .label[Boolean]("third_label") .build }}}
    * @param name
    *   [[Gauge.Name]] value
    * @return
    *   Gauge builder using [[openmetrics4s.internal.HelpStep]] and [[openmetrics4s.internal.gauge.GaugeDsl]]
    */
  def gauge(name: Gauge.Name): HelpStep[GaugeDsl[F]] = new HelpStep(
    new GaugeDsl[F](registry, prefix, name, _, commonLabels)
  )

  /** Starts creating a "counter" metric.
    *
    * @example
    *   {{{ metrics.counter("my_counter") .help("my counter help") .label[Int]("first_label")
    *   .label[String]("second_label") .label[Boolean]("third_label") .build }}}
    * @param name
    *   [[Counter.Name]] value
    * @return
    *   Counter builder using [[openmetrics4s.internal.HelpStep]] and [[openmetrics4s.internal.counter.CounterDsl]]
    */
  def counter(name: Counter.Name): HelpStep[CounterDsl[F]] =
    new HelpStep[CounterDsl[F]](
      new CounterDsl[F](registry, prefix, name, _, commonLabels)
    )

  /** Starts creating a "histogram" metric.
    *
    * @example
    *   {{{ metrics.histogram("my_histogram") .help("my counter help") .buckets(1.0, 2.0) .label[Int]("first_label")
    *   .label[String]("second_label") .label[Boolean]("third_label") .build }}}
    * @param name
    *   [[Histogram.Name]] value
    * @return
    *   Counter builder using [[openmetrics4s.internal.HelpStep]], [[openmetrics4s.internal.histogram.BucketDsl]] and
    *   [[openmetrics4s.internal.histogram.HistogramDsl]]
    */
  def histogram(name: Histogram.Name): HelpStep[BucketDsl[F]] =
    new HelpStep[BucketDsl[F]](
      new BucketDsl[F](registry, prefix, name, _, commonLabels)
    )

  /** Creates a new instance of [[MetricsFactory]] without a [[Metric.Prefix]] set
    */
  def dropPrefix: MetricsFactory[F] = new MetricsFactory[F](registry, None, commonLabels) {}

  /** Creates a new instance of [[MetricsFactory]] with the given [[Metric.Prefix]] set
    */
  def withPrefix(prefix: Metric.Prefix): MetricsFactory[F] =
    new MetricsFactory[F](registry, Some(prefix), commonLabels) {}
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
  class Builder private[openmetrics4s] (
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
