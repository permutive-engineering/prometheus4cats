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

package com.permutive.metrics.internal.histogram

import com.permutive.metrics._
import cats.data.NonEmptySeq

final class BucketDsl[F[_]] private[metrics] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    metric: Histogram.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels
) {

  /** Initialise this histogram with the default HTTP buckets list.
    *
    * @see
    *   [[com.permutive.metrics.Histogram.DefaultHttpBuckets]]
    */
  def defaultHttpBuckets: HistogramDsl[F] = buckets(
    Histogram.DefaultHttpBuckets
  )

  /** Provides the list of buckets for the histogram as a non-empty sequence.
    */
  def buckets(list: NonEmptySeq[Double]): HistogramDsl[F] =
    new HistogramDsl(registry, prefix, metric, help, commonLabels, list)

  /** Provides the list of buckets for the histogram as parameters.
    */
  def buckets(head: Double, rest: Double*): HistogramDsl[F] = buckets(
    NonEmptySeq.of(head, rest: _*)
  )
}
