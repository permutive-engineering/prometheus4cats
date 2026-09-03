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
import cats.Show
import cats.data.NonEmptySeq
import cats.effect.kernel._
import cats.effect.std.Semaphore
import cats.syntax.all._

import io.prometheus.metrics.core.datapoints.CounterDataPoint
import io.prometheus.metrics.core.datapoints.DistributionDataPoint
import io.prometheus.metrics.core.datapoints.GaugeDataPoint
import io.prometheus.metrics.core.metrics.StatefulMetric
import io.prometheus.metrics.core.metrics.{Counter => PCounter}
import io.prometheus.metrics.core.metrics.{Gauge => PGauge}
import io.prometheus.metrics.core.metrics.{Histogram => PHistogram}
import io.prometheus.metrics.core.metrics.{Info => PInfo}
import io.prometheus.metrics.core.metrics.{Summary => PSummary}
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
import io.prometheus.metrics.model.registry.Collector
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.Labels
import prometheus4cats._
import prometheus4cats.javaclient.internal.EvictingCollector
import prometheus4cats.javaclient.internal.Utils
import prometheus4cats.javaclient.models.MetricType
import prometheus4cats.util.DoubleMetricRegistry
import prometheus4cats.util.NameUtils

/** Implements [[MetricRegistry]] against the upstream `prometheus-metrics-core` 1.x library (the successor to the
  * legacy simpleclient backend).
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
    private val logger: Throwable => String => F[Unit],
    private val staleSeriesTtl: Option[FiniteDuration]
) extends DoubleMetricRegistry[F] {

  /** Wraps a data-point lookup so that every write refreshes the label set's eviction timestamp. The lookup runs first:
    * upstream rejects some label values (a `null` among them) from inside `labelValues`, and touching before that would
    * record a label set the metric holds no data point for — which eviction, driven off the tracking map, would then
    * have to reclaim after the fact.
    */
  private def withTouch[D](
      evicting: Option[EvictingCollector]
  )(getDataPoint: Array[String] => D): Array[String] => D =
    evicting.fold(getDataPoint) { e => lbls =>
      val dataPoint = getDataPoint(lbls)
      e.touch(lbls)
      dataPoint
    }

  type Underlying = PrometheusRegistry

  /** Returns the underlying upstream `PrometheusRegistry`. Use to expose metrics over an HTTP endpoint or to register
    * external collectors.
    */
  def underlying: PrometheusRegistry = registry

  protected def counterName[A: Show](name: A): String = name match {
    case counter: Counter.Name => counter.value.replace("_total", "")
    case _                     => name.show
  }

  /** Common pre-registration plumbing: under the semaphore, look up an existing metric by name+labels+type. If present
    * with the same metric ID, increment its claim count and reuse. If present with a different metric ID, raise. If
    * absent, run the user-provided `build` thunk to construct a fresh metric, register it (wrapped in an
    * `EvictingCollector` when stale-series eviction is enabled and the metric has dynamic labels) and store the
    * registered collector. On release, decrement the claim count and unregister when the last claim is dropped.
    *
    * Mirrors the behaviour of the legacy `javasimpleclient` adapter — supports overlapping `Resource`-scoped
    * registrations of the same metric name without registering it twice with the underlying registry.
    */
  @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
  protected def configureBuilderOrRetrieve[M <: StatefulMetric[_, _]](
      build: () => M,
      metricType: MetricType,
      metricPrefix: Option[Metric.Prefix],
      stringName: String,
      renderedName: String,
      labels: IndexedSeq[Label.Name],
      dynamicLabels: IndexedSeq[Label.Name]
  ): Resource[F, (M, Option[EvictingCollector], Ref[F, Option[Exemplar.Data]])] = {
    lazy val metricId: MetricID = (labels, metricType)
    lazy val fullName: StateKey = (metricPrefix, stringName)
    // `renderedName` is the wire-level metric name (e.g. `foo_total` for Counter); used in error
    // messages so users see the same string the registry will expose. The StateKey uses
    // `stringName` (the `_total`-stripped form for Counter, matching upstream's storage convention).
    lazy val renderedFullName = renderedName

    val acquire = sem.permit.surround(
      ref.get
        .flatMap[(State[F], (M, Option[EvictingCollector], Ref[F, Option[Exemplar.Data]]))] { (metrics: State[F]) =>
          metrics.get(fullName) match {
            case Some((expected, (collector, exemplarRef, references))) =>
              if (metricId == expected) {
                val (metric, evicting) = collector match {
                  case e: EvictingCollector => (e.underlying.asInstanceOf[M], Some(e))
                  case m                    => (m.asInstanceOf[M], None)
                }
                Applicative[F].pure(
                  (
                    metrics.updated(fullName, (expected, (collector, exemplarRef, references + 1))),
                    (metric, evicting, exemplarRef)
                  )
                )
              } else
                ApplicativeThrow[F].raiseError(
                  new RuntimeException(
                    s"A metric with the same name as '$renderedFullName' is already registered with different labels and/or type"
                  )
                )
            case None =>
              for {
                exemplarRef <- Ref.of[F, Option[Exemplar.Data]](None)
                registered <- Sync[F].delay {
                                val metric = build()
                                val evicting = staleSeriesTtl
                                  .filter(_ => dynamicLabels.nonEmpty)
                                  .map(new EvictingCollector(metric, _))
                                val collector: Collector = evicting.getOrElse(metric)
                                registry.register(collector)
                                (metric, evicting, collector)
                              }
                (metric, evicting, collector) = registered
              } yield (
                metrics.updated(fullName, (metricId, (collector, exemplarRef, 1))),
                (metric, evicting, exemplarRef)
              )
          }
        }
        .flatMap { case (state, pair) => ref.set(state).as(pair) }
    )

    Resource.make(acquire) { _ =>
      sem.permit.surround {
        ref.get.flatMap { metrics =>
          metrics.get(fullName) match {
            case Some((`metricId`, (registered, _, 1))) =>
              ref.set(metrics - fullName) >> Utils.unregister(registered, registry, logger)
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
      build = () =>
        // No `.withExemplars()` — that method is inherited from the package-private
        // StatefulMetric$Builder, and exposing its return type from outside its package
        // triggers IllegalAccessError at JVM access-check time. Exemplar handling is enabled
        // by default in prometheus-metrics-core 1.x; `.withoutExemplars()` is the disable.
        PCounter
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          .build(),
      metricType = MetricType.Counter,
      metricPrefix = prefix,
      stringName = n,
      renderedName = fullName,
      labels = allLabelNames,
      dynamicLabels = labelNames
    ).map { case (counter, evicting, exemplarRef) =>
      val getDataPoint = withTouch(evicting)((lbls: Array[String]) => counter.labelValues(lbls: _*))

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
            getDataPoint = getDataPoint,
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
      build = () =>
        PGauge
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          .build(),
      metricType = MetricType.Gauge,
      metricPrefix = prefix,
      stringName = name.value,
      renderedName = fullName,
      labels = allLabelNames,
      dynamicLabels = labelNames
    ).map { case (gauge, evicting, _) =>
      val getDataPoint = withTouch(evicting)((lbls: Array[String]) => gauge.labelValues(lbls: _*))

      @inline
      def modify(g: GaugeDataPoint => Unit, labels: A): F[Unit] =
        Utils.modifyMetric[F, Gauge.Name, GaugeDataPoint](
          metricName = name, allLabelNames = allLabelNames, dynamicLabels = f(labels),
          commonLabelValues = commonLabelValuesArray, getDataPoint = getDataPoint, modify = g, logger = logger
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
      build = () =>
        configureBuilder(
          PHistogram
            .builder()
            .name(fullName)
            .help(help.value)
            .labelNames(allLabelNames.map(_.value): _*)
        ).build(),
      metricType = metricType,
      metricPrefix = prefix,
      stringName = name.value,
      renderedName = fullName,
      labels = allLabelNames,
      dynamicLabels = labelNames
    ).map { case (histogram, evicting, exemplarRef) =>
      val getDataPoint = withTouch(evicting)((lbls: Array[String]) => histogram.labelValues(lbls: _*))

      Histogram.make[F, Double, A](
        exemplarState(exemplarRef),
        _observe = { (d: Double, labels: A, exemplar: Option[Exemplar.Labels]) =>
          Utils.modifyMetric[F, Histogram.Name, DistributionDataPoint](
            metricName = name,
            allLabelNames = allLabelNames,
            dynamicLabels = f(labels),
            commonLabelValues = commonLabelValuesArray,
            getDataPoint = getDataPoint,
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
      build = () => {
        val builder = PSummary
          .builder()
          .name(fullName)
          .help(help.value)
          .labelNames(allLabelNames.map(_.value): _*)
          .maxAgeSeconds(maxAge.toSeconds)
          .numberOfAgeBuckets(ageBuckets.value)
        quantiles.foreach(q => builder.quantile(q.value.value, q.error.value))
        builder.build()
      },
      metricType = MetricType.Summary,
      metricPrefix = prefix,
      stringName = name.value,
      renderedName = fullName,
      labels = allLabelNames,
      dynamicLabels = labelNames
    ).map { case (summary, evicting, _) =>
      val getDataPoint = withTouch(evicting)((lbls: Array[String]) => summary.labelValues(lbls: _*))

      Summary.make[F, Double, A] { case (d, labels) =>
        Utils.modifyMetric[F, Summary.Name, DistributionDataPoint](
          metricName = name,
          allLabelNames = allLabelNames,
          dynamicLabels = f(labels),
          commonLabelValues = commonLabelValuesArray,
          getDataPoint = getDataPoint,
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
      ref.get
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
      val logger: Throwable => String => F[Unit],
      val registerJvmMetrics: Boolean,
      val staleSeriesTtl: Option[FiniteDuration]
  ) {

    private def copy(
        promRegistry: PrometheusRegistry = promRegistry,
        logger: Throwable => String => F[Unit] = logger,
        registerJvmMetrics: Boolean = registerJvmMetrics,
        staleSeriesTtl: Option[FiniteDuration] = staleSeriesTtl
    ): Builder[F] =
      new Builder(promRegistry, logger, registerJvmMetrics, staleSeriesTtl) {}

    def withRegistry(promRegistry: PrometheusRegistry): Builder[F] = copy(promRegistry = promRegistry)

    def withLogger(logger: Throwable => String => F[Unit]): Builder[F] = copy(logger = logger)

    /** Register the standard JVM/process metrics (memory pools, GC, threads, class loading, buffers) via the upstream
      * `prometheus-metrics-instrumentation-jvm` library when the registry is built. Replacement for the legacy
      * `withHotSpotCollectors` builder method.
      */
    def withJvmMetrics: Builder[F] = copy(registerJvmMetrics = true)

    /** Evict label sets that have not been written to for `ttl`. Eviction is driven by the scrape: each scrape exposes
      * a stale series one final time and removes it from the underlying collector, so it is absent from subsequent
      * scrapes; a later write recreates the series, which then starts again from zero. Intended for metrics labelled by
      * unbounded or churning values, where the registry would otherwise accumulate dead series for the lifetime of the
      * process. Only metrics with at least one *dynamic* label are affected; unlabelled, common-labels-only, `Info` and
      * callback-backed metrics never are. Because eviction only happens at scrape time, a registry that is never
      * scraped never evicts.
      *
      * This applies to every labelled stateful metric, gauges included. A labelled gauge that is set once (a
      * `build_info`-style constant) or only on change will therefore be evicted `ttl` after its last `set` and stay
      * absent until the next one, so `absent(...)` or `== 1` alerts over it can misfire. Keep such gauges unlabelled,
      * or do not enable eviction on the registry that holds them.
      *
      * `ttl` must be positive; a non-positive value fails [[build]].
      */
    def withStaleSeriesEviction(ttl: FiniteDuration): Builder[F] = copy(staleSeriesTtl = Some(ttl))

    def build: Resource[F, JavaMetricRegistry[F]] =
      Resource.eval {
        staleSeriesTtl.traverse_ { ttl =>
          Sync[F].raiseUnless(ttl > Duration.Zero)(
            new IllegalArgumentException(s"stale series TTL must be positive, got $ttl")
          )
        }
      }.flatMap { _ =>
        Resource.eval {
          if (registerJvmMetrics) Sync[F].delay(JvmMetrics.builder().register(promRegistry))
          else Applicative[F].unit
        }
      }.flatMap { _ =>
        val acquire = for {
          ref <- Ref.of[F, State[F]](Map.empty)
          sem <- Semaphore[F](1L)
          reg  = new JavaMetricRegistry[F](promRegistry, ref, sem, logger, staleSeriesTtl)
        } yield reg

        Resource.make(acquire) { reg =>
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
        logger = _ => _ => Async[F].unit,
        registerJvmMetrics = false,
        staleSeriesTtl = None
      ) {}

  }

}
