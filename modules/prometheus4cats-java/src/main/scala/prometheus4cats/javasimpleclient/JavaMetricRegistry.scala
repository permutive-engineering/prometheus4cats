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

import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.Applicative
import cats.ApplicativeThrow
import cats.Functor
import cats.Monad
import cats.Show
import cats.data.NonEmptyList
import cats.data.NonEmptySeq
import cats.effect.kernel._
import cats.effect.kernel.syntax.monadCancel._
import cats.effect.kernel.syntax.temporal._
import cats.effect.std.Dispatcher
import cats.effect.std.Semaphore
import cats.syntax.all._

import alleycats.std.iterable._
import io.prometheus.client.Collector
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.CounterMetricFamily
import io.prometheus.client.GaugeMetricFamily
import io.prometheus.client.SimpleCollector
import io.prometheus.client.SummaryMetricFamily
import io.prometheus.client.{Counter => PCounter}
import io.prometheus.client.{Gauge => PGauge}
import io.prometheus.client.{Histogram => PHistogram}
import io.prometheus.client.{Info => PInfo}
import io.prometheus.client.{Summary => PSummary}
import org.typelevel.log4cats.Logger
import prometheus4cats._
import prometheus4cats.javasimpleclient.internal.HistogramUtils
import prometheus4cats.javasimpleclient.internal.MetricCollectionProcessor
import prometheus4cats.javasimpleclient.internal.Utils
import prometheus4cats.javasimpleclient.models.MetricType
import prometheus4cats.util.DoubleCallbackRegistry
import prometheus4cats.util.DoubleMetricRegistry
import prometheus4cats.util.NameUtils

