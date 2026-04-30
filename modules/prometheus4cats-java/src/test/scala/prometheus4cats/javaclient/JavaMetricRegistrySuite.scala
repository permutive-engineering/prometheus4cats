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

import scala.jdk.CollectionConverters._

import cats.data.NonEmptyList
import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._

import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot
import io.prometheus.metrics.model.snapshots.InfoSnapshot
import io.prometheus.metrics.model.snapshots.SummarySnapshot
import munit.CatsEffectSuite
import prometheus4cats._

/** Smoke tests for the new javaclient backend covering Counter, Gauge, and (most importantly) the classic vs native
  * histogram distinction.
  *
  * The comprehensive testkit-based [[MetricRegistrySuite]] / [[CallbackRegistrySuite]] (which auto-tests every method)
  * will be wired in once Summary, Info, and the callback path are also implemented in this backend. Until then, the
  * testkit suites can't be applied because the unimplemented methods would throw.
  */
class JavaMetricRegistrySuite extends CatsEffectSuite {

  private def freshRegistry: Resource[IO, (PrometheusRegistry, MetricFactory[IO])] =
    for {
      promRegistry <- Resource.eval(IO.delay(new PrometheusRegistry()))
      registry     <- JavaMetricRegistry.Builder[IO]().withRegistry(promRegistry).build
      factory       = MetricFactory.builder.build[IO](registry)
    } yield (promRegistry, factory)

  // Snapshots are only present in the underlying PrometheusRegistry while the metric Resource is held;
  // releasing the metric unregisters it. So all assertions must run inside the metric's `.use` scope.

  test("counter — inc once, scrape returns the incremented value") {
    freshRegistry.use { case (promRegistry, factory) =>
      factory
        .counter("test_counter_total")
        .ofDouble
        .help("test counter")
        .build
        .use { c =>
          c.inc(1.0) >> IO.delay {
            val snapshot = promRegistry
              .scrape()
              .asScala
              .collectFirst { case s: CounterSnapshot if s.getMetadata.getName === "test_counter" => s }
              .getOrElse(fail("expected a CounterSnapshot named 'test_counter'"))
            assertEquals(snapshot.getDataPoints.asScala.head.getValue, 1.0)
          }
        }
    }
  }

  test("gauge — inc, dec, and set are each reflected in the scrape") {
    freshRegistry.use { case (promRegistry, factory) =>
      factory
        .gauge("test_gauge")
        .ofDouble
        .help("test gauge")
        .build
        .use { g =>
          g.inc(5.0) >> g.dec(2.0) >> g.set(10.0) >> IO.delay {
            val snapshot = promRegistry
              .scrape()
              .asScala
              .collectFirst { case s: GaugeSnapshot if s.getMetadata.getName === "test_gauge" => s }
              .getOrElse(fail("expected a GaugeSnapshot named 'test_gauge'"))
            assertEquals(snapshot.getDataPoints.asScala.head.getValue, 10.0)
          }
        }
    }
  }

