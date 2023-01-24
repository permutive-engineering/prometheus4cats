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

import cats.Functor
import cats.data.{NonEmptyList, NonEmptySeq}
import cats.effect.kernel.Resource
import cats.syntax.functor._
import prometheus4cats._

trait DoubleCallbackRegistry[F[_]] extends CallbackRegistry[F] {
  implicit protected val F: Functor[F]

  override def registerLongCounterCallback(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Long]
  ): Resource[F, Unit] = registerDoubleCounterCallback(prefix, name, help, commonLabels, callback.map(_.toDouble))

  override def registerLabelledLongCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Long, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = registerLabelledDoubleCounterCallback(
    prefix,
    name,
    help,
    commonLabels,
    labelNames,
    callback.map(_.map { case (v, a) => v.toDouble -> a })
  )(f)

  override def registerLongGaugeCallback(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Long]
  ): Resource[F, Unit] = registerDoubleGaugeCallback(prefix, name, help, commonLabels, callback.map(_.toDouble))

  override def registerLabelledLongGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Long, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = registerLabelledDoubleGaugeCallback(
    prefix,
    name,
    help,
    commonLabels,
    labelNames,
    callback.map(_.map { case (v, a) => v.toDouble -> a })
  )(f)

  override def registerLongHistogramCallback(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Long],
      callback: F[Histogram.Value[Long]]
  ): Resource[F, Unit] = registerDoubleHistogramCallback(
    prefix,
    name,
    help,
    commonLabels,
    buckets.map(_.toDouble),
    callback.map(_.map(_.toDouble))
  )

  override def registerLabelledLongHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long],
      callback: F[NonEmptyList[(Histogram.Value[Long], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = registerLabelledDoubleHistogramCallback(
    prefix,
    name,
    help,
    commonLabels,
    labelNames,
    buckets.map(_.toDouble),
    callback.map(_.map { case (v, a) => v.map(_.toDouble) -> a })
  )(f)

  override def registerLongSummaryCallback(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Summary.Value[Long]]
  ): Resource[F, Unit] = registerDoubleSummaryCallback(
    prefix,
    name,
    help,
    commonLabels,
    callback.map(_.map(_.toDouble))
  )

  override def registerLabelledLongSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Summary.Value[Long], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = registerLabelledDoubleSummaryCallback(
    prefix,
    name,
    help,
    commonLabels,
    labelNames,
    callback.map(_.map { case (v, a) => v.map(_.toDouble) -> a })
  )(f)
}
