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

package prometheus4cats.util

import cats.data.NonEmptySeq
import cats.effect.kernel.Resource
import prometheus4cats._

import scala.concurrent.duration.FiniteDuration

trait DoubleMetricRegistry[F[_]] extends MetricRegistry[F] {
  override protected[prometheus4cats] def createAndRegisterLongCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Counter[F, Long]] =
    createAndRegisterDoubleCounter(prefix, name, help, commonLabels).map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLabelledLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleCounter(prefix, name, help, commonLabels, labelNames)(f).map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLongGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Gauge[F, Long]] =
    createAndRegisterDoubleGauge(prefix, name, help, commonLabels).map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLabelledLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleGauge(prefix, name, help, commonLabels, labelNames)(f).map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLongHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Long]
  ): Resource[F, Histogram[F, Long]] =
    createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets.map(_.toDouble))
      .map(_.contramap(_.toDouble))

  override protected[prometheus4cats] def createAndRegisterLabelledLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleHistogram(prefix, name, help, commonLabels, labelNames, buckets.map(_.toDouble))(f)
      .map(
        _.contramap(_.toDouble)
      )

  override protected[prometheus4cats] def createAndRegisterLongSummary(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  ): Resource[F, Summary[F, Long]] =
    createAndRegisterDoubleSummary(prefix, name, help, commonLabels, quantiles, maxAge, ageBuckets).map(
      _.contramap(_.toDouble)
    )

  override protected[prometheus4cats] def createAndRegisterLabelledLongSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[F, Summary.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleSummary(prefix, name, help, commonLabels, labelNames, quantiles, maxAge, ageBuckets)(
      f
    ).map(_.contramap(_.toDouble))
}
