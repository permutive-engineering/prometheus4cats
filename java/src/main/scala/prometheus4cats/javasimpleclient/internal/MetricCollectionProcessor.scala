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
import cats.effect.kernel._
import cats.effect.kernel.syntax.monadCancel._
import cats.effect.kernel.syntax.temporal._
import cats.effect.std.Dispatcher
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.monoid._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.{Applicative, Functor, Show}
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.{
  Collector,
  CollectorRegistry,
  CounterMetricFamily,
  GaugeMetricFamily,
  SummaryMetricFamily,
  Counter => PCounter,
  Gauge => PGauge,
  Histogram => PHistogram
}
import org.typelevel.log4cats.Logger
import prometheus4cats.MetricCollection.Value
import prometheus4cats._
import prometheus4cats.javasimpleclient.models.Exceptions.DuplicateMetricsException
import prometheus4cats.javasimpleclient.{CallbackState, State}
import prometheus4cats.util.NameUtils

import scala.collection.mutable.ListBuffer
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

private[javasimpleclient] class MetricCollectionProcessor[F[_]: Async: Logger] private (
    ref: Ref[F, State],
    callbacks: Ref[F, CallbackState[F]],
    collectionCallbackRef: Ref[F, Map[Option[
      Metric.Prefix
    ], (Map[Label.Name, String], Map[Unique.Token, F[MetricCollection]])]],
    duplicates: Ref[F, Set[(Option[Metric.Prefix], String)]],
    dispatcher: Dispatcher[F],
    singleTimeout: FiniteDuration,
    combinedTimeout: FiniteDuration,
    callbackHasTimedOut: Ref[F, Boolean],
    callbackHasErrored: Ref[F, Boolean],
    singleCallbackErrored: Ref[F, (Boolean, Boolean)],
    callbackCounter: PCounter,
    singleCallbackCounter: PCounter,
    callbackTimeHistogram: PHistogram,
    duplicateGauge: PGauge
) extends Collector {

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

        regDupErr
          .guarantee(
            Sync[F]
              .delay(duplicateGauge.labels("in_registry", prefix.show).set(registryDuplicates.size.toDouble))
          )
          .as(samples.toList)
      }

    }
  }

  private def incSingleCallbackCounter(status: String) =
    Sync[F].delay(singleCallbackCounter.labels(status).inc())

  private def timeoutEach(
      collectionF: F[MetricCollection],
      hasLoggedTimeout: Boolean,
      hasLoggedError: Boolean
  ): F[(Boolean, Boolean, MetricCollection)] = {
    def incTimeout = incSingleCallbackCounter("timeout")
    def incError = incSingleCallbackCounter("error")

    collectionF
      .flatTap(_ => incSingleCallbackCounter("success"))
      .map((hasLoggedTimeout, hasLoggedError, _))
      .timeout(singleTimeout)
      .handleErrorWith {
        case th: TimeoutException =>
          (if (hasLoggedTimeout) incTimeout
           else
             Logger[F]
               .warn(th)(
                 s"Timed out running a callback for metric collection after $singleTimeout.\n" +
                   "This may be due to the callback having been registered that performs some long running calculation which blocks\n" +
                   "Please review your code or raise an issue or pull request with the library from which this callback was registered.\n" +
                   s"This warning will only be shown once after process start. The counter '${MetricCollectionProcessor.singleCallbackCounterName}'" +
                   "tracks the number of times this occurs."
               )
               .guarantee(incTimeout)).as((true, hasLoggedError, MetricCollection.empty))
        case th =>
          (if (hasLoggedError) incError
           else
             Logger[F].warn(th)(
               s"Executing a callback for metric collection failed with the following exception.\n" +
                 "Callbacks that can routinely throw exceptions are strongly discouraged as this can cause performance problems when polling metrics\n" +
                 "Please review your code or raise an issue or pull request with the library from which this callback was registered.\n" +
                 s"This warning will only be shown once after process start. The counter '${MetricCollectionProcessor.singleCallbackCounterName}'" +
                 "tracks the number of times this occurs."
             )).as(MetricCollection.empty).as((hasLoggedTimeout, true, MetricCollection.empty))
      }
  }

  private val evaluateCollections =
    singleCallbackErrored.get.flatMap { case (hasLoggedTimeout, hasLoggedError) =>
      Clock[F]
        .timed(collectionCallbackRef.get.flatMap { callbacks =>
          def allCallbacks = new GaugeMetricFamily(
            "prometheus4cats_registered_metric_collection_callbacks",
            "Number of metric collection callbacks registered in the Prometheus Java registry by Prometheus4Cats",
            callbacks.map(_._2._2.size).sum.toDouble
          )

          callbacks.toList.flatTraverse { case (prefix, (commonLabels, callbacks)) =>
            callbacks.values
              .foldM((hasLoggedTimeout, hasLoggedError, MetricCollection.empty)) {
                case ((hasLoggedTimeout0, hasLoggedError0, acc), col) =>
                  timeoutEach(col, hasLoggedTimeout0, hasLoggedError0).map { case (lto, le, col0) =>
                    (lto, le, acc |+| col0)
                  }
              }
              .flatMap { case (hasLoggedTimeout0, hasLoggedError0, col) =>
                singleCallbackErrored
                  .set(hasLoggedTimeout0, hasLoggedError0) >> convertMetrics(prefix, commonLabels, col)
                  .map(allCallbacks :: _)
              }
          }
        })
        .flatMap { case (dur, cols) =>
          Sync[F].delay(callbackTimeHistogram.observe(dur.toSeconds.toDouble)).as(cols.asJava)
        }
    }

  private def trackHasErrored[A](state: Ref[F, Boolean], onTrue: F[A], onFalse: F[A]) =
    state.modify { current =>
      if (current) (current, onTrue) else (true, onFalse)
    }.flatten

  private def incCallbackCounter(status: String) =
    Sync[F].delay(callbackCounter.labels(status).inc())

  private def timeoutCallbacks[A](fa: F[A], empty: A): A = {
    def incTimeout = incCallbackCounter("timeout")
    def incError = incCallbackCounter("error")

    Utils
      .timeoutCallback(
        dispatcher,
        combinedTimeout,
        // use flatTap to inc "success" status of the counter here, so that it will be cancelled if the operation times out or errors
        fa.flatTap(_ => incCallbackCounter("success")),
        th =>
          trackHasErrored(
            callbackHasTimedOut,
            incTimeout.as(empty),
            Logger[F]
              .warn(th)(
                s"Timed out running callback for metric collection after $combinedTimeout.\n" +
                  "This may be due to a callback having been registered that performs some long running calculation which blocks\n" +
                  "Please review your code or raise an issue or pull request with the library from which this callback was registered.\n" +
                  s"This warning will only be shown once after process start. The counter '${MetricCollectionProcessor.callbackCounterName}'" +
                  "tracks the number of times this occurs."
              )
              .guarantee(incTimeout)
              .as(empty)
          ),
        th =>
          trackHasErrored(
            callbackHasErrored,
            incError.as(empty),
            Logger[F]
              .warn(th)(
                s"Executing callbacks for metric collection failed with the following exception.\n" +
                  "Callbacks that can routinely throw exceptions are strongly discouraged as this can cause performance problems when polling metrics\n" +
                  "Please review your code or raise an issue or pull request with the library from which this callback was registered.\n" +
                  s"This warning will only be shown once after process start. The counter '${MetricCollectionProcessor.callbackCounterName}'" +
                  "tracks the number of times this occurs."
              )
              .guarantee(incError)
              .as(empty)
          )
      )
  }

  override def collect(): util.List[MetricFamilySamples] = timeoutCallbacks(
    evaluateCollections,
    List.empty[MetricFamilySamples].asJava
  )

}

