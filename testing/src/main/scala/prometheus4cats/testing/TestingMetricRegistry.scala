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

package prometheus4cats
package testing

import cats.data.{Chain, NonEmptyList, NonEmptySeq}
import cats.effect.kernel._
import cats.effect.std.MapRef
import cats.syntax.all._
import prometheus4cats.testing.TestingMetricRegistry._
import prometheus4cats.util.{DoubleCallbackRegistry, DoubleMetricRegistry, NameUtils}

import scala.concurrent.duration.FiniteDuration

sealed abstract class TestingMetricRegistry[F[_]] private (
    private val underlying: MapRef[F, (String, List[String]), Option[
      (Int, MetricType, Metric[Double], MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]])
    ]],
    private val info: MapRef[F, String, Option[(Int, Info[F, Map[Label.Name, String]])]]
)(implicit override val F: Concurrent[F])
    extends DoubleMetricRegistry[F]
    with DoubleCallbackRegistry[F] {

  def counterHistory(name: Counter.Name, commonLabels: Metric.CommonLabels): F[Option[Chain[Double]]] =
    counterHistory(None, name, commonLabels)

  def counterHistory(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      commonLabels: Metric.CommonLabels
  ): F[Option[Chain[Double]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels),
      values(commonLabels),
      MetricType.Counter
    ).map(_.map(_.map(_._1)))

  def counterHistory(
      name: Counter.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[Double]]] = counterHistory(None, name, commonLabels, labelNames, labelValues)

  def counterHistory(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[Double]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      values(commonLabels, labelValues),
      MetricType.Counter
    ).map(_.map(_.map(_._1)))

  def exemplarCounterHistory(
      name: Counter.Name,
      commonLabels: Metric.CommonLabels
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    exemplarCounterHistory(None, name, commonLabels)

  def exemplarCounterHistory(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      commonLabels: Metric.CommonLabels
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels),
      values(commonLabels),
      MetricType.Counter
    )

  def exemplarCounterHistory(
      name: Counter.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    exemplarCounterHistory(None, name, commonLabels, labelNames, labelValues)

  def exemplarCounterHistory(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      values(commonLabels, labelValues),
      MetricType.Counter
    )

  def gaugeHistory(name: Gauge.Name, commonLabels: Metric.CommonLabels): F[Option[Chain[Double]]] =
    gaugeHistory(None, name, commonLabels)

  def gaugeHistory(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      commonLabels: Metric.CommonLabels
  ): F[Option[Chain[Double]]] =
    metricHistory(NameUtils.makeName(prefix, name.value), names(commonLabels), values(commonLabels), MetricType.Gauge)
      .map(_.map(_.map(_._1)))

  def gaugeHistory(
      name: Gauge.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[Double]]] = gaugeHistory(None, name, commonLabels, labelNames, labelValues)

  def gaugeHistory(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[Double]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      values(commonLabels, labelValues),
      MetricType.Gauge
    ).map(_.map(_.map(_._1)))

  def histogramHistory(name: Histogram.Name, commonLabels: Metric.CommonLabels): F[Option[Chain[Double]]] =
    histogramHistory(None, name, commonLabels)

  def histogramHistory(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      commonLabels: Metric.CommonLabels
  ): F[Option[Chain[Double]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels),
      values(commonLabels),
      MetricType.Histogram
    ).map(_.map(_.map(_._1)))

  def histogramHistory(
      name: Histogram.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[Double]]] = histogramHistory(None, name, commonLabels, labelNames, labelValues)

  def histogramHistory(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[Double]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      values(commonLabels, labelValues),
      MetricType.Histogram
    ).map(_.map(_.map(_._1)))

  def exemplarHistogramHistory(
      name: Histogram.Name,
      commonLabels: Metric.CommonLabels
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    exemplarHistogramHistory(None, name, commonLabels)

  def exemplarHistogramHistory(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      commonLabels: Metric.CommonLabels
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels),
      values(commonLabels),
      MetricType.Histogram
    )

  def exemplarHistogramHistory(
      name: Histogram.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    exemplarHistogramHistory(None, name, commonLabels, labelNames, labelValues)

  def exemplarHistogramHistory(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      values(commonLabels, labelValues),
      MetricType.Histogram
    )

  def summaryHistory(name: Summary.Name, commonLabels: Metric.CommonLabels): F[Option[Chain[Double]]] =
    summaryHistory(None, name, commonLabels)

  def summaryHistory(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      commonLabels: Metric.CommonLabels
  ): F[Option[Chain[Double]]] =
    metricHistory(NameUtils.makeName(prefix, name.value), names(commonLabels), values(commonLabels), MetricType.Summary)
      .map(_.map(_.map(_._1)))

  def summaryHistory(
      name: Summary.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[Double]]] = summaryHistory(None, name, commonLabels, labelNames, labelValues)

  def summaryHistory(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      labelValues: IndexedSeq[String]
  ): F[Option[Chain[Double]]] =
    metricHistory(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      values(commonLabels, labelValues),
      MetricType.Summary
    ).map(_.map(_.map(_._1)))

  def infoValue(
      prefix: Option[Metric.Prefix],
      name: Summary.Name
  ): F[Option[Double]] = info(NameUtils.makeName(prefix, name.value)).get.map(_.as(1.0))

  override def createAndRegisterDoubleCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Counter[F, Double, Unit]] =
    store(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels),
      MetricType.Counter,
      (ref: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]]) =>
        Counter.make[F, Double, Unit]((d: Double, _: Unit, e: Option[Exemplar.Labels]) =>
          ref(values(commonLabels)).update(c => c.append((c.lastOption.get._1 + d, e)))
        ),
      Chain.one(0.0 -> None)
    )

  override def createAndRegisterLabelledDoubleCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter[F, Double, A]] =
    store(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      MetricType.Counter,
      (ref: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]]) =>
        Counter.make[F, Double, A]((d: Double, a: A, e: Option[Exemplar.Labels]) =>
          ref(values(commonLabels, f(a))).update(c => c.append((c.lastOption.get._1 + d, e)))
        ),
      Chain.one(0.0 -> None)
    )

  override def createAndRegisterDoubleGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): Resource[F, Gauge[F, Double, Unit]] =
    store(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels),
      MetricType.Gauge,
      (ref: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]]) =>
        Gauge.make[F, Double, Unit](
          (d: Double, _: Unit) => ref(values(commonLabels)).update(c => c.append((c.lastOption.get._1 + d, None))),
          (d: Double, _: Unit) => ref(values(commonLabels)).update(c => c.append((c.lastOption.get._1 - d, None))),
          (d: Double, _: Unit) => ref(values(commonLabels)).update(_.append(d -> None))
        ),
      Chain.one(0.0 -> None)
    )

  override def createAndRegisterLabelledDoubleGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge[F, Double, A]] =
    store(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      MetricType.Gauge,
      (ref: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]]) =>
        Gauge.make[F, Double, A](
          (d: Double, a: A) => ref(values(commonLabels, f(a))).update(c => c.append((c.lastOption.get._1 + d, None))),
          (d: Double, a: A) => ref(values(commonLabels, f(a))).update(c => c.append((c.lastOption.get._1 - d, None))),
          (d: Double, a: A) => ref(values(commonLabels, f(a))).update(_.append(d -> None))
        ),
      Chain.one(0.0 -> None)
    )

  override def createAndRegisterDoubleHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double]
  ): Resource[F, Histogram[F, Double, Unit]] =
    store(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels),
      MetricType.Histogram,
      (ref: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]]) =>
        Histogram.make[F, Double, Unit]((d: Double, _: Unit, e: Option[Exemplar.Labels]) =>
          ref(values(commonLabels)).update(_.append(d -> e))
        ),
      Chain.nil
    )

  override def createAndRegisterLabelledDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] =
    store(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      MetricType.Histogram,
      (ref: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]]) =>
        Histogram.make[F, Double, A]((d: Double, a: A, e: Option[Exemplar.Labels]) =>
          ref(values(commonLabels, f(a))).update(_.append(d -> e))
        ),
      Chain.nil
    )

  override def createAndRegisterDoubleSummary(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  ): Resource[F, Summary[F, Double, Unit]] =
    store(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels),
      MetricType.Summary,
      (ref: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]]) =>
        Summary.make[F, Double, Unit]((d: Double, _: Unit) => ref(values(commonLabels)).update(_.append(d -> None))),
      Chain.nil
    )

  override def createAndRegisterLabelledDoubleSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[F, Summary[F, Double, A]] =
    store(
      NameUtils.makeName(prefix, name.value),
      names(commonLabels, labelNames),
      MetricType.Summary,
      (ref: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]]) =>
        Summary.make[F, Double, A]((d: Double, a: A) => ref(values(commonLabels, f(a))).update(_.append(d -> None))),
      Chain.nil
    )

  override def createAndRegisterInfo(
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help
  ): Resource[F, Info[F, Map[Label.Name, String]]] = {
    val release = info(NameUtils.makeName(prefix, name.value)).update {
      case None => throw new RuntimeException("This should be unreachable - our reference counting has a bug")
      case Some((n, i)) => if (n == 1) None else Some(n - 1 -> i)

    }
    Resource.make(
      info(NameUtils.makeName(prefix, name.value)).modify {
        case None =>
          val i = Info.make[F, Map[Label.Name, String]]((_: Map[Label.Name, String]) => F.unit)
          Some(1 -> i) -> i
        case Some((n, i)) => Some(n + 1 -> i) -> i
      }
    )(_ => release)
  }

  override def registerDoubleCounterCallback(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Double]
  ): Resource[F, Unit] = Resource.unit

  override def registerLabelledDoubleCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

  override def registerDoubleGaugeCallback(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Double]
  ): Resource[F, Unit] = Resource.unit

  override def registerLabelledDoubleGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

  override def registerDoubleHistogramCallback(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double],
      callback: F[Histogram.Value[Double]]
  ): Resource[F, Unit] = Resource.unit

  override def registerLabelledDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      callback: F[NonEmptyList[(Histogram.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

  override def registerDoubleSummaryCallback(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Summary.Value[Double]]
  ): Resource[F, Unit] = Resource.unit

  override def registerLabelledDoubleSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Summary.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = Resource.unit

  override def registerMetricCollectionCallback(
      prefix: Option[Metric.Prefix],
      commonLabels: Metric.CommonLabels,
      callback: F[MetricCollection]
  ): Resource[F, Unit] = Resource.unit

  private def store[M <: Metric[Double]](
      name: String,
      labels: List[String],
      tpe: MetricType,
      create: MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]] => M,
      initial: Chain[(Double, Option[Exemplar.Labels])]
  ): Resource[F, M] =
    Resource
      .eval(
        MapRef
          .ofShardedImmutableMap[F, List[String], Chain[(Double, Option[Exemplar.Labels])]](32)
          .map(r => MapRef.defaultedMapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]](r, initial))
          .flatMap { ref =>
            val release =
              underlying(name -> labels).update {
                case None => throw new RuntimeException("This should be unreachable - our reference counting has a bug")
                case Some((n, t, c, h)) => if (n == 1) None else Some((n - 1, t, c, h))
              }

            underlying(name -> labels).modify {
              case None =>
                val m = create(ref)
                Some((1, tpe, m, ref)) -> F.pure(
                  Resource.make(F.pure(m))(_ => release)
                )
              case curr @ Some((n, t, m, h)) =>
                if (t == tpe)
                  Some((n + 1, t, m, h)) ->
                    F.pure(
                      Resource.make(
                        // Cast safe by construction
                        F.pure(m.asInstanceOf[M])
                      )(_ => release)
                    )
                else
                  curr -> F.raiseError[Resource[F, M]](
                    new RuntimeException(
                      s"Cannot create metric of type $tpe as metric of type $t already exists with the same name and labels"
                    )
                  )
            }.flatten
          }
      )
      .flatten

  def metricHistory(
      name: String,
      labelNames: List[String],
      labelValues: List[String],
      tpe: MetricType
  ): F[Option[Chain[(Double, Option[Exemplar.Labels])]]] =
    underlying(name -> labelNames).get.flatMap(_.flatTraverse {
      case (_, t, _, r) if t == tpe =>
        r(labelValues).get.map(_.some)
      case _ => Option.empty[Chain[(Double, Option[Exemplar.Labels])]].pure[F]
    })

  private def names(commonLabels: Metric.CommonLabels): List[String] =
    commonLabels.value.keys.map(_.value).toList

  private def names(commonLabels: Metric.CommonLabels, labels: IndexedSeq[Label.Name]): List[String] =
    names(commonLabels) ++ labels.map(_.value)

  private def values(commonLabels: Metric.CommonLabels): List[String] =
    commonLabels.value.values.toList

  private def values(comonLabels: Metric.CommonLabels, labels: IndexedSeq[String]): List[String] =
    values(comonLabels) ++ labels
}

object TestingMetricRegistry {

  def apply[F[_]: Concurrent]: F[TestingMetricRegistry[F]] = (
    MapRef
      .ofShardedImmutableMap[
        F,
        (String, List[String]),
        (Int, MetricType, Metric[Double], MapRef[F, List[String], Chain[(Double, Option[Exemplar.Labels])]])
      ](256),
    MapRef.ofShardedImmutableMap[F, String, (Int, Info[F, Map[Label.Name, String]])](64)
  ).mapN { case (m, i) => new TestingMetricRegistry(m, i) {} }

  sealed trait MetricType
  object MetricType {
    case object Counter extends MetricType
    case object Gauge extends MetricType
    case object Histogram extends MetricType
    case object Summary extends MetricType
    case object Info extends MetricType
  }
}
