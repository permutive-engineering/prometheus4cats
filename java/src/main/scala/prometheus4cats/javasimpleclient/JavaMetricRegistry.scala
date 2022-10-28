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

package prometheus4cats.javasimpleclient

import java.util

import cats.data.NonEmptySeq
import cats.effect.kernel._
import cats.effect.std.{Dispatcher, Semaphore}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.show._
import cats.{Applicative, ApplicativeThrow, Functor, Show}
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.{
  Collector,
  CollectorRegistry,
  CounterMetricFamily,
  GaugeMetricFamily,
  SimpleCollector,
  SummaryMetricFamily,
  Counter => PCounter,
  Gauge => PGauge,
  Histogram => PHistogram,
  Info => PInfo,
  Summary => PSummary
}
import org.typelevel.log4cats.Logger
import prometheus4cats._
import prometheus4cats.javasimpleclient.internal.{HistogramUtils, MetricCollectionProcessor, Utils}
import prometheus4cats.javasimpleclient.models.MetricType
import prometheus4cats.util.{DoubleCallbackRegistry, DoubleMetricRegistry, NameUtils}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class JavaMetricRegistry[F[_]: Async: Logger] private (
    registry: CollectorRegistry,
    ref: Ref[F, State],
    metricCollectionCollector: MetricCollectionProcessor[F],
    sem: Semaphore[F],
    dispatcher: Dispatcher[F],
    callbackTimeout: FiniteDuration
) extends DoubleMetricRegistry[F]
    with DoubleCallbackRegistry[F] {
  override protected val F: Functor[F] = implicitly

  private def counterName[A: Show](name: A) = name match {
    case counter: Counter.Name => counter.value.replace("_total", "")
    case _ => name.show
  }

  private def timeoutCallback[A](fa: F[A], onError: A, supplementalError: String): A =
    Utils.timeoutCallback(dispatcher, callbackTimeout, fa, onError, supplementalError)

  private def configureBuilderOrRetrieve[A: Show, B <: SimpleCollector.Builder[B, C], C <: SimpleCollector[_]](
      builder: SimpleCollector.Builder[B, C],
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      name: A,
      help: Metric.Help,
      labels: IndexedSeq[Label.Name],
      modifyBuilder: Option[B => B] = None
  ): F[C] = {
    lazy val n = counterName(name)

    lazy val metricId: MetricID = (labels, metricType)
    lazy val fullName: StateKey = (metricPrefix, n)
    lazy val renderedFullName = NameUtils.makeName(metricPrefix, name)

    // the semaphore is needed here because `update` can't be used on the Ref, due to creation of the collector
    // possibly throwing and therefore needing to be wrapped in a `Sync.delay`. This would be fine, but the actual
    // state must be pure and the collector is needed for that.
    sem.permit.surround(
      ref.get
        .flatMap[(State, C)] { (metrics: State) =>
          metrics.get(fullName) match {
            case Some((expected, Right(collector))) =>
              if (metricId == expected) Applicative[F].pure(metrics -> collector.asInstanceOf[C])
              else
                ApplicativeThrow[F].raiseError(
                  new RuntimeException(
                    s"A metric with the same name as '$renderedFullName' is already registered with different labels and/or type"
                  )
                )
            case Some((_, Left(_))) =>
              ApplicativeThrow[F].raiseError(
                new RuntimeException(
                  s"A callback with the same name as '$renderedFullName' is already registered with different labels and/or type"
                )
              )
            case None =>
              Sync[F].delay {
                val b: B =
                  builder
                    .name(NameUtils.makeName(metricPrefix, name))
                    .help(help.value)
                    .labelNames(labels.map(_.value): _*)

                modifyBuilder.foreach(f => f(b))

                b.register(registry)
              }.map { collector =>
                metrics.updated(fullName, (metricId, Right(collector))) -> collector
              }
          }
        }
        .flatMap { case (state, collector) => ref.set(state).as(collector) }
    )
  }

  // the semaphore is needed here because `update` can't be used on the Ref, due to creation of the collector
  // possibly throwing and therefore needing to be wrapped in a `Sync.delay`. This would be fine, but the actual
  // state must be pure and the collector is needed for that.
  private def registerCallback[A: Show](
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      name: A,
      labels: IndexedSeq[Label.Name],
      collector: Collector
  ): F[Unit] = {
    lazy val n = counterName(name)

    lazy val metricId: MetricID = (labels, metricType)
    lazy val fullName: StateKey = (metricPrefix, n)
    lazy val renderedFullName = NameUtils.makeName(metricPrefix, name)

    sem.permit.surround(
      ref.get
        .flatMap[State] { (metrics: State) =>
          metrics.get(fullName) match {
            case Some((_, collector)) =>
              val collectorType = if (collector.isRight) "metric" else "callback"

              ApplicativeThrow[F].raiseError(
                new RuntimeException(
                  s"A $collectorType with the same name as '$renderedFullName' is already registered with different labels and/or type"
                )
              )
            case None =>
              Sync[F].delay(registry.register(collector)).as(metrics.updated(fullName, (metricId, Left(collector))))
          }

        }
        .flatMap(ref.set)
    )
  }

  override protected[prometheus4cats] def createAndRegisterDoubleCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Counter[F, Double]] = {
    lazy val commonLabelNames = commonLabels.value.keys.toIndexedSeq
    lazy val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieve(
      PCounter.build(),
      MetricType.Counter,
      prefix,
      name,
      help,
      commonLabels.value.keys.toIndexedSeq
    ).map { counter =>
      Counter.make(
        1.0,
        (d: Double) =>
          Utils
            .modifyMetric[F, Counter.Name, PCounter.Child](counter, name, commonLabelNames, commonLabelValues, _.inc(d))
      )
    }
  }

  override protected[prometheus4cats] def createAndRegisterLabelledDoubleCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, Double, A]] = {
    val commonLabelNames = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieve(
      PCounter.build(),
      MetricType.Counter,
      prefix,
      name,
      help,
      labelNames ++ commonLabels.value.keys.toIndexedSeq
    ).map { counter =>
      Counter.Labelled.make(
        1.0,
        (d: Double, labels: A) =>
          Utils.modifyMetric[F, Counter.Name, PCounter.Child](
            counter,
            name,
            labelNames ++ commonLabelNames,
            f(labels) ++ commonLabelValues,
            _.inc(d)
          )
      )
    }
  }

  override protected[prometheus4cats] def createAndRegisterDoubleGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Gauge[F, Double]] = {
    val commonLabelNames = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq
    configureBuilderOrRetrieve(
      PGauge.build(),
      MetricType.Gauge,
      prefix,
      name,
      help,
      commonLabels.value.keys.toIndexedSeq
    ).map { gauge =>
      @inline
      def modify(f: PGauge.Child => Unit): F[Unit] =
        Utils.modifyMetric(gauge, name, commonLabelNames, commonLabelValues, f)

      def inc(n: Double): F[Unit] =
        modify(_.inc(n))

      def dec(n: Double): F[Unit] =
        modify(_.dec(n))

      def set(n: Double): F[Unit] =
        modify(_.set(n))

      Gauge.make(inc, dec, set)
    }
  }

  override protected[prometheus4cats] def createAndRegisterLabelledDoubleGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, Double, A]] = {
    val commonLabelNames = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieve(
      PGauge.build(),
      MetricType.Gauge,
      prefix,
      name,
      help,
      labelNames ++ commonLabels.value.keys.toIndexedSeq
    ).map { gauge =>
      @inline
      def modify(g: PGauge.Child => Unit, labels: A): F[Unit] =
        Utils.modifyMetric(gauge, name, labelNames ++ commonLabelNames, f(labels) ++ commonLabelValues, g)

      def inc(n: Double, labels: A): F[Unit] = modify(_.inc(n), labels)

      def dec(n: Double, labels: A): F[Unit] = modify(_.dec(n), labels)

      def set(n: Double, labels: A): F[Unit] = modify(_.set(n), labels)

      Gauge.Labelled.make(inc, dec, set)
    }
  }

  override protected[prometheus4cats] def createAndRegisterDoubleHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double]
  ): F[Histogram[F, Double]] = {
    val commonLabelNames = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieve(
      PHistogram.build().buckets(buckets.toSeq: _*),
      MetricType.Histogram,
      prefix,
      name,
      help,
      commonLabels.value.keys.toIndexedSeq
    ).map { histogram =>
      Histogram.make(d =>
        Utils.modifyMetric[F, Histogram.Name, PHistogram.Child](
          histogram,
          name,
          commonLabelNames,
          commonLabelValues,
          _.observe(d)
        )
      )
    }
  }

  override protected[prometheus4cats] def createAndRegisterLabelledDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, Double, A]] = {
    val commonLabelNames = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieve(
      PHistogram.build().buckets(buckets.toSeq: _*),
      MetricType.Histogram,
      prefix,
      name,
      help,
      labelNames ++ commonLabelNames
    ).map { histogram =>
      Histogram.Labelled.make[F, Double, A](_observe = { (d: Double, labels: A) =>
        Utils.modifyMetric[F, Histogram.Name, PHistogram.Child](
          histogram,
          name,
          labelNames ++ commonLabelNames,
          f(labels) ++ commonLabelValues,
          _.observe(d)
        )
      })
    }
  }

  override protected[prometheus4cats] def createAndRegisterDoubleSummary(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  ): F[Summary[F, Double]] = {

    val commonLabelNames = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieve(
      quantiles.foldLeft(PSummary.build().ageBuckets(ageBuckets.value).maxAgeSeconds(maxAge.toSeconds))((b, q) =>
        b.quantile(q.value.value, q.error.value)
      ),
      MetricType.Summary,
      prefix,
      name,
      help,
      commonLabelNames
    ).map { summary =>
      Summary.make[F, Double](d =>
        Utils.modifyMetric[F, Summary.Name, PSummary.Child](
          summary,
          name,
          commonLabelNames,
          commonLabelValues,
          _.observe(d)
        )
      )
    }
  }

  override protected[prometheus4cats] def createAndRegisterLabelledDoubleSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): F[Summary.Labelled[F, Double, A]] = {

    val commonLabelNames = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieve(
      quantiles.foldLeft(PSummary.build().ageBuckets(ageBuckets.value).maxAgeSeconds(maxAge.toSeconds))((b, q) =>
        b.quantile(q.value.value, q.error.value)
      ),
      MetricType.Summary,
      prefix,
      name,
      help,
      labelNames ++ commonLabelNames
    ).map { summary =>
      Summary.Labelled.make[F, Double, A] { case (d, labels) =>
        Utils.modifyMetric[F, Summary.Name, PSummary.Child](
          summary,
          name,
          labelNames ++ commonLabelNames,
          f(labels) ++ commonLabelValues,
          _.observe(d)
        )
      }
    }
  }

  // The java library always appends "_info" to the metric name, so we need a special `Show` instance
  implicit private val infoNameShow: Show[Info.Name] = Show.show(_.value.replace("_info", ""))

  override protected[prometheus4cats] def createAndRegisterInfo(
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help
  ): F[Info[F, Map[Label.Name, String]]] =
    configureBuilderOrRetrieve(
      PInfo.build(),
      MetricType.Info,
      prefix,
      name,
      help,
      IndexedSeq.empty
    ).map { info =>
      Info.make[F, Map[Label.Name, String]](labels =>
        Utils.modifyMetric[F, Info.Name, PInfo.Child](
          info,
          name,
          IndexedSeq.empty,
          IndexedSeq.empty,
          pinfo => pinfo.info(labels.map { case (n, v) => n.value -> v }.asJava)
        )
      )
    }

  private def register[A: Show, B](
      metricType: MetricType,
      prefix: Option[Metric.Prefix],
      name: A,
      commonLabels: Metric.CommonLabels,
      callback: F[B]
  )(
      makeFamily: (String, B) => Collector.MetricFamilySamples,
      makeLabelledFamily: (String, util.List[String], util.List[String], B) => Collector.MetricFamilySamples
  ): F[Unit] = {
    lazy val stringName = NameUtils.makeName(prefix, name)

    lazy val commonLabelNames: util.List[String] = commonLabels.value.keys.map(_.value).toList.asJava
    lazy val commonLabelValues: util.List[String] = commonLabels.value.values.toList.asJava

    def runCallback: Option[B] = timeoutCallback(callback.map(Option(_)), None, s"Affected metric: '$stringName'")

    lazy val collector = new Collector {
      override def collect(): util.List[Collector.MetricFamilySamples] =
        runCallback match {
          case Some(value) =>
            val metrics =
              if (commonLabels.value.isEmpty) List[Collector.MetricFamilySamples](makeFamily(stringName, value))
              else List(makeLabelledFamily(stringName, commonLabelNames, commonLabelValues, value))

            metrics.asJava
          case None => List.empty[Collector.MetricFamilySamples].asJava
        }

    }

    registerCallback(metricType, prefix, name, commonLabels.value.keys.toIndexedSeq, collector)
  }

  private def registerLabelled[A: Show, B, C](
      metricType: MetricType,
      prefix: Option[Metric.Prefix],
      name: A,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(B, C)]
  )(
      f: C => IndexedSeq[String],
      makeLabelledFamily: (String, util.List[String], util.List[String], B) => Collector.MetricFamilySamples
  ): F[Unit] = {
    lazy val stringName = NameUtils.makeName(prefix, name)

    lazy val commonLabelNames: util.List[String] =
      (labelNames ++ commonLabels.value.keys.toIndexedSeq).map(_.value).asJava
    lazy val commonLabelValues: IndexedSeq[String] = commonLabels.value.values.toIndexedSeq

    def runCallback: Option[(B, C)] = timeoutCallback(callback.map(Option(_)), None, s"Affected metric: '$stringName'")

    lazy val collector = new Collector {
      override def collect(): util.List[Collector.MetricFamilySamples] =
        runCallback match {
          case Some((value, labels)) =>
            val metrics =
              List(makeLabelledFamily(stringName, commonLabelNames, (f(labels) ++ commonLabelValues).asJava, value))

            metrics.asJava
          case None => List.empty[Collector.MetricFamilySamples].asJava
        }

    }

    registerCallback(metricType, prefix, name, labelNames ++ commonLabels.value.keys.toIndexedSeq, collector)
  }

  override protected[prometheus4cats] def registerDoubleCounterCallback(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Double]
  ): F[Unit] =
    register(MetricType.Counter, prefix, name, commonLabels, callback)(
      (n, v) => new CounterMetricFamily(n, help.value, if (v < 0) 0 else v),
      (n, lns, lvs, v) => new CounterMetricFamily(n, help.value, lns).addMetric(lvs, if (v < 0) 0 else v)
    )

  override protected[prometheus4cats] def registerLabelledDoubleCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Double, A)]
  )(f: A => IndexedSeq[String]): F[Unit] =
    registerLabelled(MetricType.Counter, prefix, name, commonLabels, labelNames, callback)(
      f,
      (n, lns, lvs, v) => new CounterMetricFamily(n, help.value, lns).addMetric(lvs, if (v < 0) 0 else v)
    )

  override protected[prometheus4cats] def registerDoubleGaugeCallback(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Double]
  ): F[Unit] = register(MetricType.Gauge, prefix, name, commonLabels, callback)(
    (n, v) => new GaugeMetricFamily(n, help.value, v),
    (n, lns, lvs, v) => new GaugeMetricFamily(n, help.value, lns).addMetric(lvs, v)
  )

  override protected[prometheus4cats] def registerLabelledDoubleGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Double, A)]
  )(f: A => IndexedSeq[String]): F[Unit] =
    registerLabelled(MetricType.Gauge, prefix, name, commonLabels, labelNames, callback)(
      f,
      (n, lns, lvs, v) => new GaugeMetricFamily(n, help.value, lns).addMetric(lvs, v)
    )

  override protected[prometheus4cats] def registerDoubleHistogramCallback(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double],
      callback: F[Histogram.Value[Double]]
  ): F[Unit] = {
    lazy val stringName = NameUtils.makeName(prefix, name)

    def runCallback: Option[Histogram.Value[Double]] =
      timeoutCallback(callback.map(Option(_)), None, s"Affected metric: '$stringName'")

    val makeSamples = HistogramUtils.histogramSamples(prefix, name, help, commonLabels.value, IndexedSeq.empty, buckets)

    val collector = new Collector {
      override def collect(): util.List[MetricFamilySamples] =
        runCallback match {
          case Some(value) => List(makeSamples(Seq(value -> IndexedSeq.empty[String]))).asJava
          case None => List.empty[Collector.MetricFamilySamples].asJava
        }
    }

    registerCallback(MetricType.Histogram, prefix, name, commonLabels.value.keys.toIndexedSeq, collector)
  }

  override protected[prometheus4cats] def registerLabelledDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      callback: F[(Histogram.Value[Double], A)]
  )(f: A => IndexedSeq[String]): F[Unit] = {
    lazy val stringName = NameUtils.makeName(prefix, name)

    def runCallback: Option[(Histogram.Value[Double], A)] =
      timeoutCallback(callback.map(Option(_)), None, s"Affected metric: '$stringName'")

    val makeSamples = HistogramUtils.histogramSamples(prefix, name, help, commonLabels.value, labelNames, buckets)

    val collector = new Collector {
      override def collect(): util.List[MetricFamilySamples] =
        runCallback match {
          case Some((value, labels)) => List(makeSamples(Seq(value -> f(labels)))).asJava
          case None => List.empty[Collector.MetricFamilySamples].asJava
        }
    }

    registerCallback(MetricType.Histogram, prefix, name, labelNames ++ commonLabels.value.keys.toIndexedSeq, collector)
  }

  override protected[prometheus4cats] def registerDoubleSummaryCallback(
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      callback: F[Summary.Value[Double]]
  ): F[Unit] =
    register(MetricType.Summary, prefix, name, commonLabels, callback)(
      (n, v) =>
        if (v.quantiles.isEmpty) new SummaryMetricFamily(n, help.value, v.count, v.sum)
        else
          new SummaryMetricFamily(
            n,
            help.value,
            List.empty[String].asJava,
            v.quantiles.keys.toList.map(_.asInstanceOf[java.lang.Double]).asJava
          )
            .addMetric(
              List.empty[String].asJava,
              v.count,
              v.sum,
              v.quantiles.values.toList.map(_.asInstanceOf[java.lang.Double]).asJava
            ),
      (n, lns, lvs, v) =>
        if (v.quantiles.isEmpty) new SummaryMetricFamily(n, help.value, lns).addMetric(lvs, v.count, v.sum)
        else
          new SummaryMetricFamily(
            n,
            help.value,
            lns,
            v.quantiles.keys.toList.map(_.asInstanceOf[java.lang.Double]).asJava
          )
            .addMetric(lvs, v.count, v.sum, v.quantiles.values.toList.map(_.asInstanceOf[java.lang.Double]).asJava)
    )

  override protected[prometheus4cats] def registerLabelledDoubleSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[(Summary.Value[Double], A)]
  )(f: A => IndexedSeq[String]): F[Unit] =
    registerLabelled(MetricType.Summary, prefix, name, commonLabels, labelNames, callback)(
      f,
      (n, lns, lvs, v) =>
        if (v.quantiles.isEmpty) new SummaryMetricFamily(n, help.value, lns).addMetric(lvs, v.count, v.sum)
        else
          new SummaryMetricFamily(
            n,
            help.value,
            lns,
            v.quantiles.keys.toList.map(_.asInstanceOf[java.lang.Double]).asJava
          )
            .addMetric(lvs, v.count, v.sum, v.quantiles.values.toList.map(_.asInstanceOf[java.lang.Double]).asJava)
    )

  override protected[prometheus4cats] def registerMetricCollectionCallback(
      prefix: Option[Metric.Prefix],
      commonLabels: Metric.CommonLabels,
      callback: F[MetricCollection]
  ): F[Unit] = metricCollectionCollector.register(prefix, commonLabels, callback)

}

