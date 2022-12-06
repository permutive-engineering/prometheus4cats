package prometheus4cats
package testing

import cats.syntax.all._
import cats.data.Chain
import cats.effect.kernel._
import cats.effect.std.MapRef
import cats.data.NonEmptySeq
import prometheus4cats.util.DoubleMetricRegistry
import scala.concurrent.duration.FiniteDuration
import TestingMetricRegistry._

class TestingMetricRegistry[F[_]](
    private val store: MapRef[F, (String, List[String]), Option[(Int, MetricType, Metric[Double])]]
)(implicit F: Concurrent[F])
    extends DoubleMetricRegistry[F] {

  private def store[M <: Metric[Double], State](
      name: String,
      labels: List[String],
      tpe: MetricType,
      create: Ref[F, State] => M,
      initial: State
  ): Resource[F, M] =
    Resource
      .eval(F.ref(initial).flatMap { ref =>
        val release =
          store(name -> labels).update {
            case None => throw new RuntimeException("This should be unreachable - our reference counting has a bug")
            case Some((n, t, c)) => if (n == 1) None else Some((n - 1, t, c))
          }

        store(name -> labels).modify {
          case None =>
            val m = create(ref)
            Some((1, tpe, m)) -> F.pure(
              Resource.make(F.pure(m))(_ => release)
            )
          case Some((n, t, c)) =>
            Some((n + 1, t, c)) -> F.pure(
              Resource.make(
                // Cast safe by construction
                F.pure(c.asInstanceOf[M])
              )(_ => release)
            )
        }.flatten
      })
      .flatten

  override protected[prometheus4cats] def createAndRegisterDoubleCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Counter[F, Double]] =
    store(
      name.value,
      commonLabels.value.values.toList,
      MetricType.Counter,
      (ref: Ref[F, Double]) => Counter.make[F, Double]((d: Double) => ref.update(_ + d)),
      0.0
    )

  override protected[prometheus4cats] def createAndRegisterLabelledDoubleCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter.Labelled[F, Double, A]] =
    store(
      name.value,
      (commonLabels.value.values ++ labelNames.map(_.value)).toList,
      MetricType.Counter,
      (ref: Ref[F, Double]) => Counter.Labelled.make[F, Double, A]((d: Double, _: A) => ref.update(_ + d)),
      0.0
    )

  // TODO handle prefix
  override protected[prometheus4cats] def createAndRegisterDoubleGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Gauge[F, Double]] =
    store(
      name.value,
      commonLabels.value.values.toList,
      MetricType.Gauge,
      (ref: Ref[F, Double]) =>
        Gauge.make[F, Double](
          (d: Double) => ref.update(_ + d),
          (d: Double) => ref.update(_ - d),
          (d: Double) => ref.set(d)
        ),
      0.0
    )

  override protected[prometheus4cats] def createAndRegisterLabelledDoubleGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge.Labelled[F, Double, A]] =
    store(
      name.value,
      (commonLabels.value.values ++ labelNames.map(_.value)).toList,
      MetricType.Gauge,
      (ref: Ref[F, Double]) =>
        Gauge.Labelled.make[F, Double, A](
          (d: Double, _: A) => ref.update(_ + d),
          (d: Double, _: A) => ref.update(_ - d),
          (d: Double, _: A) => ref.set(d)
        ),
      0.0
    )

  override protected[prometheus4cats] def createAndRegisterDoubleHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double]
  ): Resource[F, Histogram[F, Double]] =
    store(
      name.value,
      commonLabels.value.values.toList,
      MetricType.Histogram,
      (ref: Ref[F, Chain[Double]]) => Histogram.make[F, Double]((d: Double) => ref.update(_.append(d))),
      Chain.nil
    )

  override protected[prometheus4cats] def createAndRegisterLabelledDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram.Labelled[F, Double, A]] =
    store(
      name.value,
      (commonLabels.value.values ++ labelNames.map(_.value)).toList,
      MetricType.Histogram,
      (ref: Ref[F, Chain[Double]]) =>
        Histogram.Labelled.make[F, Double, A]((d: Double, _: A) => ref.update(_.append(d))),
      Chain.nil
    )

  override protected[prometheus4cats] def createAndRegisterDoubleSummary(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  ): Resource[F, Summary[F, Double]] =
    store(
      name.value,
      commonLabels.value.values.toList,
      MetricType.Summary,
      (ref: Ref[F, Chain[Double]]) => Summary.make[F, Double]((d: Double) => ref.update(_.append(d))),
      Chain.nil
    )

  override protected[prometheus4cats] def createAndRegisterLabelledDoubleSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[F, Summary.Labelled[F, Double, A]] =
    store(
      name.value,
      (commonLabels.value.values ++ labelNames.map(_.value)).toList,
      MetricType.Summary,
      (ref: Ref[F, Chain[Double]]) => Summary.Labelled.make[F, Double, A]((d: Double, _: A) => ref.update(_.append(d))),
      Chain.nil
    )

  override protected[prometheus4cats] def createAndRegisterInfo(
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help
  ): Resource[F, Info[F, Map[Label.Name, String]]] = ???

}

object TestingMetricRegistry {
  sealed private trait MetricType
  private object MetricType {
    case object Counter extends MetricType
    case object Gauge extends MetricType
    case object Histogram extends MetricType
    case object Summary extends MetricType
  }
}
