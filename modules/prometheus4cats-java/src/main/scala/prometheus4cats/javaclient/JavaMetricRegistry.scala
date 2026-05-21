/*
 * Copyright 2022-2026 Permutive Ltd. <https://permutive.com>
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

package prometheus4cats.javaclient

import scala.concurrent.duration._

import cats.Applicative
import cats.ApplicativeThrow
import cats.Functor
import cats.Show
import cats.data.NonEmptyList
import cats.data.NonEmptySeq
import cats.effect.kernel._
import cats.effect.std.Semaphore
import cats.syntax.all._

import io.prometheus.metrics.core.metrics.{Counter => PCounter}
import io.prometheus.metrics.core.metrics.{Gauge => PGauge}
import io.prometheus.metrics.core.metrics.{Histogram => PHistogram}
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.Labels
import prometheus4cats._
import prometheus4cats.javaclient.internal.Utils
import prometheus4cats.javaclient.models.MetricType
import prometheus4cats.util.DoubleCallbackRegistry
import prometheus4cats.util.DoubleMetricRegistry
import prometheus4cats.util.NameUtils

/** Implements [[MetricRegistry]] and [[CallbackRegistry]] against the upstream `prometheus-metrics-core` 1.x library
  * (the successor to the legacy simpleclient backend).
  *
  * This class is the v6 replacement for `prometheus4cats.javasimpleclient.JavaMetricRegistry`. Both implementations
  * coexist during the migration window.
  *
  * Construct via [[JavaMetricRegistry.Builder]].
  */
