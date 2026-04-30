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

import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._

import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot
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
