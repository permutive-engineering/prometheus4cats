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

import scala.concurrent.duration.FiniteDuration

import cats.data.NonEmptySeq
import cats.effect.kernel.Resource

import prometheus4cats._

trait DoubleMetricRegistry[F[_]] extends MetricRegistry[F] {

  override def createAndRegisterLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter[F, Long, A]] =
    createAndRegisterDoubleCounter(prefix, name, help, commonLabels, labelNames)(f).map(_.contramap(_.toDouble))

  override def createAndRegisterLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge[F, Long, A]] =
    createAndRegisterDoubleGauge(prefix, name, help, commonLabels, labelNames)(f).map(_.contramap(_.toDouble))

  override def createAndRegisterLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Long, A]] =
    createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, labelNames, buckets.map(_.toDouble))(f)
      .map(
        _.contramap(_.toDouble)
      )

  override def createAndRegisterLongSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[F, Summary[F, Long, A]] =
    createAndRegisterDoubleSummary(prefix, name, help, commonLabels, labelNames, quantiles, maxAge, ageBuckets)(
      f
    ).map(_.contramap(_.toDouble))

}
