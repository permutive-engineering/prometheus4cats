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

package prometheus4cats.internal.summary

import prometheus4cats.Summary.QuantileDefinition
import prometheus4cats._
import prometheus4cats.internal._

import scala.concurrent.duration._

class SummaryDsl[F[_], A] private[prometheus4cats] (
    quantiles: Seq[QuantileDefinition] = SummaryDsl.defaultQuantiles,
    maxAgeValue: FiniteDuration = SummaryDsl.defaultMaxAge,
    ageBucketsValue: Summary.AgeBuckets = Summary.AgeBuckets.Default,
    makeSummary: (Seq[QuantileDefinition], FiniteDuration, Summary.AgeBuckets) => F[Summary[F, A]],
    makeLabelledSummary: (
        Seq[QuantileDefinition],
        FiniteDuration,
        Summary.AgeBuckets
    ) => LabelledMetricPartiallyApplied[F, A, Summary.Labelled]
) extends MetricDsl[F, A, Summary, Summary.Labelled](
      makeSummary(quantiles, maxAgeValue, ageBucketsValue),
      makeLabelledSummary(quantiles, maxAgeValue, ageBucketsValue)
    )
    with SummaryDsl.Base[F, A] {
  override def quantile(quantile: Summary.Quantile, error: Summary.AllowedError): SummaryDsl[F, A] =
    new SummaryDsl[F, A](
      quantiles :+ QuantileDefinition(quantile, error),
      maxAgeValue,
      ageBucketsValue,
      makeSummary,
      makeLabelledSummary
    )

  override def maxAge(age: FiniteDuration): AgeBucketsStep[F, A] =
    new AgeBucketsStep[F, A](quantiles, age, ageBucketsValue, makeSummary, makeLabelledSummary)
}

object SummaryDsl {
  trait Base[F[_], A] extends BuildStep[F, Summary[F, A]] { self: MetricDsl[F, A, Summary, Summary.Labelled] =>
    def quantile(quantile: Summary.Quantile, error: Summary.AllowedError): SummaryDsl[F, A]
    def maxAge(age: FiniteDuration): AgeBucketsStep[F, A]

    def label[B]: FirstLabelApply[F, A, B, Summary.Labelled]

    /** @inheritdoc
      */
    def unsafeLabels(
        labelNames: IndexedSeq[Label.Name]
    ): BuildStep[F, Summary.Labelled[F, A, Map[Label.Name, String]]]

    /** @inheritdoc
      */
    def unsafeLabels(
        labelNames: Label.Name*
    ): BuildStep[F, Summary.Labelled[F, A, Map[Label.Name, String]]]

    /** @inheritdoc
      */
    def labels[B, N <: Nat](labelNames: Sized[IndexedSeq[Label.Name], N])(
        f: B => Sized[IndexedSeq[String], N]
    ): LabelsBuildStep[F, A, B, N, Summary.Labelled]
  }

  private val defaultQuantiles: Seq[Summary.QuantileDefinition] = Seq.empty

  private val defaultMaxAge: FiniteDuration = 10.minutes

  class WithCallbacks[F[_], A, A0](
      quantiles: Seq[QuantileDefinition] = SummaryDsl.defaultQuantiles,
      maxAgeValue: FiniteDuration = SummaryDsl.defaultMaxAge,
      ageBucketsValue: Summary.AgeBuckets = Summary.AgeBuckets.Default,
      makeSummary: (Seq[QuantileDefinition], FiniteDuration, Summary.AgeBuckets) => F[Summary[F, A]],
      makeSummaryCallback: F[A0] => F[Unit],
      makeLabelledSummary: (
          Seq[QuantileDefinition],
          FiniteDuration,
          Summary.AgeBuckets
      ) => LabelledMetricPartiallyApplied[F, A, Summary.Labelled],
      makeLabelledSummaryCallback: LabelledCallbackPartiallyApplied[F, A0]
  ) extends MetricDsl.WithCallbacks[F, A, A0, Summary, Summary.Labelled](
        makeSummary(quantiles, maxAgeValue, ageBucketsValue),
        makeSummaryCallback,
        makeLabelledSummary(quantiles, maxAgeValue, ageBucketsValue),
        makeLabelledSummaryCallback
      )
      with Base[F, A] {
    override def quantile(quantile: Summary.Quantile, error: Summary.AllowedError): SummaryDsl[F, A] =
      new SummaryDsl[F, A](
        quantiles :+ QuantileDefinition(quantile, error),
        maxAgeValue,
        ageBucketsValue,
        makeSummary,
        makeLabelledSummary
      )

    override def maxAge(age: FiniteDuration): AgeBucketsStep[F, A] =
      new AgeBucketsStep[F, A](quantiles, age, ageBucketsValue, makeSummary, makeLabelledSummary)
  }
}

class AgeBucketsStep[F[_], A] private[summary] (
    quantiles: Seq[QuantileDefinition],
    maxAgeValue: FiniteDuration,
    ageBucketsValue: Summary.AgeBuckets,
    makeSummary: (Seq[QuantileDefinition], FiniteDuration, Summary.AgeBuckets) => F[Summary[F, A]],
    makeLabelledSummary: (
        Seq[QuantileDefinition],
        FiniteDuration,
        Summary.AgeBuckets
    ) => LabelledMetricPartiallyApplied[F, A, Summary.Labelled]
) extends MetricDsl[F, A, Summary, Summary.Labelled](
      makeSummary(quantiles, maxAgeValue, ageBucketsValue),
      makeLabelledSummary(quantiles, maxAgeValue, ageBucketsValue)
    ) {
  def ageBuckets(buckets: Summary.AgeBuckets): MetricDsl[F, A, Summary, Summary.Labelled] =
    new MetricDsl[F, A, Summary, Summary.Labelled](
      makeSummary(quantiles, maxAgeValue, buckets),
      makeLabelledSummary(quantiles, maxAgeValue, buckets)
    )
}
