package prometheus4cats.internal.summary

import prometheus4cats.Summary.QuantileDefinition
import prometheus4cats._
import prometheus4cats.internal.{LabelledCallbackPartiallyApplied, LabelledMetricPartiallyApplied, MetricDsl}

import scala.concurrent.duration._

class SummaryDsl[F[_], A] private[prometheus4cats] (
    quantiles: Seq[QuantileDefinition] = SummaryDsl.defaultQuantiles,
    maxAgeValue: FiniteDuration = SummaryDsl.defaultMaxAge,
    ageBucketsValue: Int = SummaryDsl.defaultAgeBuckets,
    makeSummary: (Seq[QuantileDefinition], FiniteDuration, Int) => F[Summary[F, A]],
    makeLabelledSummary: (
        Seq[QuantileDefinition],
        FiniteDuration,
        Int
    ) => LabelledMetricPartiallyApplied[F, A, Summary.Labelled]
) extends MetricDsl[F, A, Summary, Summary.Labelled](
      makeSummary(quantiles, maxAgeValue, ageBucketsValue),
      makeLabelledSummary(quantiles, maxAgeValue, ageBucketsValue)
    )
    with SummaryDsl.Base[F, A] {
  def quantile(q: QuantileDefinition) = new SummaryDsl[F, A](
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
  trait Base[F[_], A] { self: MetricDsl[F, A, Summary, Summary.Labelled] => }

  private val defaultQuantiles: Seq[Summary.QuantileDefinition] = Seq.empty

  private val defaultMaxAge: FiniteDuration = 10.minutes

  private val defaultAgeBuckets: Int = 5

  class WithCallbacks[F[_], A, A0](
      quantiles: Seq[QuantileDefinition] = SummaryDsl.defaultQuantiles,
      maxAgeValue: FiniteDuration = SummaryDsl.defaultMaxAge,
      ageBucketsValue: Int = SummaryDsl.defaultAgeBuckets,
      makeSummary: (Seq[QuantileDefinition], FiniteDuration, Int) => F[Summary[F, A]],
      makeSummaryCallback: F[A0] => F[Unit],
      makeLabelledSummary: (
          Seq[QuantileDefinition],
          FiniteDuration,
          Int
      ) => LabelledMetricPartiallyApplied[F, A, Summary.Labelled],
      makeLabelledSummaryCallback: LabelledCallbackPartiallyApplied[F, A0]
  ) extends MetricDsl.WithCallbacks[F, A, A0, Summary, Summary.Labelled](
        makeSummary(quantiles, maxAgeValue, ageBucketsValue),
        makeSummaryCallback,
        makeLabelledSummary(quantiles, maxAgeValue, ageBucketsValue),
        makeLabelledSummaryCallback
      )
      with Base[F, A] {}
}

class AgeBucketsStep[F[_], A] private[summary] (
    quantiles: Seq[QuantileDefinition],
    maxAgeValue: FiniteDuration,
    ageBucketsValue: Int,
    makeSummary: (Seq[QuantileDefinition], FiniteDuration, Int) => F[Summary[F, A]],
    makeLabelledSummary: (
        Seq[QuantileDefinition],
        FiniteDuration,
        Int
    ) => LabelledMetricPartiallyApplied[F, A, Summary.Labelled]
) extends MetricDsl[F, A, Summary, Summary.Labelled](
      makeSummary(quantiles, maxAgeValue, ageBucketsValue),
      makeLabelledSummary(quantiles, maxAgeValue, ageBucketsValue)
    ) {
  def ageBuckets(buckets: Int): MetricDsl[F, A, Summary, Summary.Labelled] =
    new MetricDsl[F, A, Summary, Summary.Labelled](
      makeSummary(quantiles, maxAgeValue, buckets),
      makeLabelledSummary(quantiles, maxAgeValue, buckets)
    )
}
