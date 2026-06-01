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

import java.util.concurrent.TimeoutException

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.Applicative
import cats.ApplicativeThrow
import cats.Functor
import cats.Show
import cats.data.NonEmptyList
import cats.data.NonEmptySeq
import cats.effect.kernel._
import cats.effect.kernel.syntax.temporal._
import cats.effect.std.Dispatcher
import cats.effect.std.Semaphore
import cats.syntax.all._

import alleycats.std.iterable._
import io.prometheus.metrics.core.datapoints.CounterDataPoint
import io.prometheus.metrics.core.datapoints.DistributionDataPoint
import io.prometheus.metrics.core.datapoints.GaugeDataPoint
import io.prometheus.metrics.core.metrics.{Counter => PCounter}
import io.prometheus.metrics.core.metrics.{Gauge => PGauge}
import io.prometheus.metrics.core.metrics.{Histogram => PHistogram}
import io.prometheus.metrics.core.metrics.{Info => PInfo}
import io.prometheus.metrics.core.metrics.{Summary => PSummary}
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.registry.{Collector => PCollector}
import io.prometheus.metrics.model.registry.{MultiCollector => PMultiCollector}
import io.prometheus.metrics.model.snapshots.ClassicHistogramBuckets
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.DataPointSnapshot
import io.prometheus.metrics.model.snapshots.Exemplars
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot
import io.prometheus.metrics.model.snapshots.Labels
import io.prometheus.metrics.model.snapshots.MetricMetadata
import io.prometheus.metrics.model.snapshots.MetricSnapshot
import io.prometheus.metrics.model.snapshots.MetricSnapshots
import io.prometheus.metrics.model.snapshots.Quantiles
import io.prometheus.metrics.model.snapshots.SummarySnapshot
import io.prometheus.metrics.model.snapshots.{Exemplar => PExemplar}
import io.prometheus.metrics.model.snapshots.{Quantile => PQuantile}
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
    private val callbackState: Ref[F, CallbackState[F]],
    private val callbackTimeoutState: Ref[F, Set[String]],
    private val callbackErrorState: Ref[F, Set[String]],
    private val singleCallbackTimeoutState: Ref[F, Set[String]],
    private val singleCallbackErrorState: Ref[F, Set[String]],
    private val sem: Semaphore[F],
    private val dispatcher: Dispatcher[F],
    private val callbackTimeout: FiniteDuration,
    private val singleCallbackTimeout: FiniteDuration,
    // Metric-collection callbacks: keyed by prefix (each prefix has its own merged common-labels map
    // and a token-keyed map of user callbacks producing MetricCollection values). Constructed
    // here, but the upstream MultiCollector that aggregates them is built in the class body via
    // [[buildMetricCollectionMultiCollector]] so it can capture `this`.
    private val metricCollectionRef: Ref[F, Map[
      Option[Metric.Prefix],
      (
          Map[Label.Name, String],
          Map[
            Unique.Token,
            F[MetricCollection]
          ]
      )
    ]],
    private val logger: Throwable => String => F[Unit]
) extends DoubleMetricRegistry[F]
    with DoubleCallbackRegistry[F] {

  type Underlying = PrometheusRegistry

  /** Returns the underlying upstream `PrometheusRegistry`. Use to expose metrics over an HTTP endpoint or to register
    * external collectors.
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
      renderedName: String,
      labels: IndexedSeq[Label.Name]
  ): Resource[F, (M, Ref[F, Option[Exemplar.Data]])] = {
    lazy val metricId: MetricID = (labels, metricType)
    lazy val fullName: StateKey = (metricPrefix, stringName)
    // `renderedName` is the wire-level metric name (e.g. `foo_total` for Counter); used in error
    // messages so users see the same string the registry will expose. The StateKey uses
    // `stringName` (the `_total`-stripped form for Counter, matching upstream's storage convention).
    lazy val renderedFullName = renderedName

    val acquire = sem.permit.surround(
      // Reject metric registration when a callback already owns this name. Mirrors the
      // `javasimpleclient` adapter; required for cross-axis (callback↔metric) collision detection.
      callbackState.get.flatMap { cbs =>
        cbs.get(fullName) match {
          case None => Applicative[F].unit
          case Some(_) =>
            ApplicativeThrow[F].raiseError[Unit](
              new RuntimeException(
                s"A callback with the same name as '$renderedFullName' is already registered with different labels and/or type"
              )
            )
        }
      } >> ref.get
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
            case _ =>
              logger(new IllegalStateException("javaclient: unexpected state during Resource release"))(
                s"Unexpected state at $renderedFullName release; collector NOT unregistered. " +
                  "This indicates a bug in the registry state machinery; please report."
              )
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
      renderedName = fullName,
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
          Utils.modifyMetric[F, Counter.Name, CounterDataPoint](
            metricName = name,
            allLabelNames = allLabelNames,
            dynamicLabels = f(labels),
            commonLabelValues = commonLabelValuesArray,
            getDataPoint = (lbls: Array[String]) => counter.labelValues(lbls: _*),
            modify = (dp: CounterDataPoint) =>
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
      renderedName = fullName,
      labels = allLabelNames
    ).map { case (gauge, _) =>
      @inline
      def modify(g: GaugeDataPoint => Unit, labels: A): F[Unit] =
        Utils.modifyMetric[F, Gauge.Name, GaugeDataPoint](
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

  /** Shared post-registration plumbing for all three histogram modes (classic-only, native-only, dual). Extracts the
    * common labels, registers the upstream `PHistogram` via `configureBuilderOrRetrieve` (using the caller's
    * `configureBuilder` to set mode-specific bits like `.classicOnly()` or `.nativeOnly()`), and wraps the result in a
    * prometheus4cats `Histogram` whose observe goes through `Utils.modifyMetric` against the upstream
    * `DistributionDataPoint`.
    */
  private def histogramFromBuilder[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      metricType: MetricType,
      configureBuilder: PHistogram.Builder => PHistogram.Builder,
      exemplarState: Ref[F, Option[Exemplar.Data]] => Histogram.ExemplarState[F]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] = {
    val commonLabelNames       = commonLabels.value.keys.toIndexedSeq
    val commonLabelValuesArray = commonLabels.value.values.toArray
    val allLabelNames          = labelNames ++ commonLabelNames
    val fullName               = NameUtils.makeName(prefix, name)

    configureBuilderOrRetrieve[PHistogram](
      register = () =>
        configureBuilder(
          PHistogram
            .builder()
            .name(fullName)
            .help(help.value)
            .labelNames(allLabelNames.map(_.value): _*)
        ).register(registry),
      metricType = metricType,
      metricPrefix = prefix,
      stringName = name.value,
      renderedName = fullName,
      labels = allLabelNames
    ).map { case (histogram, exemplarRef) =>
      Histogram.make[F, Double, A](
        exemplarState(exemplarRef),
        _observe = { (d: Double, labels: A, exemplar: Option[Exemplar.Labels]) =>
          Utils.modifyMetric[F, Histogram.Name, DistributionDataPoint](
            metricName = name,
            allLabelNames = allLabelNames,
            dynamicLabels = f(labels),
            commonLabelValues = commonLabelValuesArray,
            getDataPoint = (lbls: Array[String]) => histogram.labelValues(lbls: _*),
            modify = (dp: DistributionDataPoint) =>
              exemplar.fold(dp.observe(d))(e => dp.observeWithExemplar(d, transformExemplarLabels(e))),
            logger = logger
          )
        }
      )
    }
  }

  /** Applies the five native-histogram tuning setters from a [[NativeHistogram]] config to a `PHistogram.Builder`,
    * conditionally including `nativeResetDuration` only when the configured duration is positive. Shared by the
    * native-only and dual-mode registration paths.
    */
  private def applyNativeConfig(builder: PHistogram.Builder, config: NativeHistogram): PHistogram.Builder = {
    val withTuning = builder
      .nativeInitialSchema(config.initialSchema)
      .nativeMaxNumberOfBuckets(config.maxNumberOfBuckets)
      .nativeMaxZeroThreshold(config.maxZeroThreshold)
      .nativeMinZeroThreshold(config.minZeroThreshold)
    if (config.resetDuration > 0.seconds)
      withTuning.nativeResetDuration(
        config.resetDuration.toSeconds,
        java.util.concurrent.TimeUnit.SECONDS
      )
    else withTuning
  }

  override def createAndRegisterDoubleHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] =
    histogramFromBuilder(
      prefix,
      name,
      help,
      commonLabels,
      labelNames,
      metricType = MetricType.Histogram,
      // .classicOnly() is required because the 1.x default emits BOTH classic AND native
      // histograms from a single declaration. Preserving v5 behaviour means only the classic
      // form is emitted from the .histogram(...) DSL path; the .nativeHistogram(...) DSL path
      // calls .nativeOnly() instead.
      configureBuilder = _.classicOnly().classicUpperBounds(buckets.toList: _*),
      exemplarState = ref => Histogram.ExemplarState.fromRef(buckets, ref)
    )(f)

  override def createAndRegisterDoubleHistogramWithNative[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      config: NativeHistogram
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] =
    histogramFromBuilder(
      prefix,
      name,
      help,
      commonLabels,
      labelNames,
      // Use a distinct MetricType so dedup is correct: registering the same metric name as
      // dual-mode and then again as classic-only is a programmer error and should fail.
      metricType = MetricType.HistogramWithNative,
      // Dual-mode: NEITHER .classicOnly() NOR .nativeOnly(). Both classicUpperBounds(...) and
      // the native setters are configured. The resulting Histogram emits BOTH representations,
      // letting Prometheus's `convert_classic_histograms_to_nhcb` pick the classic form for
      // server-side NHCB conversion while the native exponential is also available directly.
      configureBuilder = b => applyNativeConfig(b.classicUpperBounds(buckets.toList: _*), config),
      exemplarState = ref => Histogram.ExemplarState.fromRef(buckets, ref)
    )(f)

  override def createAndRegisterDoubleNativeHistogram[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      config: NativeHistogram
  )(f: A => IndexedSeq[String]): Resource[F, Histogram[F, Double, A]] =
    histogramFromBuilder(
      prefix,
      name,
      help,
      commonLabels,
      labelNames,
      metricType = MetricType.NativeHistogram,
      configureBuilder = b => applyNativeConfig(b.nativeOnly(), config),
      // Native histograms use ExemplarState.noop: the upstream Histogram still accepts exemplars
      // via observeWithExemplar(d, labels), but the bucket-driven sampler in
      // Histogram.ExemplarState.fromRef requires explicit bucket boundaries which native
      // histograms do not have. Consumers wanting sampled exemplars on a native histogram are not
      // supported in this initial cut; explicit exemplars (.observeWithExemplar) still work
      // end-to-end.
      exemplarState = _ => Histogram.ExemplarState.noop
    )(f)

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
    val commonLabelNames       = commonLabels.value.keys.toIndexedSeq
    val commonLabelValuesArray = commonLabels.value.values.toArray
    val allLabelNames          = labelNames ++ commonLabelNames
    val fullName               = NameUtils.makeName(prefix, name)

    configureBuilderOrRetrieve[PSummary](
      register = () => {
        val builder = PSummary
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          .maxAgeSeconds(maxAge.toSeconds)
          .numberOfAgeBuckets(ageBuckets.value)
        quantiles.foreach(q => builder.quantile(q.value.value, q.error.value))
        builder.register(registry)
      },
      metricType = MetricType.Summary,
      metricPrefix = prefix,
      stringName = name.value,
      renderedName = fullName,
      labels = allLabelNames
    ).map { case (summary, _) =>
      Summary.make[F, Double, A] { case (d, labels) =>
        Utils.modifyMetric[F, Summary.Name, DistributionDataPoint](
          metricName = name,
          allLabelNames = allLabelNames,
          dynamicLabels = f(labels),
          commonLabelValues = commonLabelValuesArray,
          getDataPoint = (lbls: Array[String]) => summary.labelValues(lbls: _*),
          modify = (dp: DistributionDataPoint) => dp.observe(d),
          logger = logger
        )
      }
    }
  }

  @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
  override def createAndRegisterInfo[A](
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name]
  )(f: A => IndexedSeq[String]): Resource[F, Info[F, A]] = {
    // Info uses MetricWithFixedMetadata (not StatefulMetric), so the registration state machinery
    // here is a slim variant of configureBuilderOrRetrieve — Info doesn't need exemplar tracking.
    // The fully-qualified name (with prefix and `_info` suffix) is what upstream stores on the
    // metadata; the testkit looks up snapshots by either the full or the `_info`-stripped form.
    val renderedFullName   = NameUtils.makeName(prefix, name)
    val fullName: StateKey = (prefix, name.value)
    val metricId: MetricID = (labelNames, MetricType.Info)

    val acquire = sem.permit.surround(
      callbackState.get.flatMap { cbs =>
        cbs.get(fullName) match {
          case None => Applicative[F].unit
          case Some(_) =>
            ApplicativeThrow[F].raiseError[Unit](
              new RuntimeException(
                s"A callback with the same name as '$renderedFullName' is already registered with different labels and/or type"
              )
            )
        }
      } >> ref.get
        .flatMap[(State[F], PInfo)] { (metrics: State[F]) =>
          metrics.get(fullName) match {
            case Some((expected, (collector, exemplarRef, references))) =>
              if (metricId == expected)
                Applicative[F].pure(
                  (
                    metrics.updated(fullName, (expected, (collector, exemplarRef, references + 1))),
                    collector.asInstanceOf[PInfo]
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
                collector <- Sync[F].delay(
                               PInfo
                                 .builder()
                                 .name(renderedFullName)
                                 .help(help.value)
                                 .labelNames(labelNames.map(_.value): _*)
                                 .register(registry)
                             )
              } yield (
                metrics.updated(fullName, (metricId, (collector, exemplarRef, 1))),
                collector
              )
          }
        }
        .flatMap { case (state, collector) => ref.set(state).as(collector) }
    )

    val release: PInfo => F[Unit] = collector =>
      sem.permit.surround {
        ref.get.flatMap { metrics =>
          metrics.get(fullName) match {
            case Some((`metricId`, (_, _, 1))) =>
              ref.set(metrics - fullName) >>
                Sync[F].delay(registry.unregister(collector)).handleErrorWith { e =>
                  logger(e)(s"Failed to unregister Info collector: '$collector'")
                }
            case Some((`metricId`, (collector, exemplarRef, references))) =>
              ref.set(metrics.updated(fullName, (metricId, (collector, exemplarRef, references - 1))))
            case _ =>
              logger(new IllegalStateException("javaclient: unexpected state during Info Resource release"))(
                s"Unexpected state at $renderedFullName release; Info collector NOT unregistered. " +
                  "This indicates a bug in the registry state machinery; please report."
              )
          }
        }
      }

    Resource.make(acquire)(release).map { info =>
      Info.make[F, A] { a =>
        val values = f(a)
        Sync[F].delay(info.setLabelValues(values: _*)).handleErrorWith { e =>
          logger(e)(s"Failed to set Info label values for metric '$renderedFullName'")
        }
      }
    }
  }

  // ─── callback machinery ──────────────────────────────────────────────────────────────────────────
  //
  // Mirrors the legacy javasimpleclient adapter's per-callback timeout + error-tracking pattern:
  //
  //   - `singleCallbackTimeout` bounds an individual callback invocation; exceeding it returns empty
  //     samples for that registration and logs ONCE per metric name.
  //   - `callbackTimeout` bounds the COMBINED collection of all registered callbacks for a single
  //     metric (i.e., the wrapper that aggregates samples across multiple consumer registrations).
  //   - Error tracking refs (`callbackTimeoutState`, `callbackErrorState`, `singleCallbackTimeoutState`,
  //     `singleCallbackErrorState`) prevent log spam: the first time a metric's callback times out / fails,
  //     we log; subsequent occurrences are silently counted by the upstream Prometheus runtime via the
  //     regular scrape-error path.
  //
  // Skipped vs the legacy adapter: the internal `prometheus4cats_combined_callback_metric_total` and
  // `prometheus4cats_callback_total` counters that tracked callback success/error/timeout counts
  // per metric. They're observability nice-to-haves; consumers can always add them externally if
  // wanted. May add in a follow-up commit.

  private def trackErrors[A](
      state: Ref[F, Set[String]],
      stringName: String,
      onContains: F[A],
      onContainsNot: F[A]
  ): F[A] =
    state.modify { current =>
      if (current.contains(stringName)) (current, onContains) else (current + stringName, onContainsNot)
    }.flatten

  /** Wrap an effect with a timeout + error fallback, dispatching synchronously through the supplied Dispatcher. Both
    * timeouts and failures are mapped to `empty` and forwarded to `onTimeout` / `onError` for logging-side-effect
    * handling.
    */
  private def runCallbackWithBounds[A](
      fa: F[A],
      timeout: FiniteDuration,
      onTimeout: TimeoutException => F[A],
      onError: Throwable => F[A]
  ): A =
    dispatcher.unsafeRunSync(fa.timeout(timeout).handleErrorWith {
      case th: TimeoutException => onTimeout(th)
      case th                   => onError(th)
    })

  /** Per-callback timeout wrapper: bounds an individual registration's callback invocation. Returns the
    * (logged-timeout, logged-error, samples) triple so the outer aggregation can decide what to persist into
    * [[singleCallbackErrorState]] without re-logging on every scrape.
    */
  private def timeoutEachCallback(
      stringName: String,
      samplesF: F[NonEmptyList[DataPointSnapshot]],
      hasLoggedTimeout: Boolean,
      hasLoggedError: Boolean
  ): F[(Boolean, Boolean, List[DataPointSnapshot])] =
    samplesF
      .map(samples => (hasLoggedTimeout, hasLoggedError, samples.toList))
      .timeout(singleCallbackTimeout)
      .handleErrorWith {
        case th: TimeoutException =>
          (if (hasLoggedTimeout) Applicative[F].unit
           else
             logger(th)(
               s"Timed out running a callback for the metric '$stringName' after $singleCallbackTimeout. " +
                 "This warning will only be shown once after process start."
             )).as((true, hasLoggedError, List.empty))
        case th =>
          (if (hasLoggedError) Applicative[F].unit
           else
             logger(th)(
               s"Executing a callback for the metric '$stringName' failed with the following exception. " +
                 "This warning will only be shown once after process start."
             )).as((hasLoggedTimeout, true, List.empty))
      }

  /** Generic callback registration:
    *   1. Look up (or create) a single upstream `Collector` for this metric name. 2. Add the user's callback to the
    *      Collector's per-token registration map. 3. On Resource release, remove this token; if it was the last token,
    *      unregister the Collector.
    *
    * The `makeCollector` thunk is responsible for constructing the kind-specific Collector that builds the right
    * `MetricSnapshot` from the aggregated `(value, labels)` tuples at scrape time.
    */
  private def registerCallback[A: Show](
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      name: A,
      callback: F[NonEmptyList[DataPointSnapshot]],
      makeCollector: Ref[F, CallbackPayload[F]] => PCollector
  ): Resource[F, Unit] = {
    // Use the same `_total`-stripped key the metric path uses, so cross-axis (metric↔callback)
    // collision detection sees both paths under the same StateKey. Counter names end in `_total`
    // for the metric path; the simpleclient/upstream wire convention stores them base-name.
    lazy val n                  = counterName(name)
    lazy val fullName: StateKey = (metricPrefix, n)
    lazy val renderedFullName   = NameUtils.makeName(metricPrefix, name)

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
                  token    <- Unique[F].unique
                  innerRef <- Ref.of[F, CallbackPayload[F]](Map(token -> callback))
                  collector = makeCollector(innerRef)
                  _        <- Sync[F].delay(registry.register(collector))
                  _        <- callbackState.set(callbacks.updated(fullName, (metricType, innerRef, collector)))
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
                  callbackState.set(state - fullName) >>
                    Sync[F].delay(registry.unregister(collector)).handleErrorWith { e =>
                      logger(e)(s"Failed to unregister callback collector: '$collector'")
                    }
                else callbacks.set(newCallbacks)
              }
            case None => Applicative[F].unit
          }
        })
      }
      .void
  }

  /** Aggregate all per-token callbacks into a single list of pre-built data-point snapshots, applying the per-callback
    * timeout and error-tracking machinery. Used by every kind-specific Collector; the Collector then casts the data
    * points to its concrete subtype and wraps them in the right `MetricSnapshot`.
    */
  private def collectAllPayload(
      callbacks: Ref[F, CallbackPayload[F]],
      renderedFullName: String
  ): F[List[DataPointSnapshot]] =
    (singleCallbackTimeoutState.get, singleCallbackErrorState.get).tupled.flatMap { case (loggedTimeout, loggedError) =>
      callbacks.get
        .flatMap(
          _.values.foldM(
            (
              loggedTimeout.contains(renderedFullName),
              loggedError.contains(renderedFullName),
              List.empty[DataPointSnapshot]
            )
          ) { case ((hasLoggedTimeout0, hasLoggedError0, acc), samplesF) =>
            timeoutEachCallback(renderedFullName, samplesF, hasLoggedTimeout0, hasLoggedError0).map {
              case (lto, le, samples) => (lto, le, acc ++ samples)
            }
          }
        )
        .flatMap { case (hasLoggedTimeout0, hasLoggedError0, samples) =>
          val updateTimeout =
            if (hasLoggedTimeout0) singleCallbackTimeoutState.set(loggedTimeout + renderedFullName)
            else Applicative[F].unit
          val updateError =
            if (hasLoggedError0) singleCallbackErrorState.set(loggedError + renderedFullName)
            else Applicative[F].unit
          (updateTimeout *> updateError).as(samples)
        }
    }

  /** Run the aggregated-payload computation through the dispatcher with the combined-callbacks timeout + once-only
    * error logging. Returns `empty` on timeout/error.
    */
  private def runAggregateCollect[A](
      stringName: String,
      result: F[A],
      empty: A
  ): A = runCallbackWithBounds(
    result,
    callbackTimeout,
    th =>
      trackErrors(
        callbackTimeoutState,
        stringName,
        Applicative[F].pure(empty),
        logger(th)(
          s"Timed out running callbacks for metric '$stringName' after $callbackTimeout. " +
            "This warning will only be shown once for each metric after process start."
        ).as(empty)
      ),
    th =>
      trackErrors(
        callbackErrorState,
        stringName,
        Applicative[F].pure(empty),
        logger(th)(
          s"Callbacks for metric '$stringName' failed with the following exception. " +
            "This warning will only be shown once for each metric after process start."
        ).as(empty)
      )
  )

  @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
  override def registerDoubleCounterCallback[A](
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = {
    val commonLabelKeys      = commonLabels.value.keys.toIndexedSeq.map(_.value)
    val commonLabelValuesArr = commonLabels.value.values.toIndexedSeq
    val allLabelNamesStr     = (labelNames.map(_.value) ++ commonLabelKeys).toArray
    val rawName              = NameUtils.makeName(prefix, name)
    // Counter snapshots use the BASE name (without "_total" suffix) per upstream convention; the
    // exposition writer adds "_total" back to the wire format.
    val baseName = if (rawName.endsWith("_total")) rawName.dropRight("_total".length) else rawName

    // Build CounterDataPointSnapshot instances inside the user callback's F so the generic
    // CallbackPayload type can hold them as plain DataPointSnapshots. The Collector at scrape time
    // casts back to CounterDataPointSnapshot.
    //
    // Negative values are clamped to 0.0 to preserve v5 (javasimpleclient) tolerance for malformed
    // callbacks. Upstream v6's CounterDataPointSnapshot.validate() throws IllegalArgumentException on
    // negative values, so the clamp prevents a scrape-time crash when a consumer accidentally exposes
    // a non-monotonic source as a Counter callback.
    val projected: F[NonEmptyList[DataPointSnapshot]] = callback.map(
      _.map { case (v, a) =>
        new CounterSnapshot.CounterDataPointSnapshot(
          if (v < 0) 0.0 else v,
          Labels.of(allLabelNamesStr, (f(a) ++ commonLabelValuesArr).toArray),
          null: PExemplar,
          0L
        ): DataPointSnapshot
      }
    )

    registerCallback[Counter.Name](
      MetricType.Counter,
      prefix,
      name,
      projected,
      makeCollector = (callbacks: Ref[F, CallbackPayload[F]]) =>
        new PCollector {

          override def collect(): MetricSnapshot = {
            val dataPoints: java.util.List[CounterSnapshot.CounterDataPointSnapshot] =
              runAggregateCollect(
                baseName,
                collectAllPayload(callbacks, baseName).map(
                  _.map(_.asInstanceOf[CounterSnapshot.CounterDataPointSnapshot]).asJava
                ),
                java.util.Collections.emptyList[CounterSnapshot.CounterDataPointSnapshot]()
              )
            new CounterSnapshot(new MetricMetadata(baseName, help.value), dataPoints)
          }

        }
    )
  }

  @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
  override def registerDoubleGaugeCallback[A](
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Double, A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = {
    val commonLabelKeys      = commonLabels.value.keys.toIndexedSeq.map(_.value)
    val commonLabelValuesArr = commonLabels.value.values.toIndexedSeq
    val allLabelNamesStr     = (labelNames.map(_.value) ++ commonLabelKeys).toArray
    val fullName             = NameUtils.makeName(prefix, name)

    val projected: F[NonEmptyList[DataPointSnapshot]] = callback.map(
      _.map { case (v, a) =>
        new GaugeSnapshot.GaugeDataPointSnapshot(
          v,
          Labels.of(allLabelNamesStr, (f(a) ++ commonLabelValuesArr).toArray),
          null: PExemplar
        ): DataPointSnapshot
      }
    )

    registerCallback[Gauge.Name](
      MetricType.Gauge,
      prefix,
      name,
      projected,
      makeCollector = (callbacks: Ref[F, CallbackPayload[F]]) =>
        new PCollector {

          override def collect(): MetricSnapshot = {
            val dataPoints: java.util.List[GaugeSnapshot.GaugeDataPointSnapshot] =
              runAggregateCollect(
                fullName,
                collectAllPayload(callbacks, fullName).map(
                  _.map(_.asInstanceOf[GaugeSnapshot.GaugeDataPointSnapshot]).asJava
                ),
                java.util.Collections.emptyList[GaugeSnapshot.GaugeDataPointSnapshot]()
              )
            new GaugeSnapshot(new MetricMetadata(fullName, help.value), dataPoints)
          }

        }
    )
  }

  override def registerDoubleHistogramCallback[A](
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      callback: F[NonEmptyList[(Histogram.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = {
    val commonLabelKeys      = commonLabels.value.keys.toIndexedSeq.map(_.value)
    val commonLabelValuesArr = commonLabels.value.values.toIndexedSeq
    val allLabelNamesStr     = (labelNames.map(_.value) ++ commonLabelKeys).toArray
    val fullName             = NameUtils.makeName(prefix, name)
    // Histogram buckets in 1.x include +Inf as the last upper bound; the user-supplied buckets array
    // doesn't, so append it. Histogram.Value[Double].bucketValues is parallel-indexed with the
    // (declared-buckets ++ +Inf) sequence (cumulative counts).
    val upperBoundsWithInf = (buckets.toSeq :+ Double.PositiveInfinity).toArray

    val projected: F[NonEmptyList[DataPointSnapshot]] = callback.map(
      _.map { case (value, a) =>
        val labelValuesArr = (f(a) ++ commonLabelValuesArr).toArray
        // Histogram.Value.bucketValues are CUMULATIVE counts (matching the wire-format convention).
        // ClassicHistogramBuckets.of expects PER-BUCKET counts (the wire format computes cumulative
        // from per-bucket at scrape). Convert by differencing successive cumulative entries.
        val cumulative = value.bucketValues.toSeq.map(_.toLong)
        val perBucket =
          (cumulative.head +: cumulative.zip(cumulative.tail).map { case (prev, curr) => curr - prev }).toArray
        new HistogramSnapshot.HistogramDataPointSnapshot(
          ClassicHistogramBuckets.of(upperBoundsWithInf, perBucket),
          value.sum,
          Labels.of(allLabelNamesStr, labelValuesArr),
          Exemplars.EMPTY,
          0L
        ): DataPointSnapshot
      }
    )

    registerCallback[Histogram.Name](
      MetricType.Histogram,
      prefix,
      name,
      projected,
      makeCollector = (callbacks: Ref[F, CallbackPayload[F]]) =>
        new PCollector {

          override def collect(): MetricSnapshot = {
            val dataPoints: java.util.List[HistogramSnapshot.HistogramDataPointSnapshot] =
              runAggregateCollect(
                fullName,
                collectAllPayload(callbacks, fullName).map(
                  _.map(_.asInstanceOf[HistogramSnapshot.HistogramDataPointSnapshot]).asJava
                ),
                java.util.Collections.emptyList[HistogramSnapshot.HistogramDataPointSnapshot]()
              )
            new HistogramSnapshot(new MetricMetadata(fullName, help.value), dataPoints)
          }

        }
    )
  }

  override def registerDoubleSummaryCallback[A](
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      labelNames: IndexedSeq[Label.Name],
      callback: F[NonEmptyList[(Summary.Value[Double], A)]]
  )(f: A => IndexedSeq[String]): Resource[F, Unit] = {
    val commonLabelKeys      = commonLabels.value.keys.toIndexedSeq.map(_.value)
    val commonLabelValuesArr = commonLabels.value.values.toIndexedSeq
    val allLabelNamesStr     = (labelNames.map(_.value) ++ commonLabelKeys).toArray
    val fullName             = NameUtils.makeName(prefix, name)

    val projected: F[NonEmptyList[DataPointSnapshot]] = callback.map(
      _.map { case (value, a) =>
        val labelValuesArr = (f(a) ++ commonLabelValuesArr).toArray
        val quantilesJava: Array[PQuantile] =
          value.quantiles.toList.map { case (q, v) => new PQuantile(q, v) }.toArray
        val quantiles =
          if (quantilesJava.isEmpty) Quantiles.EMPTY else Quantiles.of(quantilesJava: _*)
        new SummarySnapshot.SummaryDataPointSnapshot(
          value.count.toLong,
          value.sum,
          quantiles,
          Labels.of(allLabelNamesStr, labelValuesArr),
          Exemplars.EMPTY,
          0L
        ): DataPointSnapshot
      }
    )

    registerCallback[Summary.Name](
      MetricType.Summary,
      prefix,
      name,
      projected,
      makeCollector = (callbacks: Ref[F, CallbackPayload[F]]) =>
        new PCollector {

          override def collect(): MetricSnapshot = {
            val dataPoints: java.util.List[SummarySnapshot.SummaryDataPointSnapshot] =
              runAggregateCollect(
                fullName,
                collectAllPayload(callbacks, fullName).map(
                  _.map(_.asInstanceOf[SummarySnapshot.SummaryDataPointSnapshot]).asJava
                ),
                java.util.Collections.emptyList[SummarySnapshot.SummaryDataPointSnapshot]()
              )
            new SummarySnapshot(new MetricMetadata(fullName, help.value), dataPoints)
          }

        }
    )
  }

  /** A single upstream `MultiCollector` that aggregates all metric-collection callbacks across all prefixes. Built
    * lazily so it captures `this` (specifically `metricCollectionRef`, the dispatcher, and the timeout helpers). The
    * Builder.build flow accesses it via `metricCollectionCollector` to register it with the underlying registry on
    * acquire and unregister on release.
    */
  private[javaclient] val metricCollectionCollector: PMultiCollector = new PMultiCollector {

    override def collect(): MetricSnapshots = {
      val result: F[MetricSnapshots] = metricCollectionRef.get.flatMap { perPrefix =>
        // For each prefix, run all registered callbacks under the per-callback timeout, merge their
        // MetricCollection values, then convert to MetricSnapshots. We use a fresh
        // `singleCallbackErrorState` reading on each scrape so log-once gating still applies.
        perPrefix.toList.flatTraverse { case (prefix, (commonLabels, callbacks)) =>
          val mergedCol: F[MetricCollection] = callbacks.values.toList.foldM(MetricCollection.empty) { (acc, cbF) =>
            cbF
              .timeout(singleCallbackTimeout)
              .handleErrorWith { th =>
                logger(th)(
                  s"A metric-collection callback (prefix=${prefix.map(_.value).getOrElse("<none>")}) failed or timed out."
                ).as(MetricCollection.empty)
              }
              .map(acc.combine)
          }
          mergedCol.map(metricCollectionToSnapshots(prefix, commonLabels, _))
        }
      }.map(snapshotList => new MetricSnapshots(snapshotList.asJava))

      runCallbackWithBounds(
        result,
        callbackTimeout,
        th =>
          logger(th)(s"Combined metric-collection callbacks timed out after $callbackTimeout")
            .as(MetricSnapshots.builder().build()),
        th => logger(th)("Combined metric-collection callbacks failed").as(MetricSnapshots.builder().build())
      )
    }

  }

  @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
  override def registerMetricCollectionCallback(
      prefix: Option[Metric.Prefix],
      commonLabels: Metric.CommonLabels,
      callback: F[MetricCollection]
  ): Resource[F, Unit] = {
    val acquire: F[Unique.Token] = Unique[F].unique.flatMap { token =>
      metricCollectionRef
        .update(map =>
          map.updated(
            prefix,
            map.get(prefix).fold(commonLabels.value -> Map(token -> callback)) { case (existingCommon, cbs) =>
              (existingCommon ++ commonLabels.value) -> cbs.updated(token, callback)
            }
          )
        )
        .as(token)
    }

    Resource
      .make(acquire) { token =>
        metricCollectionRef.update { map =>
          map.get(prefix).fold(map) { case (commonLabels, cbs) =>
            val remaining = cbs - token
            if (remaining.isEmpty) map - prefix
            else map.updated(prefix, commonLabels -> remaining)
          }
        }
      }
      .void
  }

  /** Convert a `MetricCollection` (the prometheus4cats per-prefix bag of typed metric values) into a flat list of
    * upstream `MetricSnapshot`s. Each (name, labelNames) entry becomes one snapshot with one or more data points (one
    * per List entry in the user's MetricCollection).
    */
  @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
  private def metricCollectionToSnapshots(
      prefix: Option[Metric.Prefix],
      commonLabels: Map[Label.Name, String],
      mc: MetricCollection
  ): List[MetricSnapshot] = {
    val commonLabelKeysArr   = commonLabels.keys.toArray.map(_.value)
    val commonLabelValuesArr = commonLabels.values.toArray

    def labelsFor(labelNames: IndexedSeq[Label.Name], labelValues: IndexedSeq[String]): Labels = {
      val nameArr  = labelNames.map(_.value).toArray ++ commonLabelKeysArr
      val valueArr = labelValues.toArray ++ commonLabelValuesArr
      Labels.of(nameArr, valueArr)
    }

    val counterSnapshots: List[MetricSnapshot] = mc.counters.toList.flatMap { case ((name, labelNames), values) =>
      values.headOption.map { head =>
        val rawName  = NameUtils.makeName(prefix, name)
        val baseName = if (rawName.endsWith("_total")) rawName.dropRight("_total".length) else rawName
        val dps = values.map { v =>
          val (vDouble, lbls) = v match {
            case x: MetricCollection.Value.LongCounter   => (x.value.toDouble, x.labelValues)
            case x: MetricCollection.Value.DoubleCounter => (x.value, x.labelValues)
          }
          // Negative values are clamped to 0.0 — same v5-parity / upstream-validate rationale as in
          // registerDoubleCounterCallback.
          new CounterSnapshot.CounterDataPointSnapshot(
            if (vDouble < 0) 0.0 else vDouble,
            labelsFor(labelNames, lbls),
            null: PExemplar,
            0L
          )
        }.asJava
        new CounterSnapshot(new MetricMetadata(baseName, head.help.value), dps): MetricSnapshot
      }
    }

    val gaugeSnapshots: List[MetricSnapshot] = mc.gauges.toList.flatMap { case ((name, labelNames), values) =>
      values.headOption.map { head =>
        val fullName = NameUtils.makeName(prefix, name)
        val dps = values.map { v =>
          val (vDouble, lbls) = v match {
            case x: MetricCollection.Value.LongGauge   => (x.value.toDouble, x.labelValues)
            case x: MetricCollection.Value.DoubleGauge => (x.value, x.labelValues)
          }
          new GaugeSnapshot.GaugeDataPointSnapshot(
            vDouble,
            labelsFor(labelNames, lbls),
            null: PExemplar
          )
        }.asJava
        new GaugeSnapshot(new MetricMetadata(fullName, head.help.value), dps): MetricSnapshot
      }
    }

    val histogramSnapshots: List[MetricSnapshot] = mc.histograms.toList.flatMap { case ((name, labelNames), values) =>
      values.headOption.map { head =>
        val fullName = NameUtils.makeName(prefix, name)
        // Each consumer-supplied histogram value declares its own buckets — they must agree across the
        // List entries for a given name. Take the first entry's buckets as the canonical declaration.
        val buckets: NonEmptySeq[Double] = head match {
          case h: MetricCollection.Value.LongHistogram   => h.buckets.map(_.toDouble)
          case h: MetricCollection.Value.DoubleHistogram => h.buckets
        }
        val upperBoundsWithInf = (buckets.toSeq :+ Double.PositiveInfinity).toArray
        val dps = values.map { v =>
          val (sum, cumulativeCounts, lbls) = v match {
            case x: MetricCollection.Value.LongHistogram =>
              (x.value.sum.toDouble, x.value.bucketValues.toSeq.map(_.toLong), x.labelValues)
            case x: MetricCollection.Value.DoubleHistogram =>
              (x.value.sum, x.value.bucketValues.toSeq.map(_.toLong), x.labelValues)
          }
          // bucketValues are cumulative; ClassicHistogramBuckets.of wants per-bucket. See comment
          // in registerDoubleHistogramCallback for the same conversion.
          val perBucket =
            (cumulativeCounts.head +: cumulativeCounts.zip(cumulativeCounts.tail).map { case (prev, curr) =>
              curr - prev
            }).toArray
          new HistogramSnapshot.HistogramDataPointSnapshot(
            ClassicHistogramBuckets.of(upperBoundsWithInf, perBucket),
            sum,
            labelsFor(labelNames, lbls),
            Exemplars.EMPTY,
            0L
          )
        }.asJava
        new HistogramSnapshot(new MetricMetadata(fullName, head.help.value), dps): MetricSnapshot
      }
    }

    val summarySnapshots: List[MetricSnapshot] = mc.summaries.toList.flatMap { case ((name, labelNames), values) =>
      values.headOption.map { head =>
        val fullName = NameUtils.makeName(prefix, name)
        val dps = values.map { v =>
          val (count, sum, quantiles, lbls) = v match {
            case x: MetricCollection.Value.LongSummary =>
              (
                x.value.count.toLong,
                x.value.sum.toDouble,
                x.value.quantiles.map { case (q, v) => q -> v.toDouble },
                x.labelValues
              )
            case x: MetricCollection.Value.DoubleSummary =>
              (x.value.count.toLong, x.value.sum, x.value.quantiles, x.labelValues)
          }
          val quantilesJava = quantiles.toList.map { case (q, v) => new PQuantile(q, v) }.toArray
          val pquantiles =
            if (quantilesJava.isEmpty) Quantiles.EMPTY else Quantiles.of(quantilesJava: _*)
          new SummarySnapshot.SummaryDataPointSnapshot(
            count,
            sum,
            pquantiles,
            labelsFor(labelNames, lbls),
            Exemplars.EMPTY,
            0L
          )
        }.asJava
        new SummarySnapshot(new MetricMetadata(fullName, head.help.value), dps): MetricSnapshot
      }
    }

    counterSnapshots ::: gaugeSnapshots ::: histogramSnapshots ::: summarySnapshots
  }

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
    *   - takes a `PrometheusRegistry` instead of `CollectorRegistry`;
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
        Dispatcher.sequential[F].flatMap { dispatcher =>
          val acquire = for {
            ref                        <- Ref.of[F, State[F]](Map.empty)
            cbState                    <- Ref.of[F, CallbackState[F]](Map.empty)
            cbTimeoutState             <- Ref.of[F, Set[String]](Set.empty)
            cbErrorState               <- Ref.of[F, Set[String]](Set.empty)
            singleCallbackTimeoutState <- Ref.of[F, Set[String]](Set.empty)
            singleCallbackErrorState   <- Ref.of[F, Set[String]](Set.empty)
            metricCollectionRef <- Ref.of[F, Map[
                                     Option[Metric.Prefix],
                                     (Map[Label.Name, String], Map[Unique.Token, F[MetricCollection]])
                                   ]](Map.empty)
            sem <- Semaphore[F](1L)
            reg = new JavaMetricRegistry[F](
                    promRegistry, ref, cbState, cbTimeoutState, cbErrorState, singleCallbackTimeoutState,
                    singleCallbackErrorState, sem, dispatcher, callbackTimeout = callbackCollectionTimeout,
                    singleCallbackTimeout = callbackTimeout, metricCollectionRef = metricCollectionRef, logger = logger
                  )
            // Register the metric-collection MultiCollector unconditionally; it returns an empty
            // MetricSnapshots when no consumer callbacks are registered, so it's a no-op-cost
            // collector when unused.
            _ <- Sync[F].delay(promRegistry.register(reg.metricCollectionCollector))
          } yield reg

          Resource.make(acquire) { reg =>
            // Unregister metric-collection collector first (always present), then individual
            // callback collectors, then claimed metric collectors.
            Sync[F]
              .delay(promRegistry.unregister(reg.metricCollectionCollector))
              .handleErrorWith(e => logger(e)("Failed to unregister metric-collection MultiCollector at shutdown")) >>
              reg.callbackState.get.flatMap { cbs =>
                cbs.values.toList.traverse_ { case (_, _, collector) =>
                  Sync[F].delay(promRegistry.unregister(collector)).handleErrorWith { e =>
                    logger(e)(s"Failed to unregister callback collector at shutdown: '$collector'")
                  }
                }
              } >> reg.ref.get.flatMap { metrics =>
                if (metrics.nonEmpty)
                  metrics.values.toList.traverse_ { case (_, (collector, _, _)) =>
                    Utils.unregister(collector, promRegistry, logger)
                  }
                else Applicative[F].unit
              }
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