@SuppressWarnings(Array("all"))
class JavaMetricRegistry[F[_]: Async: Logger] private (
    private val registry: CollectorRegistry,
    private val ref: Ref[F, State[F]],
    private val callbackState: Ref[F, CallbackState[F]],
    private val callbackTimeoutState: Ref[F, Set[String]],
    private val callbackErrorState: Ref[F, Set[String]],
    private val singleCallbackErrorState: Ref[F, (Set[String], Set[String])],
    private val callbackCounter: PCounter,
    private val singleCallbackCounter: PCounter,
    private val metricCollectionCollector: MetricCollectionProcessor[F],
    private val sem: Semaphore[F],
    private val dispatcher: Dispatcher[F],
    private val callbackTimeout: FiniteDuration,
    private val singleCallbackTimeout: FiniteDuration
) extends DoubleMetricRegistry[F]
    with DoubleCallbackRegistry[F] {

  override protected val F: Functor[F] = implicitly

  protected def counterName[A: Show](name: A): String = name match {
    case counter: Counter.Name => counter.value.replace("_total", "")
    case _                     => name.show
  }

  protected def configureBuilderOrRetrieve[A: Show, B <: SimpleCollector.Builder[B, C], C <: SimpleCollector[_]](
      builder: SimpleCollector.Builder[B, C],
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      name: A,
      help: Metric.Help,
      labels: IndexedSeq[Label.Name],
      modifyBuilder: Option[B => B] = None
  ): Resource[F, C] =
    configureBuilderOrRetrieveExemplar(builder, metricType, metricPrefix, name, help, labels, modifyBuilder).map(_._1)

  protected def configureBuilderOrRetrieveExemplar[
      A: Show,
      B <: SimpleCollector.Builder[B, C],
      C <: SimpleCollector[_]
  ](
      builder: SimpleCollector.Builder[B, C],
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      name: A,
      help: Metric.Help,
      labels: IndexedSeq[Label.Name],
      modifyBuilder: Option[B => B] = None
  ): Resource[F, (C, Ref[F, Option[Exemplar.Data]])] = {
    lazy val n = counterName(name)

    lazy val metricId: MetricID = (labels, metricType)
    lazy val fullName: StateKey = (metricPrefix, n)
    lazy val renderedFullName   = NameUtils.makeName(metricPrefix, name)

    // the semaphore is needed here because `update` can't be used on the Ref, due to creation of the collector
    // possibly throwing and therefore needing to be wrapped in a `Sync.delay`. This would be fine, but the actual
    // state must be pure and the collector is needed for that.
    val acquire = sem.permit.surround(
      callbackState.get.flatMap { st =>
        st.get(fullName) match {
          case None => Applicative[F].unit
          case Some(_) =>
            ApplicativeThrow[F].raiseError[Unit](
              new RuntimeException(
                s"A callback with the same name as '$renderedFullName' is already registered with different labels and/or type"
              )
            )
        }
      } >>
        ref.get
          .flatMap[(State[F], (C, Ref[F, Option[Exemplar.Data]]))] { (metrics: State[F]) =>
            metrics.get(fullName) match {
              case Some((expected, (collector, exemplarRef, references))) =>
                if (metricId == expected)
                  Applicative[F].pure(
                    (
                      metrics.updated(fullName, (expected, (collector, exemplarRef, references + 1))),
                      (
                        collector
                          .asInstanceOf[C],
                        exemplarRef
                      )
                    )
                  )
                else
                  ApplicativeThrow[F].raiseError(
                    new RuntimeException(
                      s"A metric with the same name as '$renderedFullName' is already registered with different labels and/or type"
                    )
                  )
              case None =>
                for {
                  exemplarRef <- Ref.of[F, Option[Exemplar.Data]](None)
                  collector <- Sync[F].delay {
                                 val b: B =
                                   builder
                                     .name(NameUtils.makeName(metricPrefix, name))
                                     .help(help.value)
                                     .labelNames(labels.map(_.value): _*)

                                 modifyBuilder.foreach(f => f(b))

                                 b.register(registry)
                               }
                } yield (metrics.updated(fullName, (metricId, (collector, exemplarRef, 1))), (collector, exemplarRef))
            }
          }
          .flatMap { case (state, collector) => ref.set(state).as(collector) }
    )

    Resource.make(acquire) { case (collector, _) =>
      sem.permit.surround {
        ref.get.flatMap { metrics =>
          metrics.get(fullName) match {
            case Some((`metricId`, (_, _, 1))) =>
              ref.set(metrics - fullName) >> Utils.unregister(collector, registry)
            case Some((`metricId`, (collector, exemplarRef, references))) =>
              ref.set(metrics.updated(fullName, (metricId, (collector, exemplarRef, references - 1))))
            case _ => Applicative[F].unit
          }
        }
      }
    }
  }

  override def createAndRegisterDoubleCounter[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Counter[F, Double, A]] = {
    val commonLabelNames  = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieveExemplar(
      PCounter.build().withExemplars(),
      MetricType.Counter,
      prefix,
      name,
      help,
      labelNames ++ commonLabelNames
    ).map { case (counter, exemplarRef) =>
      Counter.make(
        Counter.ExemplarState.fromRef(exemplarRef),
        1.0,
        (
            d: Double,
            labels: A,
            exemplar: Option[Exemplar.Labels]
        ) =>
          Utils.modifyMetric[F, Counter.Name, PCounter.Child](
            counter,
            name,
            labelNames ++ commonLabelNames,
            f(labels) ++ commonLabelValues,
            c => exemplar.fold(c.inc(d))(e => c.incWithExemplar(d, transformExemplarLabels(e)))
          )
      )
    }
  }

  override def createAndRegisterDoubleGauge[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Gauge[F, Double, A]] = {
    val commonLabelNames  = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieve(
      PGauge.build(),
      MetricType.Gauge,
      prefix,
      name,
      help,
      labelNames ++ commonLabelNames
    ).map { gauge =>
      @inline
      def modify(g: PGauge.Child => Unit, labels: A): F[Unit] =
        Utils.modifyMetric(gauge, name, labelNames ++ commonLabelNames, f(labels) ++ commonLabelValues, g)

      def inc(n: Double, labels: A): F[Unit] = modify(_.inc(n), labels)

      def dec(n: Double, labels: A): F[Unit] = modify(_.dec(n), labels)

      def set(n: Double, labels: A): F[Unit] = modify(_.set(n), labels)

      Gauge.make(inc, dec, set)
    }
  }

  override def createAndRegisterDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] = {
    val commonLabelNames  = commonLabels.value.keys.toIndexedSeq
    val commonLabelValues = commonLabels.value.values.toIndexedSeq

    configureBuilderOrRetrieveExemplar(
      PHistogram.build().withExemplars().buckets(buckets.toSeq: _*),
      MetricType.Histogram,
      prefix,
      name,
      help,
      labelNames ++ commonLabelNames
    ).map { case (histogram, exemplarRef) =>
      Histogram.make[F, Double, A](
        Histogram.ExemplarState.fromRef(buckets, exemplarRef),
        _observe = { (d: Double, labels: A, exemplar: Option[Exemplar.Labels]) =>
          Utils.modifyMetric[F, Histogram.Name, PHistogram.Child](
            histogram,
            name,
            labelNames ++ commonLabelNames,
            f(labels) ++ commonLabelValues,
            h => exemplar.fold(h.observe(d))(e => h.observeWithExemplar(d, transformExemplarLabels(e)))
          )
        }
      )
    }
  }

  override def createAndRegisterDoubleSummary[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      quantiles: Seq[Summary.QuantileDefinition],
      maxAge: FiniteDuration,
      ageBuckets: Summary.AgeBuckets
  )(f: A => IndexedSeq[String]): Resource[F, Summary[F, Double, A]] = {

    val commonLabelNames  = commonLabels.value.keys.toIndexedSeq
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
      Summary.make[F, Double, A] { case (d, labels) =>
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

  override def createAndRegisterInfo(
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help
  ): Resource[F, Info[F, Map[Label.Name, String]]] =
    configureBuilderOrRetrieve(
      PInfo.build(), MetricType.Info, prefix, name, help, IndexedSeq.empty
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

  private def trackErrors[A](state: Ref[F, Set[String]], stringName: String, onContains: F[A], onContainsNot: F[A]) =
    state.modify { current =>
      if (current.contains(stringName)) (current, onContains) else (current + stringName, onContainsNot)
    }.flatten

  private def incCallbackCounter(stringName: String, status: String) =
    Sync[F].delay(callbackCounter.labels(stringName, status).inc())

  private def timeoutCallbacks[A](fa: F[A], empty: A, stringName: String): A = {
    def incTimeout = incCallbackCounter(stringName, "timeout")
    def incError   = incCallbackCounter(stringName, "error")

    Utils.timeoutCallback(
      dispatcher,
      callbackTimeout,
      // use flatTap to inc "success" status of the counter here, so that it will be cancelled if the operation times out or errors
      fa.flatTap(_ => incCallbackCounter(stringName, "success")),
      th =>
        trackErrors(
          callbackTimeoutState,
          stringName,
          incTimeout.as(empty),
          Logger[F]
            .warn(th)(
              s"Timed out running callbacks for metric '$stringName' after $callbackTimeout.\n" +
                "This may be due to a callback having been registered that performs some long running calculation which blocks\n" +
                "Please review your code or raise an issue or pull request with the library from which this callback was registered.\n" +
                s"This warning will only be shown once for each metric after process start. The counter '${JavaMetricRegistry.callbacksCounterName}'" +
                "tracks the number of times this occurs per metric name."
            )
            .guarantee(incTimeout)
            .as(empty)
        ),
      th =>
        trackErrors(
          callbackErrorState,
          stringName,
          incError.as(empty),
          Logger[F]
            .warn(th)(
              s"Callbacks for metric '$stringName' failed with the following exception.\n" +
                "Callbacks that can routinely throw exceptions are strongly discouraged as this can cause performance problems when polling metrics\n" +
                "Please review your code or raise an issue or pull request with the library from which this callback was registered.\n" +
                s"This warning will only be shown once for each metric after process start. The counter '${JavaMetricRegistry.callbacksCounterName}'" +
                "tracks the number of times this occurs per metric name."
            )
            .guarantee(incError)
            .as(empty)
        )
    )
  }

  private def incSingleCallbackCounter(name: String, status: String) =
    Sync[F].delay(singleCallbackCounter.labels(name, status).inc())

  private def timeoutEach(
      stringName: String,
      samplesF: F[NonEmptyList[Collector.MetricFamilySamples]],
      hasLoggedTimeout: Boolean,
      hasLoggedError: Boolean
  ): F[(Boolean, Boolean, List[Collector.MetricFamilySamples])] = {
    def incTimeout = incSingleCallbackCounter(stringName, "timeout")
    def incError   = incSingleCallbackCounter(stringName, "error")

    samplesF
      .flatTap(_ => incSingleCallbackCounter(stringName, "success"))
      .map(samples => (hasLoggedTimeout, hasLoggedError, samples.toList))
      .timeout(singleCallbackTimeout)
      .handleErrorWith {
        case th: TimeoutException =>
          (if (hasLoggedTimeout) incTimeout
           else
             Logger[F]
               .warn(th)(
                 s"Timed out running a callback for the metric '$stringName' after $singleCallbackTimeout.\n" +
                   "This may be due to the callback having been registered that performs some long running calculation which blocks\n" +
                   "Please review your code or raise an issue or pull request with the library from which this callback was registered.\n" +
                   s"This warning will only be shown once after process start. The counter '${JavaMetricRegistry.callbackCounterName}'" +
                   "tracks the number of times this occurs."
               )
               .guarantee(incTimeout)).as((true, hasLoggedError, List.empty[Collector.MetricFamilySamples]))
        case th =>
          (if (hasLoggedError) incError
           else
             Logger[F]
               .warn(th)(
                 s"Executing a callback for the metric '$stringName' failed with the following exception.\n" +
                   "Callbacks that can routinely throw exceptions are strongly discouraged as this can cause performance problems when polling metrics\n" +
                   "Please review your code or raise an issue or pull request with the library from which this callback was registered.\n" +
                   s"This warning will only be shown once after process start. The counter '${JavaMetricRegistry.callbackCounterName}'" +
                   "tracks the number of times this occurs."
               )
               .guarantee(incError)).as((hasLoggedTimeout, true, List.empty[Collector.MetricFamilySamples]))
      }
  }

  private def registerCallback[A: Show](
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      name: A,
      callback: F[NonEmptyList[Collector.MetricFamilySamples]]
  ): Resource[F, Unit] = {
    lazy val n = counterName(name)

    lazy val fullName: StateKey = (metricPrefix, n)
    lazy val renderedFullName   = NameUtils.makeName(metricPrefix, name)

    def makeCollector(callbacks: Ref[F, Map[Unique.Token, F[NonEmptyList[Collector.MetricFamilySamples]]]]): Collector =
      new Collector {

        private val result = singleCallbackErrorState.get.flatMap { case (loggedTimeout, loggedError) =>
          callbacks.get
            .flatMap(
              _.values.foldM(
                (
                  loggedTimeout.contains(renderedFullName),
                  loggedError.contains(renderedFullName),
                  List.empty[Collector.MetricFamilySamples]
                )
              ) { case ((hasLoggedTimeout0, hasLoggedError0, acc), samplesF) =>
                timeoutEach(renderedFullName, samplesF, hasLoggedTimeout0, hasLoggedError0).map {
                  case (lto, le, samples) =>
                    (lto, le, acc |+| samples)
                }

              }
            )
            .flatMap { case (hasLoggedTimeout0, hasLoggedError0, samples) =>
              ((hasLoggedTimeout0, hasLoggedError0) match {
                case (true, true) =>
                  singleCallbackErrorState.set((loggedTimeout + renderedFullName, loggedError + renderedFullName))
                case (true, false)  => singleCallbackErrorState.set((loggedTimeout + renderedFullName, loggedError))
                case (false, true)  => singleCallbackErrorState.set((loggedTimeout, loggedError + renderedFullName))
                case (false, false) => Applicative[F].unit
              }).as(samples.asJava)

            }

        }

        override def collect(): util.List[MetricFamilySamples] =
          timeoutCallbacks(
            result,
            util.Collections.emptyList[Collector.MetricFamilySamples](),
            n
          )

      }

    val acquire = sem.permit.surround(
      ref.get.flatMap(r =>
        r.get(fullName) match {
          case None => Applicative[F].unit
          case Some(_) =>
            ApplicativeThrow[F].raiseError[Unit](
              new RuntimeException(
                s"A metric with the same name as '$renderedFullName' is already registered with different labels and/or type"
              )
            )
        }
      ) >>
        callbackState.get
          .flatMap[Unique.Token] { (callbacks: CallbackState[F]) =>
            callbacks.get(fullName) match {

              case Some((`metricType`, states, _)) =>
                Unique[F].unique.flatMap { token =>
                  states.update(_.updated(token, callback)).as(token)
                }
              case Some(_) =>
                ApplicativeThrow[F].raiseError(
                  new RuntimeException(
                    s"A callback with the same name as '$renderedFullName' is already registered with different type"
                  )
                )
              case None =>
                for {
                  token <- Unique[F].unique
                  ref <-
                    Ref
                      .of[F, Map[Unique.Token, F[NonEmptyList[Collector.MetricFamilySamples]]]](Map(token -> callback))
                  collector = makeCollector(ref)
                  _        <- Sync[F].delay(registry.register(collector))
                  _        <- callbackState.set(callbacks.updated(fullName, (metricType, ref, collector)))
                } yield token
            }

          }
    )

    Resource
      .make(acquire) { token =>
        sem.permit.surround(callbackState.get.flatMap { state =>
          state.get(fullName) match {
            case Some((_, callbacks, collector)) =>
              callbacks.get.flatMap { cbs =>
                val newCallbacks = cbs - token

                if (newCallbacks.isEmpty)
                  callbackState.set(state - fullName) >> Utils.unregister(collector, registry)
                else callbacks.set(newCallbacks)
              }
            case None => Applicative[F].unit
          }
        })
      }
      .void
  }

  private def register[A: Show, B, C](
      metricType: MetricType,
      prefix: Option[Metric.Prefix],
      name: A,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(B, C)]]
  )(
      f: C => IndexedSeq[String],
      makeFamily: (String, List[String], List[String], B) => Collector.MetricFamilySamples
  ): Resource[F, Unit] = {
    lazy val stringName = NameUtils.makeName(prefix, name)

    lazy val commonLabelNames: List[String] =
      (labelNames ++ commonLabels.value.keys.toIndexedSeq).map(_.value).toList
    lazy val commonLabelValues: IndexedSeq[String] = commonLabels.value.values.toIndexedSeq

    val samples: F[NonEmptyList[MetricFamilySamples]] = callback.map(_.map { case (value, labels) =>
      makeFamily(stringName, commonLabelNames, (f(labels) ++ commonLabelValues).toList, value)
    })

    registerCallback(metricType, prefix, name, samples)
  }

  override def registerDoubleCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] =
    register(MetricType.Counter, prefix, name, commonLabels, labelNames, callback)(
      f,
      (n, lns, lvs, v) => new CounterMetricFamily(n, help.value, lns.asJava).addMetric(lvs.asJava, if (v < 0) 0 else v)
    )

  override def registerDoubleGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] =
    register(MetricType.Gauge, prefix, name, commonLabels, labelNames, callback)(
      f,
      (n, lns, lvs, v) => new GaugeMetricFamily(n, help.value, lns.asJava).addMetric(lvs.asJava, v)
    )

  override def registerDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      callback: F[NonEmptyList[(Histogram.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = {
    val makeSamples = HistogramUtils.labelledHistogramSamples(help, buckets)

    register(
      MetricType.Histogram, prefix, name, commonLabels, labelNames, callback
    )(f, makeSamples)
  }

  override def registerDoubleSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Summary.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] =
    register(MetricType.Summary, prefix, name, commonLabels, labelNames, callback)(
      f,
      (n, lns, lvs, v) =>
        if (v.quantiles.isEmpty)
          new SummaryMetricFamily(n, help.value, lns.asJava).addMetric(lvs.asJava, v.count, v.sum)
        else
          new SummaryMetricFamily(
            n,
            help.value,
            lns.asJava,
            v.quantiles.keys.toList.map(_.asInstanceOf[java.lang.Double]).asJava
          )
            .addMetric(
              lvs.asJava,
              v.count,
              v.sum,
              v.quantiles.values.toList.map(_.asInstanceOf[java.lang.Double]).asJava
            )
    )

  override def registerMetricCollectionCallback(
      prefix: Option[Metric.Prefix],
      commonLabels: Metric.CommonLabels,
      callback: F[MetricCollection]
  ): Resource[F, Unit] = metricCollectionCollector.register(prefix, commonLabels, callback)

  private def transformExemplarLabels(labels: Exemplar.Labels): util.Map[String, String] = labels.value.map {
    case (k, v) => k.value -> v
  }.asJava

}

@SuppressWarnings(Array("all"))
object JavaMetricRegistry {

  private val callbacksCounterName = "prometheus4cats_combined_callback_metric_total"

  private val callbacksCounterHelp =
    "Number of times all callbacks for a metric have been executed, with a status (success, error, timeout)"

  private val callbackCounterName = "prometheus4cats_callback_total"

  private val callbackCounterHelp =
    "Number of times each metric callback has been executed, with a status (success, error, timeout)"

  private val callbackCounterLabels = List("metric_name", "status")

  sealed abstract class Builder(
      val promRegistry: CollectorRegistry,
      val callbackTimeout: FiniteDuration,
      val callbackCollectionTimeout: FiniteDuration
  ) {

    private def copy(
        promRegistry: CollectorRegistry = promRegistry,
        callbackTimeout: FiniteDuration = callbackTimeout,
        callbackCollectionTimeout: FiniteDuration = callbackCollectionTimeout
    ): Builder = new Builder(promRegistry, callbackTimeout, callbackCollectionTimeout) {}

    def withRegistry(promRegistry: CollectorRegistry): Builder = copy(promRegistry = promRegistry)

    def withCallbackTimeout(callbackTimeout: FiniteDuration): Builder =
      copy(callbackTimeout = callbackTimeout)

    def withCallbackCollectionTimeout(callbackCollectionTimeout: FiniteDuration): Builder =
      copy(callbackCollectionTimeout = callbackCollectionTimeout)

    def build[F[_]: Async: Logger]: Resource[F, JavaMetricRegistry[F]] =
      Dispatcher.sequential[F].flatMap { dis =>
        val callbacksCounter =
          PCounter
            .build(callbacksCounterName, callbackCounterHelp)
            .labelNames(callbackCounterLabels: _*)
            .create()

        val singleCallbackCounter =
          PCounter
            .build(callbackCounterName, callbacksCounterHelp)
            .labelNames(callbackCounterLabels: _*)
            .create()

        val acquire = for {
          ref                       <- Ref.of[F, State[F]](Map.empty)
          metricsGauge               = makeMetricsGauge(dis, ref)
          _                         <- Sync[F].delay(promRegistry.register(metricsGauge))
          callbackState             <- Ref.of[F, CallbackState[F]](Map.empty)
          callbacksGauge             = makeCallbacksGauge(dis, callbackState)
          _                         <- Sync[F].delay(promRegistry.register(callbacksGauge))
          callbackTimeoutState      <- Ref.of[F, Set[String]](Set.empty)
          callbackErrorState        <- Ref.of[F, Set[String]](Set.empty)
          singleCallbacksErrorState <- Ref.of[F, (Set[String], Set[String])]((Set.empty, Set.empty))
          _                         <- Sync[F].delay(promRegistry.register(callbacksCounter))
          _                         <- Sync[F].delay(promRegistry.register(singleCallbackCounter))
          sem                       <- Semaphore[F](1L)
          metricCollectionProcessor <-
            MetricCollectionProcessor
              .create(
                ref, callbackState, dis, callbackTimeout, callbackCollectionTimeout, promRegistry
              )
              .allocated
        } yield (
          ref,
          metricsGauge,
          callbacksGauge,
          metricCollectionProcessor._2,
          new JavaMetricRegistry[F](
            promRegistry, ref, callbackState, callbackTimeoutState, callbackErrorState, singleCallbacksErrorState,
            callbacksCounter, singleCallbackCounter, metricCollectionProcessor._1, sem, dis, callbackCollectionTimeout,
            callbackTimeout
          )
        )

        Resource
          .make(acquire) { case (ref, metricsGauge, callbacksGauge, procRelease, _) =>
            Utils.unregister(metricsGauge, promRegistry) >> Utils.unregister(callbacksGauge, promRegistry) >> Utils
              .unregister(callbacksCounter, promRegistry) >> Utils
              .unregister(singleCallbackCounter, promRegistry) >> procRelease >>
              ref.get.flatMap { metrics =>
                if (metrics.nonEmpty)
                  metrics.values
                    .map(_._2)
                    .toList
                    .traverse_ { case (collector, _, _) =>
                      Utils.unregister(collector, promRegistry)
                    }
                else Applicative[F].unit
              }
          }
          .map(_._5)
      }

  }

  object Builder {

    def apply(): Builder = new Builder(CollectorRegistry.defaultRegistry, 250.millis, 1.second) {}

  }

  private def makeCallbacksGauge[F[_]: Monad](dis: Dispatcher[F], state: Ref[F, CallbackState[F]]) = new Collector {

    override def collect(): util.List[MetricFamilySamples] = dis.unsafeRunSync(
      state.get.flatMap { s =>
        val allCallbacks = new GaugeMetricFamily(
          "prometheus4cats_registered_callback_metrics",
          "Number of callback metrics registered in the Prometheus Java registry by Prometheus4Cats",
          s.size.toDouble
        )

        s.toList
          .foldM(
            new GaugeMetricFamily(
              "prometheus4cats_registered_callbacks_per_metric",
              "Number of callbacks per metric callback registered with the Prometheus4Cats Java registry",
              List("metric_name", "metric_type").asJava
            )
          ) { case (gauge, ((prefix, name), (metricType, callbacks, _))) =>
            callbacks.get.map { cbs =>
              gauge.addMetric(List(NameUtils.makeName(prefix, name), metricType.toString).asJava, cbs.size.toDouble)
            }
          }
          .map(List[MetricFamilySamples](_, allCallbacks).asJava)
      }
    )

  }

  private def makeMetricsGauge[F[_]: Monad](dis: Dispatcher[F], state: Ref[F, State[F]]) = new Collector {

    override def collect(): util.List[MetricFamilySamples] = dis.unsafeRunSync(state.get.map { s =>
      val allMetrics = new GaugeMetricFamily(
        "prometheus4cats_registered_metrics",
        "Number of metrics registered in the Prometheus Java registry by Prometheus4Cats",
        s.size.toDouble
      )

      val claims = s.toList.foldLeft(
        new GaugeMetricFamily(
          "prometheus4cats_registered_metric_claims",
          "Number of claims on each metric registered in the Prometheus Java registry by Prometheus4Cats",
          List("metric_name", "metric_type").asJava
        )
      ) { case (gauge, ((prefix, name), ((_, metricType), (_, _, claims)))) =>
        gauge.addMetric(List(NameUtils.makeName(prefix, name), metricType.toString).asJava, claims.toDouble)
      }

      List[MetricFamilySamples](allMetrics, claims).asJava
    })

  }

}
