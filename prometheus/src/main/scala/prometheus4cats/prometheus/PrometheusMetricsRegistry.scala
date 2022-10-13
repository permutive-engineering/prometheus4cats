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

package prometheus4cats.prometheus

import cats.data.NonEmptySeq
import cats.effect.kernel._
import cats.effect.std.Semaphore
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.show._
import cats.{Applicative, ApplicativeThrow, Show}
import io.prometheus.client.{
  CollectorRegistry,
  SimpleCollector,
  Counter => PCounter,
  Gauge => PGauge,
  Histogram => PHistogram
}
import prometheus4cats.prometheus.internal.Utils
import prometheus4cats.prometheus.models.MetricType
import prometheus4cats.util.NameUtils
import prometheus4cats._
import org.typelevel.log4cats.Logger

class PrometheusMetricsRegistry[F[_]: Sync: Logger] private (
    registry: CollectorRegistry,
    ref: Ref[F, State],
    sem: Semaphore[F]
) extends MetricsRegistry[F] {

  private def configureBuilderOrRetrieve[A: Show, B <: SimpleCollector.Builder[B, C], C <: SimpleCollector[_]](
      builder: SimpleCollector.Builder[B, C],
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      name: A,
      help: Metric.Help,
      labels: IndexedSeq[Label.Name],
      modifyBuilder: Option[B => B] = None
  ): F[C] = {
    lazy val metricId: MetricID = (labels, metricType)
    lazy val fullName: StateKey = (metricPrefix, name.show)
    lazy val renderedFullName = NameUtils.makeName(metricPrefix, name)

    // the semaphore is needed here because `update` can't be used on the Ref, due to creation of the collector
    // possibly throwing and therefore needing to be wrapped in a `Sync.delay`. This would be fine, but the actual
    // state must be pure and the collector is needed for that.
    sem.permit.surround(
      ref.get
        .flatMap[(State, C)] { (metrics: State) =>
          metrics.get(fullName) match {
            case Some((expected, collector)) =>
              if (metricId == expected) Applicative[F].pure(metrics -> collector.asInstanceOf[C])
              else
                ApplicativeThrow[F].raiseError(
                  new RuntimeException(
                    s"A metric with the same name as '$renderedFullName' is already registered with different labels and/or type"
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
                metrics.updated(fullName, (metricId, collector)) -> collector
              }
          }
        }
        .flatMap { case (state, collector) => ref.set(state).as(collector) }
    )
  }

  override def createAndRegisterDoubleCounter(
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
      name.value.replace("_total", ""),
      help,
      commonLabels.value.keys.toIndexedSeq
    ).map { counter =>
      Counter.make(
        1.0,
        d =>
          Utils
            .modifyMetric[F, Counter.Name, PCounter.Child](counter, name, commonLabelNames, commonLabelValues, _.inc(d))
      )
    }
  }

  override def createAndRegisterLabelledDoubleCounter[A](
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
      name.value.replace("_total", ""),
      help,
      labelNames ++ commonLabels.value.keys.toIndexedSeq
    ).map { counter =>
      Counter.Labelled.make(
        1.0,
        (d, labels) =>
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

  override def createAndRegisterDoubleGauge(
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

  override def createAndRegisterLongGauge(
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Gauge[F, Long]] = createAndRegisterDoubleGauge(prefix, name, help, commonLabels).map(_.contramap(_.toDouble))

  override def createAndRegisterLabelledDoubleGauge[A](
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

  override def createAndRegisterLabelledLongGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Gauge.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleGauge(prefix, name, help, commonLabels, labelNames)(f).map(_.contramap(_.toDouble))

  override def createAndRegisterDoubleHistogram(
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

  override def createAndRegisterLabelledDoubleHistogram[A](
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
      labelNames ++ commonLabels.value.keys.toIndexedSeq
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

  override def createAndRegisterLongCounter(
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels
  ): F[Counter[F, Long]] = createAndRegisterDoubleCounter(prefix, name, help, commonLabels).map(_.contramap(_.toDouble))

  override def createAndRegisterLabelledLongCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): F[Counter.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleCounter(prefix, name, help, commonLabels, labelNames)(f).map(_.contramap(_.toDouble))

  override def createAndRegisterLongHistogram(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Long]
  ): F[Histogram[F, Long]] = createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets.map(_.toDouble))
    .map(_.contramap(_.toDouble))

  override def createAndRegisterLabelledLongHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long]
  )(f: A => IndexedSeq[String]): F[Histogram.Labelled[F, Long, A]] =
    createAndRegisterLabelledDoubleHistogram(prefix, name, help, commonLabels, labelNames, buckets.map(_.toDouble))(f)
      .map(_.contramap(_.toDouble))
}

object PrometheusMetricsRegistry {
  def default[F[_]: Async: Logger]: Resource[F, PrometheusMetricsRegistry[F]] = fromSimpleClientRegistry(
    CollectorRegistry.defaultRegistry
  )

  def fromSimpleClientRegistry[F[_]: Async: Logger](
      promRegistry: CollectorRegistry
  ): Resource[F, PrometheusMetricsRegistry[F]] = {
    val acquire = for {
      ref <- Ref.of[F, State](Map.empty)
      sem <- Semaphore[F](1L)
    } yield ref -> new PrometheusMetricsRegistry[F](promRegistry, ref, sem)

    Resource
      .make(acquire) { case (ref, _) =>
        ref.get.flatMap { metrics =>
          metrics.values
            .map(_._2)
            .toList
            .traverse_ { collector =>
              Sync[F].delay(promRegistry.unregister(collector)).handleErrorWith { e =>
                Logger[F].warn(e)(s"Failed to unregister a collector on shutdown.")
              }
            }
        }
      }
      .map(_._2)
  }
}