private[javasimpleclient] object MetricCollectionProcessor {
  private val callbackTimerName = "prometheus4cats_collection_callback_duration"
  private val callbackTimerHelp = "Time it takes to run the metric collection callback"

  private val duplicatesGaugeName = "prometheus4cats_collection_callback_duplicates"
  private val duplicatesGaugeHelp =
    "Duplicate metrics with different labels or types detected in metric collections callbacks"
  private val duplicatesLabelNames = List("duplicate_type", "metric_prefix")

  private val callbackCounterName = "prometheus4cats_combined_collection_callback_total"
  private val callbackCounterHelp =
    "Number of times all of the metric collection callbacks have been executed, with a status (success, error, timeout)"
  private val callbackCounterLabel = "status"

  private val singleCallbackCounterName = "prometheus4cats_collection_callback_total"
  private val singleCallbackCounterHelp =
    "Number of times a metric collection callback has been executed, with a status (success, error, timeout)"

  def create[F[_]: Async: Logger](
      ref: Ref[F, State],
      callbacks: Ref[F, CallbackState[F]],
      dispatcher: Dispatcher[F],
      callbackTimeout: FiniteDuration,
      combinedCallbackTimeout: FiniteDuration,
      promRegistry: CollectorRegistry
  ): Resource[F, MetricCollectionProcessor[F]] = {
    val callbackHist = PHistogram
      .build(callbackTimerName, callbackTimerHelp)
      .buckets(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1)
      .create()

    val duplicateGauge =
      PGauge.build(duplicatesGaugeName, duplicatesGaugeHelp).labelNames(duplicatesLabelNames: _*).create()

    val allCallbacksCounter =
      PCounter.build(callbackCounterName, callbackCounterHelp).labelNames(callbackCounterLabel).create()

    val singleCallbackCounter =
      PCounter.build(singleCallbackCounterName, singleCallbackCounterHelp).labelNames(callbackCounterLabel).create()

    val acquire = for {
      _ <- Sync[F].delay(promRegistry.register(callbackHist))
      _ <- Sync[F].delay(promRegistry.register(duplicateGauge))
      _ <- Sync[F].delay(promRegistry.register(allCallbacksCounter))
      _ <- Sync[F].delay(promRegistry.register(singleCallbackCounter))
      collectionCallbackRef <- Ref.of[F, Map[Option[
        Metric.Prefix
      ], (Map[Label.Name, String], Map[Unique.Token, F[MetricCollection]])]](Map.empty)
      callbacksGauge = makeCallbacksGauge(dispatcher, collectionCallbackRef)
      _ <- Sync[F].delay(promRegistry.register(callbacksGauge))
      duplicatesRef <- Ref.of[F, Set[(Option[Metric.Prefix], String)]](Set.empty)
      callbackHasTimedOutRef <- Ref.of[F, Boolean](false)
      callbackHasErroredRef <- Ref.of[F, Boolean](false)
      singleTimeoutErrorRef <- Ref.of[F, (Boolean, Boolean)]((false, false))
      proc = new MetricCollectionProcessor(
        ref,
        callbacks,
        collectionCallbackRef,
        duplicatesRef,
        dispatcher,
        callbackTimeout,
        combinedCallbackTimeout,
        callbackHasTimedOutRef,
        callbackHasErroredRef,
        singleTimeoutErrorRef,
        allCallbacksCounter,
        singleCallbackCounter,
        callbackHist,
        duplicateGauge
      )
      _ <- Sync[F].delay(promRegistry.register(proc))
    } yield (callbacksGauge, proc)

    Resource.make(acquire) { proc =>
      Utils.unregister(callbackHist, promRegistry) >> Utils.unregister(
        duplicateGauge,
        promRegistry
      ) >> Utils.unregister(allCallbacksCounter, promRegistry) >> Utils.unregister(
        singleCallbackCounter,
        promRegistry
      ) >> Utils.unregister(proc, promRegistry)
    }
  }
}
