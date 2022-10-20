package prometheus4cats.internal.summary

import prometheus4cats.Summary.Quantile
import prometheus4cats._
import prometheus4cats.internal.{LabelledMetricPartiallyApplied, MetricDsl}

import scala.concurrent.duration._

class SummaryDsl[F[_], A] private[prometheus4cats] (
    quantiles: Seq[Quantile] = SummaryDsl.defaultQuantiles,
    maxAgeValue: FiniteDuration = SummaryDsl.defaultMaxAge,
    ageBucketsValue: Int = SummaryDsl.defaultAgeBuckets,
    makeSummary: (Seq[Quantile], FiniteDuration, Int) => F[Summary[F, A]],
    makeLabelledSummary: (Seq[Quantile], FiniteDuration, Int) => LabelledMetricPartiallyApplied[F, A, Summary.Labelled]
) extends MetricDsl[F, A, Summary, Summary.Labelled](
      makeSummary(quantiles, maxAgeValue, ageBucketsValue),
      makeLabelledSummary(quantiles, maxAgeValue, ageBucketsValue)
    ) {
  def quantile(q: Quantile) = new SummaryDsl[F, A](
    quantiles :+ q,
    maxAgeValue,
    ageBucketsValue,
    makeSummary,
    makeLabelledSummary
  )

  def maxAge(age: FiniteDuration): AgeBucketsStep[F, A] = new AgeBucketsStep[F, A](
    quantiles,
    age,
    ageBucketsValue,
    makeSummary,
    makeLabelledSummary
  )
}

object SummaryDsl {
  private val defaultQuantiles: Seq[Summary.Quantile] = Seq.empty

  private val defaultMaxAge: FiniteDuration = 10.minutes

  private val defaultAgeBuckets: Int = 5
}

class AgeBucketsStep[F[_], A] private[summary] (
    quantiles: Seq[Quantile],
    maxAgeValue: FiniteDuration,
    ageBucketsValue: Int,
    makeSummary: (Seq[Quantile], FiniteDuration, Int) => F[Summary[F, A]],
    makeLabelledSummary: (Seq[Quantile], FiniteDuration, Int) => LabelledMetricPartiallyApplied[F, A, Summary.Labelled]
) extends MetricDsl[F, A, Summary, Summary.Labelled](
      makeSummary(quantiles, maxAgeValue, ageBucketsValue),
      makeLabelledSummary(quantiles, maxAgeValue, ageBucketsValue)
    ) {
  def ageBuckets(buckets: Int) = new MetricDsl[F, A, Summary, Summary.Labelled](
    makeSummary(quantiles, maxAgeValue, buckets),
    makeLabelledSummary(quantiles, maxAgeValue, buckets)
  )
}