class JavaMetricRegistry[F[_]: Async] private (
    private val registry: PrometheusRegistry,
    private val ref: Ref[F, State[F]],
    private val sem: Semaphore[F],
    private val logger: Throwable => String => F[Unit]
) extends DoubleMetricRegistry[F]
    with DoubleCallbackRegistry[F] {

  type Underlying = PrometheusRegistry

  /** Returns the underlying upstream [[io.prometheus.metrics.model.registry.PrometheusRegistry]]. Use to expose metrics
    * over an HTTP endpoint or to register external collectors.
    */
  def underlying: PrometheusRegistry = registry

  override protected val F: Functor[F] = implicitly

  protected def counterName[A: Show](name: A): String = name match {
    case counter: Counter.Name => counter.value.replace("_total", "")
    case _                     => name.show
  }

  /** Common pre-registration plumbing: under the semaphore, look up an existing metric by name+labels+type. If present
    * with the same metric ID, increment its claim count and reuse. If present with a different metric ID, raise. If
    * absent, run the user-provided `register` thunk to construct & register a fresh collector and store it. On release,
    * decrement the claim count and unregister when the last claim is dropped.
    *
    * Mirrors the behaviour of the legacy `javasimpleclient` adapter — supports overlapping `Resource`-scoped
    * registrations of the same metric name without registering it twice with the underlying registry.
    */
  @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
  protected def configureBuilderOrRetrieve[M <: io.prometheus.metrics.core.metrics.StatefulMetric[_, _]](
      register: () => M,
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      stringName: String,
      labels: IndexedSeq[Label.Name]
  ): Resource[F, (M, Ref[F, Option[Exemplar.Data]])] = {
    lazy val metricId: MetricID = (labels, metricType)
    lazy val fullName: StateKey = (metricPrefix, stringName)
    lazy val renderedFullName   = NameUtils.makeName(metricPrefix, stringName)

    val acquire = sem.permit.surround(
      ref.get
        .flatMap[(State[F], (M, Ref[F, Option[Exemplar.Data]]))] { (metrics: State[F]) =>
          metrics.get(fullName) match {
            case Some((expected, (collector, exemplarRef, references))) =>
              if (metricId == expected)
                Applicative[F].pure(
                  (
                    metrics.updated(fullName, (expected, (collector, exemplarRef, references + 1))),
                    (collector.asInstanceOf[M], exemplarRef)
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
                collector   <- Sync[F].delay(register())
              } yield (metrics.updated(fullName, (metricId, (collector, exemplarRef, 1))), (collector, exemplarRef))
          }
        }
        .flatMap { case (state, pair) => ref.set(state).as(pair) }
    )

    Resource.make(acquire) { case (collector, _) =>
      sem.permit.surround {
        ref.get.flatMap { metrics =>
          metrics.get(fullName) match {
            case Some((`metricId`, (_, _, 1))) =>
              ref.set(metrics - fullName) >> Utils.unregister(collector, registry, logger)
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
    val commonLabelNames       = commonLabels.value.keys.toIndexedSeq
    val commonLabelValuesArray = commonLabels.value.values.toArray
    val allLabelNames          = labelNames ++ commonLabelNames
    val n                      = counterName(name)
    val fullName               = NameUtils.makeName(prefix, name)

    configureBuilderOrRetrieve[PCounter](
      register = () =>
        // No `.withExemplars()` — that method is inherited from the package-private
        // StatefulMetric$Builder, and exposing its return type from outside its package
        // triggers IllegalAccessError at JVM access-check time. Exemplar handling is enabled
        // by default in prometheus-metrics-core 1.x; `.withoutExemplars()` is the disable.
        PCounter
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          .register(registry),
      metricType = MetricType.Counter,
      metricPrefix = prefix,
      stringName = n,
      labels = allLabelNames
    ).map { case (counter, exemplarRef) =>
      Counter.make(
        Counter.ExemplarState.fromRef(exemplarRef),
        1.0,
        (
            d: Double,
            labels: A,
            exemplar: Option[Exemplar.Labels]
        ) =>
          Utils.modifyMetric[F, Counter.Name, io.prometheus.metrics.core.datapoints.CounterDataPoint](
            metricName = name,
            allLabelNames = allLabelNames,
            dynamicLabels = f(labels),
            commonLabelValues = commonLabelValuesArray,
            getDataPoint = (lbls: Array[String]) => counter.labelValues(lbls: _*),
            modify = (dp: io.prometheus.metrics.core.datapoints.CounterDataPoint) =>
              exemplar.fold(dp.inc(d))(e => dp.incWithExemplar(d, transformExemplarLabels(e))),
            logger = logger
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
    val commonLabelNames       = commonLabels.value.keys.toIndexedSeq
    val commonLabelValuesArray = commonLabels.value.values.toArray
    val allLabelNames          = labelNames ++ commonLabelNames
    val fullName               = NameUtils.makeName(prefix, name)

    configureBuilderOrRetrieve[PGauge](
      register = () =>
        PGauge
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          .register(registry),
      metricType = MetricType.Gauge,
      metricPrefix = prefix,
      stringName = name.value,
      labels = allLabelNames
    ).map { case (gauge, _) =>
      @inline
      def modify(g: io.prometheus.metrics.core.datapoints.GaugeDataPoint => Unit, labels: A): F[Unit] =
        Utils.modifyMetric[F, Gauge.Name, io.prometheus.metrics.core.datapoints.GaugeDataPoint](
          metricName = name,
          allLabelNames = allLabelNames,
          dynamicLabels = f(labels),
          commonLabelValues = commonLabelValuesArray,
          getDataPoint = (lbls: Array[String]) => gauge.labelValues(lbls: _*),
          modify = g,
          logger = logger
        )

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
    val commonLabelNames       = commonLabels.value.keys.toIndexedSeq
    val commonLabelValuesArray = commonLabels.value.values.toArray
    val allLabelNames          = labelNames ++ commonLabelNames
    val fullName               = NameUtils.makeName(prefix, name)

    configureBuilderOrRetrieve[PHistogram](
      register = () =>
        PHistogram
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          // .classicOnly() is required because the 1.x default emits BOTH classic AND native
          // histograms from a single declaration. Preserving v5 behaviour means only the classic
          // form is emitted from the .histogram(...) DSL path; the .nativeHistogram(...) DSL path
          // calls .nativeOnly() instead.
          .classicOnly()
          .classicUpperBounds(buckets.toList: _*)
          .register(registry),
      metricType = MetricType.Histogram,
      metricPrefix = prefix,
      stringName = name.value,
      labels = allLabelNames
    ).map { case (histogram, exemplarRef) =>
      Histogram.make[F, Double, A](
        Histogram.ExemplarState.fromRef(buckets, exemplarRef),
        _observe = { (d: Double, labels: A, exemplar: Option[Exemplar.Labels]) =>
          Utils.modifyMetric[F, Histogram.Name, io.prometheus.metrics.core.datapoints.DistributionDataPoint](
            metricName = name,
            allLabelNames = allLabelNames,
            dynamicLabels = f(labels),
            commonLabelValues = commonLabelValuesArray,
            getDataPoint = (lbls: Array[String]) => histogram.labelValues(lbls: _*),
            modify = (dp: io.prometheus.metrics.core.datapoints.DistributionDataPoint) =>
              exemplar.fold(dp.observe(d))(e => dp.observeWithExemplar(d, transformExemplarLabels(e))),
            logger = logger
          )
        }
      )
    }
  }

  override def createAndRegisterDoubleHistogramWithNative[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      config: NativeHistogram
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] = {
    val commonLabelNames       = commonLabels.value.keys.toIndexedSeq
    val commonLabelValuesArray = commonLabels.value.values.toArray
    val allLabelNames          = labelNames ++ commonLabelNames
    val fullName               = NameUtils.makeName(prefix, name)

    configureBuilderOrRetrieve[PHistogram](
      register = () => {
        // Dual-mode: NEITHER .classicOnly() NOR .nativeOnly(). Both classicUpperBounds(...) and
        // nativeInitialSchema(...) are set. The resulting Histogram emits BOTH representations,
        // letting Prometheus's `convert_classic_histograms_to_nhcb` pick the classic form for
        // server-side NHCB conversion while the native exponential is also available directly.
        val builder = PHistogram
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          .classicUpperBounds(buckets.toList: _*)
          .nativeInitialSchema(config.initialSchema)
          .nativeMaxNumberOfBuckets(config.maxNumberOfBuckets)
          .nativeMaxZeroThreshold(config.maxZeroThreshold)
          .nativeMinZeroThreshold(config.minZeroThreshold)
        val tuned =
          if (config.resetDuration > 0.seconds)
            builder.nativeResetDuration(
              config.resetDuration.toSeconds,
              java.util.concurrent.TimeUnit.SECONDS
            )
          else builder
        tuned.register(registry)
      },
      // Use a distinct MetricType so dedup is correct: registering the same metric name as
      // dual-mode and then again as classic-only is a programmer error and should fail.
      metricType = MetricType.HistogramWithNative,
      metricPrefix = prefix,
      stringName = name.value,
      labels = allLabelNames
    ).map { case (histogram, exemplarRef) =>
      Histogram.make[F, Double, A](
        Histogram.ExemplarState.fromRef(buckets, exemplarRef),
        _observe = { (d: Double, labels: A, exemplar: Option[Exemplar.Labels]) =>
          Utils.modifyMetric[F, Histogram.Name, io.prometheus.metrics.core.datapoints.DistributionDataPoint](
            metricName = name,
            allLabelNames = allLabelNames,
            dynamicLabels = f(labels),
            commonLabelValues = commonLabelValuesArray,
            getDataPoint = (lbls: Array[String]) => histogram.labelValues(lbls: _*),
            modify = (dp: io.prometheus.metrics.core.datapoints.DistributionDataPoint) =>
              exemplar.fold(dp.observe(d))(e => dp.observeWithExemplar(d, transformExemplarLabels(e))),
            logger = logger
          )
        }
      )
    }
  }

  override def createAndRegisterDoubleNativeHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      config: NativeHistogram
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] = {
    val commonLabelNames       = commonLabels.value.keys.toIndexedSeq
    val commonLabelValuesArray = commonLabels.value.values.toArray
    val allLabelNames          = labelNames ++ commonLabelNames
    val fullName               = NameUtils.makeName(prefix, name)

    configureBuilderOrRetrieve[PHistogram](
      register = () => {
        val builder = PHistogram
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          .nativeOnly()
          .nativeInitialSchema(config.initialSchema)
          .nativeMaxNumberOfBuckets(config.maxNumberOfBuckets)
          .nativeMaxZeroThreshold(config.maxZeroThreshold)
          .nativeMinZeroThreshold(config.minZeroThreshold)
        val tuned =
          if (config.resetDuration > 0.seconds)
            builder.nativeResetDuration(
              config.resetDuration.toSeconds,
              java.util.concurrent.TimeUnit.SECONDS
            )
          else builder
        tuned.register(registry)
      },
      metricType = MetricType.NativeHistogram,
      metricPrefix = prefix,
      stringName = name.value,
      labels = allLabelNames
    ).map { case (histogram, _) =>
      // Native histograms use ExemplarState.noop: the upstream Histogram still accepts exemplars via
      // observeWithExemplar(d, labels), but the bucket-driven sampler in Histogram.ExemplarState.fromRef
      // requires explicit bucket boundaries which native histograms do not have. Consumers wanting
      // sampled exemplars on a native histogram are not supported in this initial cut; explicit
      // exemplars (.observeWithExemplar) still work end-to-end.
      Histogram.make[F, Double, A](
        Histogram.ExemplarState.noop,
        _observe = { (d: Double, labels: A, exemplar: Option[Exemplar.Labels]) =>
          Utils.modifyMetric[F, Histogram.Name, io.prometheus.metrics.core.datapoints.DistributionDataPoint](
            metricName = name,
            allLabelNames = allLabelNames,
            dynamicLabels = f(labels),
            commonLabelValues = commonLabelValuesArray,
            getDataPoint = (lbls: Array[String]) => histogram.labelValues(lbls: _*),
            modify = (dp: io.prometheus.metrics.core.datapoints.DistributionDataPoint) =>
              exemplar.fold(dp.observe(d))(e => dp.observeWithExemplar(d, transformExemplarLabels(e))),
            logger = logger
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
  )(f: A => IndexedSeq[String]): Resource[F, Summary[F, Double, A]] =
    Resource.eval(ApplicativeThrow[F].raiseError(notYetPorted("createAndRegisterDoubleSummary")))

  override def createAndRegisterInfo[A](
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Info[F, A]] =
    Resource.eval(ApplicativeThrow[F].raiseError(notYetPorted("createAndRegisterInfo")))

  override def registerDoubleCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] =
    Resource.eval(ApplicativeThrow[F].raiseError(notYetPorted("registerDoubleCounterCallback")))

  override def registerDoubleGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] =
    Resource.eval(ApplicativeThrow[F].raiseError(notYetPorted("registerDoubleGaugeCallback")))

  override def registerDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      callback: F[NonEmptyList[(Histogram.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] =
    Resource.eval(ApplicativeThrow[F].raiseError(notYetPorted("registerDoubleHistogramCallback")))

  override def registerDoubleSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Summary.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] =
    Resource.eval(ApplicativeThrow[F].raiseError(notYetPorted("registerDoubleSummaryCallback")))

  override def registerMetricCollectionCallback(
      prefix: Option[Metric.Prefix],
      commonLabels: Metric.CommonLabels,
      callback: F[MetricCollection]
  ): Resource[F, Unit] =
    Resource.eval(ApplicativeThrow[F].raiseError(notYetPorted("registerMetricCollectionCallback")))

  private def notYetPorted(methodName: String): UnsupportedOperationException =
    new UnsupportedOperationException(
      s"prometheus4cats.javaclient.JavaMetricRegistry.$methodName is not yet implemented in this commit. " +
        "It will be ported in a subsequent commit before the simpleclient backend is removed."
    )

  private def transformExemplarLabels(labels: Exemplar.Labels): Labels =
    Labels.of(
      labels.value.keys.toArray.map(_.value),
      labels.value.values.toArray
    )

}

object JavaMetricRegistry {

  /** Builder for [[JavaMetricRegistry]]. Mirrors the legacy
    * `prometheus4cats.javasimpleclient.JavaMetricRegistry.Builder` API so consumers (e.g., the `permutive-metrics`
    * bridge) can migrate by changing only the import.
    *
    * Differences from the legacy Builder:
    *   - takes a [[io.prometheus.metrics.model.registry.PrometheusRegistry]] instead of `CollectorRegistry`;
    *   - JVM/process metrics are added via [[Builder.withJvmMetrics]] (which uses
    *     `prometheus-metrics-instrumentation-jvm`'s `JvmMetrics.builder().register(...)`) rather than a list of
    *     simpleclient hotspot collectors.
    */
  sealed abstract class Builder[F[_]: Async](
      val promRegistry: PrometheusRegistry,
      val callbackTimeout: FiniteDuration,
      val callbackCollectionTimeout: FiniteDuration,
      val logger: Throwable => String => F[Unit],
      val registerJvmMetrics: Boolean
  ) {

    private def copy(
        promRegistry: PrometheusRegistry = promRegistry,
        callbackTimeout: FiniteDuration = callbackTimeout,
        callbackCollectionTimeout: FiniteDuration = callbackCollectionTimeout,
        logger: Throwable => String => F[Unit] = logger,
        registerJvmMetrics: Boolean = registerJvmMetrics
    ): Builder[F] =
      new Builder(promRegistry, callbackTimeout, callbackCollectionTimeout, logger, registerJvmMetrics) {}

    def withRegistry(promRegistry: PrometheusRegistry): Builder[F] = copy(promRegistry = promRegistry)

    def withCallbackTimeout(callbackTimeout: FiniteDuration): Builder[F] =
      copy(callbackTimeout = callbackTimeout)

    def withCallbackCollectionTimeout(callbackCollectionTimeout: FiniteDuration): Builder[F] =
      copy(callbackCollectionTimeout = callbackCollectionTimeout)

    def withLogger(logger: Throwable => String => F[Unit]): Builder[F] = copy(logger = logger)

    /** Register the standard JVM/process metrics (memory pools, GC, threads, class loading, buffers) via the upstream
      * `prometheus-metrics-instrumentation-jvm` library when the registry is built. Replacement for the legacy
      * `withHotSpotCollectors` builder method.
      */
    def withJvmMetrics: Builder[F] = copy(registerJvmMetrics = true)

    def build: Resource[F, JavaMetricRegistry[F]] =
      Resource.eval {
        if (registerJvmMetrics) Sync[F].delay(JvmMetrics.builder().register(promRegistry))
        else Applicative[F].unit
      }.flatMap { _ =>
        val acquire = for {
          ref <- Ref.of[F, State[F]](Map.empty)
          sem <- Semaphore[F](1L)
        } yield new JavaMetricRegistry[F](promRegistry, ref, sem, logger)

        Resource.make(acquire) { reg =>
          // unregister all metrics that are still claimed at shutdown
          reg.ref.get.flatMap { metrics =>
            if (metrics.nonEmpty)
              metrics.values.toList.traverse_ { case (_, (collector, _, _)) =>
                Utils.unregister(collector, promRegistry, logger)
              }
            else Applicative[F].unit
          }
        }
      }

  }

  object Builder {

    def apply[F[_]: Async](): Builder[F] =
      new Builder[F](
        promRegistry = PrometheusRegistry.defaultRegistry,
        callbackTimeout = 250.millis,
        callbackCollectionTimeout = 1.second,
        logger = _ => _ => Async[F].unit,
        registerJvmMetrics = false
      ) {}

  }

}
