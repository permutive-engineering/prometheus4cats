/*
 * Copyright 2022-2025 Permutive Ltd. <https://permutive.com>
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

package test

import scala.concurrent.duration.FiniteDuration

import cats.data.NonEmptyList
import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.kernel.Resource

import prometheus4cats._

// Functions as a compile-time test to ensure methods are implementable outside of `prometheus4cats` package scope
class ExternalPackageMetricRegistry extends MetricRegistry[IO] with CallbackRegistry[IO] {

  override def createAndRegisterDoubleCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[IO, Counter[IO, Double, A]] = ???

  override def createAndRegisterLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[IO, Counter[IO, Long, A]] = ???

  override def createAndRegisterDoubleGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[IO, Gauge[IO, Double, A]] = ???

  override def createAndRegisterLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[IO, Gauge[IO, Long, A]] = ???

  override def createAndRegisterDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): Resource[IO, Histogram[IO, Double, A]] = ???

  override def createAndRegisterLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): Resource[IO, Histogram[IO, Long, A]] = ???

  override def createAndRegisterDoubleSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[IO, Summary[IO, Double, A]] = ???

  override def createAndRegisterLongSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[IO, Summary[IO, Long, A]] = ???

  override def createAndRegisterInfo(
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help
  ): Resource[IO, Info[IO, Map[Label.Name, String]]] = ???

  override def registerDoubleCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: IO[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[IO, Unit] = ???

  override def registerLongCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: IO[NonEmptyList[(Long, A)]]
  )(f: A => IndexedSeq[String]): Resource[IO, Unit] = ???

  override def registerDoubleGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: IO[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[IO, Unit] = ???

  override def registerLongGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: IO[NonEmptyList[(Long, A)]]
  )(f: A => IndexedSeq[String]): Resource[IO, Unit] = ???

  override def registerDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      callback: IO[NonEmptyList[(Histogram.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[IO, Unit] = ???

  override def registerLongHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long],
      callback: IO[NonEmptyList[(Histogram.Value[Long], A)]]
  )(f: A => IndexedSeq[String]): Resource[IO, Unit] = ???

  override def registerDoubleSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: IO[NonEmptyList[(Summary.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[IO, Unit] = ???

  override def registerLongSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: IO[NonEmptyList[(Summary.Value[Long], A)]]
  )(f: A => IndexedSeq[String]): Resource[IO, Unit] = ???

  override def registerMetricCollectionCallback(
      prefix: Option[Metric.Prefix],
      commonLabels: Metric.CommonLabels,
      callback: IO[MetricCollection]
  ): Resource[IO, Unit] = ???

}
