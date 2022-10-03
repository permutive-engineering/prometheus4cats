package com.permutive.metrics.internal.histogram

import com.permutive.metrics._
import cats.data.NonEmptySeq

final class BucketDsl[F[_]] private[metrics] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
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
    new HistogramDsl(registry, prefix, suffix, metric, help, commonLabels, list)

  /** Provides the list of buckets for the histogram as parameters.
    */
  def buckets(head: Double, rest: Double*): HistogramDsl[F] = buckets(
    NonEmptySeq.of(head, rest: _*)
  )
}
