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

package prometheus4cats.javasimpleclient.internal

import java.util

import alleycats.std.iterable._
import alleycats.std.set._
import cats.syntax.apply._
import cats.effect.kernel._
import cats.effect.std.Dispatcher
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.{Applicative, Monoid, Show}
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.{
  Collector,
  CollectorRegistry,
  CounterMetricFamily,
  GaugeMetricFamily,
  SummaryMetricFamily,
  Gauge => PGauge,
  Histogram => PHistogram
}
import org.typelevel.log4cats.Logger
import prometheus4cats.MetricCollection.Value
import prometheus4cats._
import prometheus4cats.javasimpleclient.{CallbackState, DuplicateMetricsException, State}
import prometheus4cats.util.NameUtils

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class MetricCollectionProcessor[F[_]: Async: Logger] private (
    ref: Ref[F, State],
    callbacks: Ref[F, CallbackState[F]],
    collectionCallbackRef: Ref[F, Map[Option[
      Metric.Prefix
    ], (Map[Label.Name, String], Map[Unique.Token, F[MetricCollection]])]],
    duplicates: Ref[F, Set[(Option[Metric.Prefix], String)]],
    dispatcher: Dispatcher[F],
    callbackTimeout: FiniteDuration,
    callbackTimeHistogram: PHistogram,
    duplicateGauge: PGauge
) extends Collector {

  private def timeoutCallback[A](fa: F[A], onError: A): A =
    Utils.timeoutCallback(
      dispatcher,
      callbackTimeout,
      fa,
      onError,
      "This originated as a result of metric collection callbacks."
    )

  def register(
      prefix: Option[Metric.Prefix],
      commonLabels: Metric.CommonLabels,
      callback: F[MetricCollection]
  ): Resource[F, Unit] = {
    val acquire = Unique[F].unique.flatMap { token =>
      collectionCallbackRef
        .update(map =>
          map.updated(
            prefix,
            map.get(prefix).fold(commonLabels.value -> Map(token -> callback)) {
              case (currentCommonLabels, callbacks) =>
                (currentCommonLabels ++ commonLabels.value) -> callbacks.updated(token, callback)
            }
          )
        )
        .tupleRight(token)
    }

    Resource
      .make(acquire) { case (_, token) =>
        collectionCallbackRef.update { map =>
          map.get(prefix).fold(map) { case (commonLabels, collections) =>
            map.updated(prefix, commonLabels -> (collections - token))
          }
        }
      }
      .as(())
  }

  private def convertMetrics(
      prefix: Option[Metric.Prefix],
      commonLabels: Map[Label.Name, String],
      values: MetricCollection
  ): F[List[MetricFamilySamples]] = {
    def makeName[A: Show](n: A): String = NameUtils.makeName(prefix, n)

    val counterToSample: ((Counter.Name, IndexedSeq[Label.Name]), List[MetricCollection.Value.Counter]) => Option[
      MetricFamilySamples
    ] = { case ((name, labelNames), values) =>
      lazy val allLabelNames = (labelNames ++ commonLabels.keys).map(_.value).asJava

      values.lastOption.map { last =>
        values.foldLeft(new CounterMetricFamily(makeName(name), last.help.value, allLabelNames)) { (sample, value) =>
          val v = value match {
            case v: MetricCollection.Value.LongCounter => v.value.toDouble
            case v: MetricCollection.Value.DoubleCounter => v.value
          }

          sample.addMetric((value.labelValues.toList ++ commonLabels.values).asJava, v)
        }
      }
    }

    val gaugeToSample: ((Gauge.Name, IndexedSeq[Label.Name]), List[MetricCollection.Value.Gauge]) => Option[
      MetricFamilySamples
    ] = { case ((name, labelNames), values) =>
      lazy val allLabelNames = (labelNames ++ commonLabels.keys).map(_.value).asJava

      values.lastOption.map { last =>
        values.foldLeft(new GaugeMetricFamily(makeName(name), last.help.value, allLabelNames)) { (sample, value) =>
          val v = value match {
            case v: MetricCollection.Value.LongGauge => v.value.toDouble
            case v: MetricCollection.Value.DoubleGauge => v.value
          }

          sample.addMetric((value.labelValues.toList ++ commonLabels.values).asJava, v)
        }
      }
    }

    val histogramToSample: ((Histogram.Name, IndexedSeq[Label.Name]), List[MetricCollection.Value.Histogram]) => Option[
      MetricFamilySamples
    ] = { case ((name, labelNames), values) =>
      values.lastOption.map { last =>
        val buckets = last match {
          case v: Value.LongHistogram => v.buckets.map(_.toDouble)
          case v: Value.DoubleHistogram => v.buckets
        }

        HistogramUtils.histogramSamples(prefix, name, last.help, commonLabels, labelNames, buckets)(values.map {
          case v: Value.LongHistogram => v.value.map(_.toDouble) -> v.labelValues
          case v: Value.DoubleHistogram => v.value -> v.labelValues
        })
      }
    }

    val summaryToSample: ((Summary.Name, IndexedSeq[Label.Name]), List[MetricCollection.Value.Summary]) => Option[
      MetricFamilySamples
    ] = { case ((name, labelNames), values) =>
      lazy val allLabelNames = (labelNames ++ commonLabels.keys).map(_.value).asJava

      values.lastOption.map { last =>
        val quantiles = last match {
          case v: MetricCollection.Value.LongSummary =>
            v.value.quantiles.keys.toList.map(_.asInstanceOf[java.lang.Double]).asJava
          case v: MetricCollection.Value.DoubleSummary =>
            v.value.quantiles.keys.toList.map(_.asInstanceOf[java.lang.Double]).asJava
        }

        if (quantiles.isEmpty)
          values.foldLeft(new SummaryMetricFamily(makeName(name), last.help.value, allLabelNames)) { (sample, value) =>
            value match {
              case v: MetricCollection.Value.LongSummary =>
                sample.addMetric(
                  (value.labelValues.toList ++ commonLabels.values).asJava,
                  v.value.count.toDouble,
                  v.value.sum.toDouble
                )
              case v: MetricCollection.Value.DoubleSummary =>
                sample.addMetric(
                  (value.labelValues.toList ++ commonLabels.values).asJava,
                  v.value.count,
                  v.value.sum
                )
            }

          }
        else
          values.foldLeft(new SummaryMetricFamily(makeName(name), last.help.value, allLabelNames, quantiles)) {
            (sample, value) =>
              value match {
                case v: MetricCollection.Value.LongSummary =>
                  sample.addMetric(
                    (value.labelValues.toList ++ commonLabels.values).asJava,
                    v.value.count.toDouble,
                    v.value.sum.toDouble,
                    v.value.quantiles.values.toList.map(_.toDouble.asInstanceOf[java.lang.Double]).asJava
                  )
                case v: MetricCollection.Value.DoubleSummary =>
                  sample.addMetric(
                    (value.labelValues.toList ++ commonLabels.values).asJava,
                    v.value.count,
                    v.value.sum,
                    v.value.quantiles.values.toList.map(_.asInstanceOf[java.lang.Double]).asJava
                  )
              }
          }
      }
    }

    (ref.get, callbacks.get).flatMapN { (state, callbackState) =>
      def collectMetrics[A: Show, B](
          vs: Map[(A, IndexedSeq[Label.Name]), B],
          f: ((A, IndexedSeq[Label.Name]), B) => Option[MetricFamilySamples]
      ): (
          Set[(Option[Metric.Prefix], String)],
          Set[(Option[Metric.Prefix], String)],
          ListBuffer[MetricFamilySamples]
      ) => (
          Set[(Option[Metric.Prefix], String)],
          Set[(Option[Metric.Prefix], String)],
          ListBuffer[MetricFamilySamples]
      ) = { (names, registryDuplicates, listBuffer) =>
        vs.foldLeft((names, registryDuplicates, listBuffer)) {
          case ((names, registryDuplicates, samples), (x @ (name, _), v)) =>
            val nameString = name.show

            if (state.contains(prefix -> nameString) || callbackState.contains(prefix -> nameString))
              (names, registryDuplicates + (prefix -> nameString), samples)
            else (names + (prefix -> nameString), registryDuplicates, samples ++ f(x, v))
        }
      }

      val (_, registryDuplicates, samples) = List(
        collectMetrics(values.counters, counterToSample),
        collectMetrics(values.gauges, gaugeToSample),
        collectMetrics(values.histograms, histogramToSample),
        collectMetrics(values.summaries, summaryToSample)
      ).foldLeft(
        (
          Set.empty[(Option[Metric.Prefix], String)],
          Set.empty[(Option[Metric.Prefix], String)],
          ListBuffer.empty[MetricFamilySamples]
        )
      ) { case ((names, registryDuplicates, samples), f) =>
        f(names, registryDuplicates, samples)
      }

      if (registryDuplicates.isEmpty) Applicative[F].pure(samples.toList)
      else {
        lazy val duplicatesInRegistry =
          s"The following metrics or callbacks with the same name are already registered in the metrics registry and therefore have been excluded from the provided collection: '${registryDuplicates
              .mkString_(",")}'. " +
            "Because of this the metric(s) in the given collection will be ignored in favour of those already registered\n" +
            "To mitigate this, you can do the following to the metrics collection:\n" +
            "  - modify the metric name to disambiguate metric types and/or label sets\n"

        val regDupErr =
          if (registryDuplicates.nonEmpty)
            duplicates.modify { current =>
              if (current != registryDuplicates)
                registryDuplicates -> Logger[F].warn(DuplicateMetricsException(registryDuplicates))(
                  duplicatesInRegistry
                )
              else current -> Applicative[F].unit
            }.flatten
          else Applicative[F].unit

        regDupErr >> Sync[F]
          .delay(duplicateGauge.labels("in_registry", prefix.show).set(registryDuplicates.size.toDouble))
          .as(samples.toList)
      }

    }
  }

  private val evaluateCollections = Clock[F]
    .timed(collectionCallbackRef.get.flatMap { callbacks =>
      callbacks.toList.flatTraverse { case (prefix, (commonLabels, callbacks)) =>
        callbacks.values.sequence
          .flatMap(collections =>
            convertMetrics(prefix, commonLabels, Monoid[MetricCollection].combineAll(collections))
          )
      }
    })
    .flatMap { case (dur, cols) =>
      Sync[F].delay(callbackTimeHistogram.observe(dur.toSeconds.toDouble)).as(cols.asJava)
    }

  override def collect(): util.List[MetricFamilySamples] = timeoutCallback(
    evaluateCollections,
    List.empty[MetricFamilySamples].asJava
  )

}

