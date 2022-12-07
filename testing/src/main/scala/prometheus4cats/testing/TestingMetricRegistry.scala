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
    private val store: MapRef[F, (String, List[String]), Option[(Int, MetricType, Metric[Double], F[Chain[Double]])]]
)(implicit F: Concurrent[F])
    extends DoubleMetricRegistry[F] {

  def counterHistory(name: Counter.Name, commonLabels: Metric.CommonLabels): F[Option[Chain[Double]]] =
    metricHistory(name.value, commonLabels.value.values.toList, MetricType.Counter)

  def counterHistory(
      name: Counter.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  ): F[Option[Chain[Double]]] =
    metricHistory(name.value, labels(commonLabels, labelNames), MetricType.Counter)

  def gaugeHistory(name: Gauge.Name, commonLabels: Metric.CommonLabels): F[Option[Chain[Double]]] =
    metricHistory(name.value, commonLabels.value.values.toList, MetricType.Gauge)

  def gaugeHistory(
      name: Gauge.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  ): F[Option[Chain[Double]]] =
    metricHistory(name.value, labels(commonLabels, labelNames), MetricType.Gauge)

  def histogramHistory(name: Histogram.Name, commonLabels: Metric.CommonLabels): F[Option[Chain[Double]]] =
    metricHistory(name.value, commonLabels.value.values.toList, MetricType.Histogram)

  def histogramHistory(
      name: Histogram.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  ): F[Option[Chain[Double]]] =
    metricHistory(name.value, labels(commonLabels, labelNames), MetricType.Histogram)

  def summaryHistory(name: Summary.Name, commonLabels: Metric.CommonLabels): F[Option[Chain[Double]]] =
    metricHistory(name.value, commonLabels.value.values.toList, MetricType.Summary)

  def summaryHistory(
      name: Summary.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  ): F[Option[Chain[Double]]] =
    metricHistory(name.value, labels(commonLabels, labelNames), MetricType.Summary)

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
      (ref: Ref[F, Chain[Double]]) =>
        Counter.make[F, Double]((d: Double) => ref.update(c => c.append(c.lastOption.get + d))),
      Chain.one(0.0)
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
      labels(commonLabels, labelNames),
      MetricType.Counter,
      (ref: Ref[F, Chain[Double]]) =>
        Counter.Labelled.make[F, Double, A]((d: Double, _: A) => ref.update(c => c.append(c.lastOption.get + d))),
      Chain.one(0.0)
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
      (ref: Ref[F, Chain[Double]]) =>
        Gauge.make[F, Double](
          (d: Double) => ref.update(c => c.append(c.lastOption.get + d)),
          (d: Double) => ref.update(c => c.append(c.lastOption.get - d)),
          (d: Double) => ref.update(_.append(d))
        ),
      Chain.one(0.0)
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
      labels(commonLabels, labelNames),
      MetricType.Gauge,
      (ref: Ref[F, Chain[Double]]) =>
        Gauge.Labelled.make[F, Double, A](
          (d: Double, _: A) => ref.update(c => c.append(c.lastOption.get + d)),
          (d: Double, _: A) => ref.update(c => c.append(c.lastOption.get - d)),
          (d: Double, _: A) => ref.update(_.append(d))
        ),
      Chain.one(0.0)
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
      labels(commonLabels, labelNames),
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
      labels(commonLabels, labelNames),
      MetricType.Summary,
      (ref: Ref[F, Chain[Double]]) => Summary.Labelled.make[F, Double, A]((d: Double, _: A) => ref.update(_.append(d))),
      Chain.nil
    )

  override protected[prometheus4cats] def createAndRegisterInfo(
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help
  ): Resource[F, Info[F, Map[Label.Name, String]]] = ???

  private def store[M <: Metric[Double]](
      name: String,
      labels: List[String],
      tpe: MetricType,
      create: Ref[F, Chain[Double]] => M,
      initial: Chain[Double]
  ): Resource[F, M] =
    Resource
      .eval(F.ref(initial).flatMap { ref =>
        val release =
          store(name -> labels).update {
            case None => throw new RuntimeException("This should be unreachable - our reference counting has a bug")
            case Some((n, t, c, h)) => if (n == 1) None else Some((n - 1, t, c, h))
          }

        store(name -> labels).modify {
          case None =>
            val m = create(ref)
            Some((1, tpe, m, ref.get)) -> F.pure(
              Resource.make(F.pure(m))(_ => release)
            )
          case curr @ Some((n, t, c, h)) =>
            if (t == tpe)
              Some((n + 1, t, c, h)) ->
                F.pure(
                  Resource.make(
                    // Cast safe by construction
                    F.pure(c.asInstanceOf[M])
                  )(_ => release)
                )
            else
              curr -> F.raiseError[Resource[F, M]](
                new RuntimeException(
                  s"Cannot create metric of type $tpe as metric of type $t alreasy exists with the same name and labels"
                )
              )
        }.flatten
      })
      .flatten

  def metricHistory(name: String, labels: List[String], tpe: MetricType): F[Option[Chain[Double]]] =
    store(name -> labels).get.flatMap(_.flatMap {
      case (_, t, _, h) if t == tpe =>
        Some(h)
      case _ => None
    }.sequence)

  private def labels(comonLabels: Metric.CommonLabels, labels: IndexedSeq[Label.Name]): List[String] =
    (comonLabels.value.values ++ labels.map(_.value)).toList

}

object TestingMetricRegistry {

  def apply[F[_]: Concurrent]: F[TestingMetricRegistry[F]] = MapRef
    .ofShardedImmutableMap[F, (String, List[String]), (Int, MetricType, Metric[Double], F[Chain[Double]])](256)
    .map(m => new TestingMetricRegistry(m))

  sealed private trait MetricType
  private object MetricType {
    case object Counter extends MetricType
    case object Gauge extends MetricType
    case object Histogram extends MetricType
    case object Summary extends MetricType
  }
}