object JavaMetricRegistry {
  def default[F[_]: Async: Logger](
      callbackTimeout: FiniteDuration = 10.millis,
      metricCollectionCallbackTimeout: FiniteDuration = 100.millis
  ): Resource[F, JavaMetricRegistry[F]] =
    fromSimpleClientRegistry(
      CollectorRegistry.defaultRegistry,
      callbackTimeout,
      metricCollectionCallbackTimeout
    )

  def fromSimpleClientRegistry[F[_]: Async: Logger](
      promRegistry: CollectorRegistry,
      callbackTimeout: FiniteDuration = 10.millis,
      metricCollectionCallbackTimeout: FiniteDuration = 100.millis
  ): Resource[F, JavaMetricRegistry[F]] = Dispatcher[F].flatMap { dis =>
    val acquire = for {
      ref <- Ref.of[F, State](Map.empty)
      sem <- Semaphore[F](1L)
      metricCollectionProcessor <- MetricCollectionProcessor
        .create(
          ref,
          dis,
          metricCollectionCallbackTimeout,
          promRegistry
        )
        .allocated
    } yield (
      ref,
      metricCollectionProcessor._2,
      new JavaMetricRegistry[F](promRegistry, ref, metricCollectionProcessor._1, sem, dis, callbackTimeout)
    )

    Resource
      .make(acquire) { case (ref, procRelease, _) =>
        procRelease >>
          ref.get.flatMap { metrics =>
            if (metrics.nonEmpty)
              metrics.values
                .map(_._2)
                .toList
                .traverse_ { collector =>
                  Sync[F].delay(promRegistry.unregister(collector.merge)).handleErrorWith { e =>
                    Logger[F].warn(e)(s"Failed to unregister a collector on shutdown.")
                  }
                }
            else Applicative[F].unit
          }
      }
      .map(_._3)
  }
}