object MetricCollectionProcessor {
  private val callbackTimerName = "prometheus4cats_collection_callback_duration"
  private val callbackTimerHelp = "Time it takes to run the metric collection callback"

  private val duplicatesGaugeName = "prometheus4cats_collection_callback_duplicates"
  private val duplicatesGaugeHelp =
    "Duplicate metrics with different labels or types detected in metric collections callbacks"
  private val duplicatesLabelNames = List("duplicate_type", "metric_prefix")

  def create[F[_]: Async: Logger](
      ref: Ref[F, State],
      callbacks: Ref[F, CallbackState[F]],
      dispatcher: Dispatcher[F],
      callbackTimeout: FiniteDuration,
      promRegistry: CollectorRegistry
  ): Resource[F, MetricCollectionProcessor[F]] = {
    val callbackHist = PHistogram
      .build(callbackTimerName, callbackTimerHelp)
      .buckets(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1)
      .create()

    val duplicateGauge =
      PGauge.build(duplicatesGaugeName, duplicatesGaugeHelp).labelNames(duplicatesLabelNames: _*).create()

    val acquire = for {
      _ <- Sync[F].delay(promRegistry.register(callbackHist))
      _ <- Sync[F].delay(promRegistry.register(duplicateGauge))
      collectionCallbackRef <- Ref.of[F, Map[Option[
        Metric.Prefix
      ], (Map[Label.Name, String], Map[Unique.Token, F[MetricCollection]])]](Map.empty)
      duplicatesRef <- Ref.of[F, Set[(Option[Metric.Prefix], String)]](Set.empty)
      proc = new MetricCollectionProcessor(
        ref,
        callbacks,
        collectionCallbackRef,
        duplicatesRef,
        dispatcher,
        callbackTimeout,
        callbackHist,
        duplicateGauge
      )
      _ <- Sync[F].delay(promRegistry.register(proc))
    } yield proc

    Resource.make(acquire) { proc =>
      Utils.unregister(callbackHist, promRegistry) >> Utils.unregister(
        duplicateGauge,
        promRegistry
      ) >> Utils.unregister(proc, promRegistry)
    }
  }
}
