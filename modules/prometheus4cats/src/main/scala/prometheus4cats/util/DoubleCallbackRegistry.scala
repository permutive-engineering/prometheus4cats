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

package prometheus4cats.util

import cats.Functor
import cats.data.NonEmptyList
import cats.data.NonEmptySeq
import cats.effect.kernel.Resource
import cats.syntax.functor._

import prometheus4cats._

trait DoubleCallbackRegistry[F[_]] extends CallbackRegistry[F] {

  implicit protected val F: Functor[F]

  override def registerLongCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Long, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = registerDoubleCounterCallback(
    prefix,
    name,
    help,
    commonLabels,
    labelNames,
    callback.map(_.map { case (v, a) => v.toDouble -> a })
  )(f)

  override def registerLongGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Long, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = registerDoubleGaugeCallback(
    prefix,
    name,
    help,
    commonLabels,
    labelNames,
    callback.map(_.map { case (v, a) => v.toDouble -> a })
  )(f)

  override def registerLongHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long],
      callback: F[NonEmptyList[(Histogram.Value[Long], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = registerDoubleHistogramCallback(
    prefix,
    name,
    help,
    commonLabels,
    labelNames,
    buckets.map(_.toDouble),
    callback.map(_.map { case (v, a) => v.map(_.toDouble) -> a })
  )(f)

  override def registerLongSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Summary.Value[Long], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = registerDoubleSummaryCallback(
    prefix,
    name,
    help,
    commonLabels,
    labelNames,
    callback.map(_.map { case (v, a) => v.map(_.toDouble) -> a })
  )(f)

}
