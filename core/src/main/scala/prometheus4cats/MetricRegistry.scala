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
import cats.syntax.functor._
import cats.{Applicative, Functor, ~>}
import prometheus4cats.Metric.CommonLabels

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
  protected[prometheus4cats] def createAndRegisterDoubleCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Counter[F, Double]]

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
  protected[prometheus4cats] def createAndRegisterLongCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Counter[F, Long]]

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
  protected[prometheus4cats] def createAndRegisterLabelledDoubleCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, Double, A]]

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
  protected[prometheus4cats] def createAndRegisterLabelledLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, Long, A]]

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
  protected[prometheus4cats] def createAndRegisterDoubleGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Gauge[F, Double]]

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
  protected[prometheus4cats] def createAndRegisterLongGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Gauge[F, Long]]

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
  protected[prometheus4cats] def createAndRegisterLabelledDoubleGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, Double, A]]

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
  protected[prometheus4cats] def createAndRegisterLabelledLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, Long, A]]

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
    *   a [[Gauge]] wrapped in whatever side effect that was performed in registering it
    */
  protected[prometheus4cats] def createAndRegisterDoubleHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double]
  ): F[Histogram[F, Double]]

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
    *   a [[Gauge]] wrapped in whatever side effect that was performed in registering it
    */
  protected[prometheus4cats] def createAndRegisterLongHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Long]
  ): F[Histogram[F, Long]]

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
  protected[prometheus4cats] def createAndRegisterLabelledDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, Double, A]]

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
  protected[prometheus4cats] def createAndRegisterLabelledLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, Long, A]]

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
  protected[prometheus4cats] def createAndRegisterInfo(
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help
  ): F[Info[F, Map[Label.Name, String]]]

  final def mapK[G[_]: Functor](fk: F ~> G): MetricRegistry[G] = MetricRegistry.mapK(this, fk)
}

object MetricRegistry {

  def noop[F[_]](implicit F: Applicative[F]): MetricRegistry[F] =
    new MetricRegistry[F] {
      override protected[prometheus4cats] def createAndRegisterDoubleCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): F[Counter[F, Double]] = F.pure(Counter.noop)

      override protected[prometheus4cats] def createAndRegisterLabelledDoubleCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, Double, A]] =
        F.pure(Counter.Labelled.noop)

      override protected[prometheus4cats] def createAndRegisterDoubleGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): F[Gauge[F, Double]] =
        F.pure(Gauge.noop)

      override protected[prometheus4cats] def createAndRegisterLongGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): F[Gauge[F, Long]] =
        F.pure(Gauge.noop)

      override protected[prometheus4cats] def createAndRegisterLabelledDoubleGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, Double, A]] =
        F.pure(Gauge.Labelled.noop)

      override protected[prometheus4cats] def createAndRegisterDoubleHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Double]
      ): F[Histogram[F, Double]] = F.pure(Histogram.noop)

      override protected[prometheus4cats] def createAndRegisterLabelledDoubleHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, Double, A]] =
        F.pure(Histogram.Labelled.noop)

      override protected[prometheus4cats] def createAndRegisterLongCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): F[Counter[F, Long]] = F.pure(Counter.noop)

      override protected[prometheus4cats] def createAndRegisterLabelledLongCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, Long, A]] =
        F.pure(Counter.Labelled.noop)

      override protected[prometheus4cats] def createAndRegisterLabelledLongGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, Long, A]] =
        F.pure(Gauge.Labelled.noop)

      override protected[prometheus4cats] def createAndRegisterLongHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Long]
      ): F[Histogram[F, Long]] = F.pure(Histogram.noop)

      override protected[prometheus4cats] def createAndRegisterLabelledLongHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Long]
      )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, Long, A]] = F.pure(Histogram.Labelled.noop)

      override protected[prometheus4cats] def createAndRegisterInfo(
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help
      ): F[Info[F, Map[Label.Name, String]]] = F.pure(Info.noop)
    }

  private[prometheus4cats] def mapK[F[_], G[_]: Functor](
      self: MetricRegistry[F],
      fk: F ~> G
  ): MetricRegistry[G] =
    new MetricRegistry[G] {
      override protected[prometheus4cats] def createAndRegisterDoubleCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): G[Counter[G, Double]] = fk(
        self.createAndRegisterDoubleCounter(prefix, name, help, commonLabels)
      ).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLabelledDoubleCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): G[Counter.Labelled[G, Double, A]] = fk(
        self.createAndRegisterLabelledDoubleCounter(
          prefix,
          name,
          help,
          commonLabels,
          labelNames
        )(f)
      ).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterDoubleGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): G[Gauge[G, Double]] = fk(
        self.createAndRegisterDoubleGauge(prefix, name, help, commonLabels)
      ).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLongGauge(
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): G[Gauge[G, Long]] = fk(
        self.createAndRegisterLongGauge(prefix, name, help, commonLabels)
      ).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLabelledDoubleGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): G[Gauge.Labelled[G, Double, A]] = fk(
        self.createAndRegisterLabelledDoubleGauge(
          prefix,
          name,
          help,
          commonLabels,
          labelNames
        )(f)
      ).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterDoubleHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Double]
      ): G[Histogram[G, Double]] = fk(
        self.createAndRegisterDoubleHistogram(
          prefix,
          name,
          help,
          commonLabels,
          buckets
        )
      ).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLabelledDoubleHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Double]
      )(f: A => IndexedSeq[String]): G[Histogram.Labelled[G, Double, A]] = fk(
        self.createAndRegisterLabelledDoubleHistogram(
          prefix,
          name,
          help,
          commonLabels,
          labelNames,
          buckets
        )(f)
      ).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLongCounter(
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels
      ): G[Counter[G, Long]] = fk(self.createAndRegisterLongCounter(prefix, name, help, commonLabels)).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLabelledLongCounter[A](
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): G[Counter.Labelled[G, Long, A]] =
        fk(self.createAndRegisterLabelledLongCounter(prefix, name, help, commonLabels, labelNames)(f)).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLabelledLongGauge[A](
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name]
      )(f: A => IndexedSeq[String]): G[Gauge.Labelled[G, Long, A]] =
        fk(self.createAndRegisterLabelledLongGauge(prefix, name, help, commonLabels, labelNames)(f)).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLongHistogram(
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          buckets: NonEmptySeq[Long]
      ): G[Histogram[G, Long]] =
        fk(self.createAndRegisterLongHistogram(prefix, name, help, commonLabels, buckets)).map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterLabelledLongHistogram[A](
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: CommonLabels,
          labelNames: IndexedSeq[Label.Name],
          buckets: NonEmptySeq[Long]
      )(f: A => IndexedSeq[String]): G[Histogram.Labelled[G, Long, A]] =
        fk(self.createAndRegisterLabelledLongHistogram(prefix, name, help, commonLabels, labelNames, buckets)(f))
          .map(_.mapK(fk))

      override protected[prometheus4cats] def createAndRegisterInfo(
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help
      ): G[Info[G, Map[Label.Name, String]]] = fk(self.createAndRegisterInfo(prefix, name, help)).map(_.mapK(fk))
    }
}
