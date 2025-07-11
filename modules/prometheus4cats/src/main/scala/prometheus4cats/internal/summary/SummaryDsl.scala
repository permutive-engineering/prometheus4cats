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

package prometheus4cats.internal.summary

import scala.concurrent.duration._

import cats.Functor

import prometheus4cats.Summary.QuantileDefinition
import prometheus4cats._
import prometheus4cats.internal._

final class SummaryDsl[F[_], A] private[prometheus4cats] (
    quantiles: Seq[QuantileDefinition] = SummaryDsl.defaultQuantiles,
    maxAgeValue: FiniteDuration = SummaryDsl.defaultMaxAge,
    ageBucketsValue: Summary.AgeBuckets = Summary.AgeBuckets.Default,
    makeSummary: (
        Seq[QuantileDefinition],
        FiniteDuration,
        Summary.AgeBuckets
    ) => LabelledMetricPartiallyApplied[F, A, Summary]
) extends MetricDsl[F, A, Summary](makeSummary(quantiles, maxAgeValue, ageBucketsValue))
    with SummaryDsl.Base[F, A] {

  override def quantile(quantile: Summary.Quantile, error: Summary.AllowedError): SummaryDsl[F, A] =
    new SummaryDsl[F, A](
      quantiles :+ QuantileDefinition(quantile, error),
      maxAgeValue,
      ageBucketsValue,
      makeSummary
    )

  override def maxAge(age: FiniteDuration): AgeBucketsStep[F, A] =
    new AgeBucketsStep[F, A](quantiles, age, ageBucketsValue, makeSummary)

}

object SummaryDsl {

  trait Base[F[_], A] extends BuildStep[F, Summary[F, A, Unit]] {
    self: MetricDsl[F, A, Summary] =>

    def quantile(quantile: Summary.Quantile, error: Summary.AllowedError): SummaryDsl[F, A]

    def maxAge(age: FiniteDuration): AgeBucketsStep[F, A]

    def label[B]: FirstLabelApply[F, A, B, Summary]

    def unsafeLabels(
        labelNames: IndexedSeq[Label.Name]
    ): BuildStep[F, Summary[F, A, Map[Label.Name, String]]]

    def unsafeLabels(
        labelNames: Label.Name*
    ): BuildStep[F, Summary[F, A, Map[Label.Name, String]]]

    def labels[B](labels: (Label.Name, B => Label.Value)*): LabelledMetricDsl[F, A, B, Summary]

    def labelsFrom[B](implicit encoder: Label.Encoder[B]): LabelledMetricDsl[F, A, B, Summary]

  }

  private val defaultQuantiles: Seq[Summary.QuantileDefinition] = Seq.empty

  private val defaultMaxAge: FiniteDuration = 10.minutes

  final class WithCallbacks[F[_]: Functor, A, A0](
      quantiles: Seq[QuantileDefinition] = SummaryDsl.defaultQuantiles,
      maxAgeValue: FiniteDuration = SummaryDsl.defaultMaxAge,
      ageBucketsValue: Summary.AgeBuckets = Summary.AgeBuckets.Default,
      makeSummary: (
          Seq[QuantileDefinition],
          FiniteDuration,
          Summary.AgeBuckets
      ) => LabelledMetricPartiallyApplied[F, A, Summary],
      makeSummaryCallback: LabelledCallbackPartiallyApplied[F, A0]
  ) extends MetricDsl.WithCallbacks[F, A, A0, Summary](
        makeSummary(quantiles, maxAgeValue, ageBucketsValue),
        makeSummaryCallback
      )
      with Base[F, A] {

    override def quantile(quantile: Summary.Quantile, error: Summary.AllowedError): SummaryDsl[F, A] =
      new SummaryDsl[F, A](
        quantiles :+ QuantileDefinition(quantile, error),
        maxAgeValue,
        ageBucketsValue,
        makeSummary
      )

    override def maxAge(age: FiniteDuration): AgeBucketsStep[F, A] =
      new AgeBucketsStep[F, A](quantiles, age, ageBucketsValue, makeSummary)

  }

}

final class AgeBucketsStep[F[_], A] private[summary] (
    quantiles: Seq[QuantileDefinition],
    maxAgeValue: FiniteDuration,
    ageBucketsValue: Summary.AgeBuckets,
    makeSummary: (
        Seq[QuantileDefinition],
        FiniteDuration,
        Summary.AgeBuckets
    ) => LabelledMetricPartiallyApplied[F, A, Summary]
) extends MetricDsl[F, A, Summary](
      makeSummary(quantiles, maxAgeValue, ageBucketsValue)
    ) {

  def ageBuckets(buckets: Summary.AgeBuckets): MetricDsl[F, A, Summary] =
    new MetricDsl[F, A, Summary](
      makeSummary(quantiles, maxAgeValue, buckets)
    )

}
