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

package com.permutive.metrics

import cats.data.NonEmptySeq
import cats.syntax.functor._
import cats.{Applicative, Monad, ~>}
import com.permutive.metrics.Metric.CommonLabels

/** Trait for registering metrics against different backends. May be implemented by anyone for use with
  * [[MetricsFactory]]
  */
trait MetricsRegistry[F[_]] {

  /** Create and register a counter against a metrics registry
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
  def createAndRegisterCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Counter[F]]

  /** Create and register a labelled counter against a metrics registry
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
  def createAndRegisterLabelledCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, A]]

  /** Create and register a gauge against a metrics registry
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
  def createAndRegisterGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Gauge[F]]

  /** Create and register a labelled gauge against a metrics registry
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
  def createAndRegisterLabelledGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, A]]

  /** Create and register a histogram against a metrics registry
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
    *   a [[Gauge]] wrapped in whatever side effect that was performed in registering it
    */
  def createAndRegisterHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double]
  ): F[Histogram[F]]

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
  def createAndRegisterLabelledHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, A]]
}

object MetricsRegistry {
  def noop[F[_]](implicit F: Applicative[F]): MetricsRegistry[F] =
    new MetricsRegistry[F] {
      override def createAndRegisterCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): F[Counter[F]] = F.pure(Counter.noop)

      override def createAndRegisterLabelledCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, A]] =
        F.pure(Counter.Labelled.noop)

      override def createAndRegisterGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): F[Gauge[F]] =
        F.pure(Gauge.noop)

      override def createAndRegisterLabelledGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, A]] =
        F.pure(Gauge.Labelled.noop)

      override def createAndRegisterHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Double]
      ): F[Histogram[F]] = F.pure(Histogram.noop)

      override def createAndRegisterLabelledHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, A]] =
        F.pure(Histogram.Labelled.noop)
    }

  private[metrics] def mapK[F[_], G[_]: Monad: RecordAttempt](
      self: MetricsRegistry[F],
      fk: F ~> G
  ): MetricsRegistry[G] =
    new MetricsRegistry[G] {
      override def createAndRegisterCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): G[Counter[G]] = fk(
        self.createAndRegisterCounter(prefix, name, help, commonLabels)
      ).map(_.mapK(fk))

      override def createAndRegisterLabelledCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): G[Counter.Labelled[G, A]] = fk(
        self.createAndRegisterLabelledCounter(
          prefix,
          name,
          help,
          commonLabels,
          labelNames
        )(f)
      ).map(_.mapK(fk))

      override def createAndRegisterGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): G[Gauge[G]] = fk(
        self.createAndRegisterGauge(prefix, name, help, commonLabels)
      ).map(_.mapK(fk))

      override def createAndRegisterLabelledGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): G[Gauge.Labelled[G, A]] = fk(
        self.createAndRegisterLabelledGauge(
          prefix,
          name,
          help,
          commonLabels,
          labelNames
        )(f)
      ).map(_.mapK(fk))

      override def createAndRegisterHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Double]
      ): G[Histogram[G]] = fk(
        self.createAndRegisterHistogram(
          prefix,
          name,
          help,
          commonLabels,
          buckets
        )
      ).map(_.mapK(fk))

      override def createAndRegisterLabelledHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): G[Histogram.Labelled[G, A]] = fk(
        self.createAndRegisterLabelledHistogram(
          prefix,
          name,
          help,
          commonLabels,
          labelNames,
          buckets
        )(f)
      ).map(_.mapK(fk))
    }
}