  test("classic histogram — scrape produces classic buckets, no native data") {
    freshRegistry.use { case (promRegistry, factory) =>
      factory
        .histogram("test_classic_histogram")
        .ofDouble
        .help("test classic histogram")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0, 5.0))
        .build
        .use { h =>
          (h.observe(0.05) >> h.observe(0.3) >> h.observe(0.7) >> h.observe(2.0)) >> IO.delay {
            val snapshot = promRegistry
              .scrape()
              .asScala
              .collectFirst { case s: HistogramSnapshot if s.getMetadata.getName === "test_classic_histogram" => s }
              .getOrElse(fail("expected a HistogramSnapshot named 'test_classic_histogram'"))
            val dp = snapshot.getDataPoints.asScala.head
            assert(dp.hasClassicHistogramData, "expected classic histogram data")
            assert(!dp.hasNativeHistogramData, "did not expect native histogram data on a classic histogram")
            assertEquals(dp.getCount, 4L)
            assertEqualsDouble(dp.getSum, 3.05, 1e-9)
          }
        }
    }
  }

  test("native histogram — scrape produces native bucket data, no classic buckets") {
    freshRegistry.use { case (promRegistry, factory) =>
      factory
        .nativeHistogram("test_native_histogram")
        .help("test native histogram")
        .build
        .use { h =>
          (h.observe(0.05) >> h.observe(0.3) >> h.observe(0.7) >> h.observe(2.0)) >> IO.delay {
            val snapshot = promRegistry
              .scrape()
              .asScala
              .collectFirst { case s: HistogramSnapshot if s.getMetadata.getName === "test_native_histogram" => s }
              .getOrElse(fail("expected a HistogramSnapshot named 'test_native_histogram'"))
            val dp = snapshot.getDataPoints.asScala.head
            assert(!dp.hasClassicHistogramData, "did not expect classic histogram data on a native histogram")
            assert(dp.hasNativeHistogramData, "expected native histogram data")
            assertEquals(dp.getCount, 4L)
            assertEqualsDouble(dp.getSum, 3.05, 1e-9)
          }
        }
    }
  }

  test("dual-mode histogram (.withNative) — scrape produces BOTH classic and native data") {
    freshRegistry.use { case (promRegistry, factory) =>
      factory
        .histogram("test_dual_histogram")
        .ofDouble
        .help("test dual-mode (NHCB-friendly) histogram")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0, 5.0))
        .withNative
        .build
        .use { h =>
          (h.observe(0.05) >> h.observe(0.3) >> h.observe(0.7) >> h.observe(2.0)) >> IO.delay {
            val snapshot = promRegistry
              .scrape()
              .asScala
              .collectFirst { case s: HistogramSnapshot if s.getMetadata.getName === "test_dual_histogram" => s }
              .getOrElse(fail("expected a HistogramSnapshot named 'test_dual_histogram'"))
            val dp = snapshot.getDataPoints.asScala.head
            // The headline assertion: BOTH classic and native data are emitted from a single declaration.
            assert(dp.hasClassicHistogramData, "expected classic histogram data on dual-mode histogram")
            assert(dp.hasNativeHistogramData, "expected native histogram data on dual-mode histogram")
            assertEquals(dp.getCount, 4L)
            assertEqualsDouble(dp.getSum, 3.05, 1e-9)
            // Classic buckets are preserved as supplied.
            val classicBuckets = dp.getClassicBuckets.asScala.map(_.getUpperBound).toSet
            assert(classicBuckets.contains(0.1), s"expected classic bucket 0.1; got $classicBuckets")
            assert(classicBuckets.contains(5.0), s"expected classic bucket 5.0; got $classicBuckets")
          }
        }
    }
  }

  test("native histogram — custom NativeHistogram config propagates initialSchema to the scraped snapshot") {
    freshRegistry.use { case (promRegistry, factory) =>
      factory
        .nativeHistogram("test_native_tuned", NativeHistogram.Default.withInitialSchema(3))
        .help("native with custom schema")
        .build
        .use { h =>
          h.observe(1.0) >> IO.delay {
            val snapshot = promRegistry
              .scrape()
              .asScala
              .collectFirst { case s: HistogramSnapshot if s.getMetadata.getName === "test_native_tuned" => s }
              .getOrElse(fail("expected a HistogramSnapshot named 'test_native_tuned'"))
            val dp = snapshot.getDataPoints.asScala.head
            assert(dp.hasNativeHistogramData)
            assertEquals(dp.getNativeSchema, 3)
          }
        }
    }
  }

  test("summary — quantiles propagate and observations are aggregated") {
    freshRegistry.use { case (promRegistry, factory) =>
      factory
        .summary("test_summary")
        .ofDouble
        .help("test summary")
        .quantile(Summary.Quantile.from(0.5).toOption.get, Summary.AllowedError.from(0.05).toOption.get)
        .quantile(Summary.Quantile.from(0.99).toOption.get, Summary.AllowedError.from(0.01).toOption.get)
        .build
        .use { s =>
          (s.observe(0.1) >> s.observe(0.5) >> s.observe(1.0) >> s.observe(2.5)) >> IO.delay {
            val snapshot = promRegistry
              .scrape()
              .asScala
              .collectFirst { case s: SummarySnapshot if s.getMetadata.getName === "test_summary" => s }
              .getOrElse(fail("expected a SummarySnapshot named 'test_summary'"))
            val dp = snapshot.getDataPoints.asScala.head
            assertEquals(dp.getCount, 4L)
            assertEqualsDouble(dp.getSum, 4.1, 1e-9)
            // quantile-based summaries record the quantile values; assert both quantiles were declared
            assertEquals(dp.getQuantiles.size, 2)
          }
        }
    }
  }

  test("info — declared labels propagate to scrape output via setLabelValues") {
    freshRegistry.use { case (promRegistry, factory) =>
      factory
        .info("test_build_info")
        .help("test build info")
        .label[String]("version")
        .label[String]("commit")
        .build
        .use { i =>
          // Two `.label[String]` calls produce an Info[F, (String, String)] via the existing
          // labelled-DSL machinery (InitLast tuple-builder).
          i.info(("1.2.3", "abc1234")) >> IO.delay {
            // Upstream stores Info under its base name (without the `_info` suffix). The wire format
            // still emits `test_build_info{...} 1` — the suffix is added by the exposition writer.
            val snapshot = promRegistry
              .scrape()
              .asScala
              .collectFirst { case s: InfoSnapshot if s.getMetadata.getName === "test_build" => s }
              .getOrElse(fail("expected an InfoSnapshot for the 'test_build_info' metric"))
            val dp       = snapshot.getDataPoints.asScala.head
            val labelMap = dp.getLabels.asScala.map(l => l.getName -> l.getValue).toMap
            assertEquals(labelMap.get("version"), Some("1.2.3"))
            assertEquals(labelMap.get("commit"), Some("abc1234"))
          }
        }
    }
  }

  // NOTE: An "Info with no labels declared" test was attempted and removed — upstream
  // prometheus-metrics-core 1.x's Info requires at least one labelName for the metric to emit a
  // data point in scrape output. Calling `setLabelValues()` with empty varargs against an Info
  // built without labelNames produces no observable scrape entry. This is an edge case (real-world
  // Info almost always carries identity labels like version/commit/instance), but should be
  // surfaced in the v6 migration guide for any consumer that relied on a no-label `Info[F, Unit]`.

  test("counter callback — scrape invokes the user callback and propagates the value to the snapshot") {
    val promRegistry = new PrometheusRegistry()

    JavaMetricRegistry
      .Builder[IO]()
      .withRegistry(promRegistry)
      .build
      .use { registry =>
        val factory  = MetricFactory.builder.build[IO](registry, registry)
        val callback = IO.pure(NonEmptyList.of((42.0, "alpha"), (7.0, "beta")))
        factory
          .counter("test_callback_counter_total")
          .ofDouble
          .help("test counter callback")
          .label[String]("variant")
          .callback(callback)
          .build
          .use { _ =>
            IO.delay {
              val snapshot = promRegistry
                .scrape()
                .asScala
                .collectFirst { case s: CounterSnapshot if s.getMetadata.getName === "test_callback_counter" => s }
                .getOrElse(fail("expected a CounterSnapshot named 'test_callback_counter'"))
              val byLabel = snapshot.getDataPoints.asScala
                .map(dp => dp.getLabels.get("variant") -> dp.getValue)
                .toMap
              assertEquals(byLabel.get("alpha"), Some(42.0))
              assertEquals(byLabel.get("beta"), Some(7.0))
            }
          }
      }
  }

  test("gauge callback — scrape invokes the user callback and propagates the value to the snapshot") {
    val promRegistry = new PrometheusRegistry()

    JavaMetricRegistry
      .Builder[IO]()
      .withRegistry(promRegistry)
      .build
      .use { registry =>
        val factory  = MetricFactory.builder.build[IO](registry, registry)
        val callback = IO.pure(NonEmptyList.of((50.0, "n0"), (100.0, "n1")))
        factory
          .gauge("test_callback_gauge")
          .ofDouble
          .help("test gauge callback")
          .label[String]("node")
          .callback(callback)
          .build
          .use { _ =>
            IO.delay {
              val snapshot = promRegistry
                .scrape()
                .asScala
                .collectFirst { case s: GaugeSnapshot if s.getMetadata.getName === "test_callback_gauge" => s }
                .getOrElse(fail("expected a GaugeSnapshot named 'test_callback_gauge'"))
              val byLabel = snapshot.getDataPoints.asScala.map(dp => dp.getLabels.get("node") -> dp.getValue).toMap
              assertEquals(byLabel.get("n0"), Some(50.0))
              assertEquals(byLabel.get("n1"), Some(100.0))
            }
          }
      }
  }

  test("histogram callback — value+labels map into a HistogramDataPointSnapshot with classic buckets") {
    val promRegistry = new PrometheusRegistry()

    JavaMetricRegistry
      .Builder[IO]()
      .withRegistry(promRegistry)
      .build
      .use { registry =>
        val factory = MetricFactory.builder.build[IO](registry, registry)
        // bucketValues are CUMULATIVE counts indexed by (declared-buckets ++ +Inf) — so for buckets
        // 0.1, 1.0, 5.0 with three observations of 0.05, 0.5, 2.0 the cumulative counts are
        // [1, 2, 3, 3] (≤0.1, ≤1.0, ≤5.0, ≤+Inf).
        val histValue = Histogram.Value[Double](2.55, NonEmptySeq.of(1.0, 2.0, 3.0, 3.0))
        val callback  = IO.pure(NonEmptyList.of((histValue, "alpha")))
        factory
          .histogram("test_callback_histogram")
          .ofDouble
          .help("test histogram callback")
          .buckets(NonEmptySeq.of(0.1, 1.0, 5.0))
          .label[String]("variant")
          .callback(callback)
          .build
          .use { _ =>
            IO.delay {
              val snapshot = promRegistry
                .scrape()
                .asScala
                .collectFirst { case s: HistogramSnapshot if s.getMetadata.getName === "test_callback_histogram" => s }
                .getOrElse(fail("expected a HistogramSnapshot named 'test_callback_histogram'"))
              val dp = snapshot.getDataPoints.asScala.head
              assert(dp.hasClassicHistogramData)
              assertEqualsDouble(dp.getSum, 2.55, 1e-9)
              // Classic buckets propagate; +Inf is the last upper bound we appended.
              val upperBounds = dp.getClassicBuckets.asScala.map(_.getUpperBound).toSet
              assert(upperBounds.contains(0.1), s"saw upper bounds $upperBounds")
              assert(upperBounds.contains(5.0), s"saw upper bounds $upperBounds")
            }
          }
      }
  }

  test("summary callback — count, sum, and declared quantiles propagate to the snapshot") {
    val promRegistry = new PrometheusRegistry()

    JavaMetricRegistry
      .Builder[IO]()
      .withRegistry(promRegistry)
      .build
      .use { registry =>
        val factory = MetricFactory.builder.build[IO](registry, registry)
        val summaryValue = Summary.Value[Double](
          count = 10.0,
          sum = 42.5,
          quantiles = Map(0.5 -> 1.2, 0.99 -> 9.5)
        )
        val callback = IO.pure(NonEmptyList.of((summaryValue, "alpha")))
        factory
          .summary("test_callback_summary")
          .ofDouble
          .help("test summary callback")
          .label[String]("variant")
          .callback(callback)
          .build
          .use { _ =>
            IO.delay {
              val snapshot = promRegistry
                .scrape()
                .asScala
                .collectFirst { case s: SummarySnapshot if s.getMetadata.getName === "test_callback_summary" => s }
                .getOrElse(fail("expected a SummarySnapshot named 'test_callback_summary'"))
              val dp = snapshot.getDataPoints.asScala.head
              assertEquals(dp.getCount, 10L)
              assertEqualsDouble(dp.getSum, 42.5, 1e-9)
              val quantileValues = dp.getQuantiles.asScala.map(q => q.getQuantile -> q.getValue).toMap
              assertEquals(quantileValues.size, 2)
              assertEqualsDouble(quantileValues(0.5), 1.2, 1e-9)
              assertEqualsDouble(quantileValues(0.99), 9.5, 1e-9)
            }
          }
      }
  }

  test("registry release unregisters all claimed metrics from the underlying PrometheusRegistry") {
    val promRegistry = new PrometheusRegistry()

    JavaMetricRegistry
      .Builder[IO]()
      .withRegistry(promRegistry)
      .build
      .use { registry =>
        val factory = MetricFactory.builder.build[IO](registry)
        factory.counter("teardown_counter_total").ofDouble.help("test").build.use(_.inc(1.0))
      } >> IO.delay {
      val names = promRegistry.scrape().asScala.map(_.getMetadata.getName).toSet
      assert(
        !names.contains("teardown_counter"),
        s"counter should have been unregistered after the registry resource closed; saw $names"
      )
    }
  }

}
