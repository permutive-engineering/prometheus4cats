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
import scala.jdk.CollectionConverters._

import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.Ref
import cats.effect.kernel.Resource

import io.prometheus.metrics.core.metrics.{Counter => PCounter}
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot
import io.prometheus.metrics.model.snapshots.InfoSnapshot
import io.prometheus.metrics.model.snapshots.SummarySnapshot
import munit.CatsEffectSuite
import prometheus4cats._
import prometheus4cats.javaclient.internal.EvictingCollector
import prometheus4cats.testkit.DslSuite

/** v6-backend wiring for the cross-backend [[DslSuite]].
  *
  * No tests are declared here — every DSL test (counter / gauge / histogram (classic + native + dual) / summary / info
  * / release / name-collision contracts) lives in [[DslSuite]]. This class implements the two abstract hooks and
  * provides the v6-snapshot → `FamilyState` translation.
  *
  * `getRegistryState` is parameterless on the testkit side, so the currently-active `PrometheusRegistry` is threaded
  * through a `Ref` set inside the `resource` acquire and cleared on release. MUnit runs tests within a suite
  * sequentially and every `getRegistryState` call happens inside an active `resource.use { … }` scope, so the Ref
  * always has a value when read.
  */
@SuppressWarnings(Array("scalafix:DisableSyntax"))
class JavaMetricRegistrySuite extends CatsEffectSuite with DslSuite {

  // ─── DslSuite hooks ───────────────────────────────────────────────────────────────────────────────

  private val promRegistryRef: Ref[IO, Option[PrometheusRegistry]] =
    Ref.unsafe[IO, Option[PrometheusRegistry]](None)

  override def resource: Resource[IO, MetricFactory[IO]] =
    for {
      promRegistry <- Resource.eval(IO.delay(new PrometheusRegistry()))
      _            <- Resource.make(promRegistryRef.set(Some(promRegistry)))(_ => promRegistryRef.set(None))
      registry     <- JavaMetricRegistry.Builder[IO]().withRegistry(promRegistry).build
    } yield MetricFactory.builder.build[IO](registry)

  override def getRegistryState: IO[List[FamilyState]] =
    promRegistryRef.get.flatMap {
      case Some(pr) => IO.delay(scrapeToFamilyStates(pr))
      case None =>
        IO.raiseError(
          new IllegalStateException("getRegistryState called outside of an active `resource.use { … }` scope")
        )
    }

  // ─── v6 snapshot → FamilyState translation ────────────────────────────────────────────────────────

  private def promLabelsToMap(labels: io.prometheus.metrics.model.snapshots.Labels): Map[String, String] =
    labels.asScala.map(l => l.getName -> l.getValue).toMap

  private def promExemplarToMap(
      ex: io.prometheus.metrics.model.snapshots.Exemplar
  ): Option[Map[String, String]] =
    Option(ex).map(_.getLabels.asScala.map(l => l.getName -> l.getValue).toMap)

  private def sortDataPoints(dps: List[DataPointState]): List[DataPointState] =
    dps.sortBy(_.labels.toSeq.sortBy(_._1).mkString(","))

  private def scrapeToFamilyStates(promRegistry: PrometheusRegistry): List[FamilyState] =
    promRegistry
      .scrape()
      .asScala
      .toList
      .map {
        case s: CounterSnapshot =>
          FamilyState(
            s.getMetadata.getName,
            "COUNTER",
            s.getMetadata.getHelp,
            sortDataPoints(
              s.getDataPoints.asScala.toList.map(dp =>
                CounterDP(promLabelsToMap(dp.getLabels), dp.getValue, promExemplarToMap(dp.getExemplar))
              )
            )
          )
        case s: GaugeSnapshot =>
          FamilyState(
            s.getMetadata.getName,
            "GAUGE",
            s.getMetadata.getHelp,
            sortDataPoints(
              s.getDataPoints.asScala.toList.map(dp => GaugeDP(promLabelsToMap(dp.getLabels), dp.getValue))
            )
          )
        case s: HistogramSnapshot =>
          FamilyState(
            s.getMetadata.getName,
            "HISTOGRAM",
            s.getMetadata.getHelp,
            sortDataPoints(s.getDataPoints.asScala.toList.map { dp =>
              // v6's ClassicHistogramBucket doesn't carry a per-bucket exemplar — exemplars live on
              // the data point (`dp.getExemplars`) and are matched to buckets by observation-value
              // range (smallest bucket whose upper-bound ≥ value, i.e. exclusive-lower, inclusive-upper).
              val classic =
                if (dp.hasClassicHistogramData) {
                  val sortedBuckets = dp.getClassicBuckets.asScala.toList.sortBy(_.getUpperBound)
                  val lowerBounds   = Double.NegativeInfinity +: sortedBuckets.map(_.getUpperBound).init
                  val allExemplars  = Option(dp.getExemplars).map(_.asScala.toList).getOrElse(Nil)
                  Some(sortedBuckets.zip(lowerBounds).map { case (b, lower) =>
                    val maybeExemplar = allExemplars
                      .find(e => e.getValue > lower && e.getValue <= b.getUpperBound)
                      .map(e => e.getLabels.asScala.map(l => l.getName -> l.getValue).toMap)
                    ClassicBucket(b.getUpperBound, b.getCount, maybeExemplar)
                  })
                } else None
              val native =
                if (dp.hasNativeHistogramData) Some(NativeHistogramState(schema = dp.getNativeSchema)) else None
              HistogramDP(promLabelsToMap(dp.getLabels), dp.getCount, dp.getSum, classic, native)
            })
          )
        case s: SummarySnapshot =>
          FamilyState(
            s.getMetadata.getName,
            "SUMMARY",
            s.getMetadata.getHelp,
            sortDataPoints(s.getDataPoints.asScala.toList.map { dp =>
              val quantiles = dp.getQuantiles.asScala.map(q => q.getQuantile -> q.getValue).toMap
              SummaryDP(promLabelsToMap(dp.getLabels), dp.getCount, dp.getSum, quantiles)
            })
          )
        case s: InfoSnapshot =>
          FamilyState(
            s.getMetadata.getName,
            "INFO",
            s.getMetadata.getHelp,
            sortDataPoints(s.getDataPoints.asScala.toList.map(dp => InfoDP(promLabelsToMap(dp.getLabels))))
          )
        case other =>
          throw new NotImplementedError(
            s"scrapeToFamilyStates: no FamilyState translation for ${other.getClass.getSimpleName}; extend the model"
          )
      }
      .sortBy(_.name)

  // ─── suite-local tests ────────────────────────────────────────────────────────────────────────────

  private def buildEvicting(promRegistry: PrometheusRegistry, ttl: FiniteDuration): Resource[IO, MetricFactory[IO]] =
    JavaMetricRegistry
      .Builder[IO]()
      .withRegistry(promRegistry)
      .withStaleSeriesEviction(ttl)
      .build
      .map(MetricFactory.builder.build[IO](_))

  private def evictingScrape(evicting: EvictingCollector): Map[String, Double] =
    evicting.collect() match {
      case s: CounterSnapshot =>
        s.getDataPoints.asScala.map(dp => promLabelsToMap(dp.getLabels)("status") -> dp.getValue).toMap
      case other => fail(s"expected CounterSnapshot, got $other")
    }

  test("EvictingCollector evicts stale label sets at scrape time, exposing them one final time") {
    var now      = 0L
    val counter  = PCounter.builder().name("evict_unit_total").help("eviction").labelNames("status").build()
    val evicting = new EvictingCollector(counter, 100.nanos, () => now)

    def write(status: String): Unit = {
      evicting.touch(Array(status))
      counter.labelValues(status).inc()
    }

    write("idle")
    write("active")
    now = 80
    assertEquals(evictingScrape(evicting), Map("idle" -> 1.0, "active" -> 1.0))

    now = 90
    write("active")
    now = 150
    assertEquals(evictingScrape(evicting), Map("idle" -> 1.0, "active" -> 2.0))
    assertEquals(evictingScrape(evicting), Map("active" -> 2.0))

    write("idle")
    assertEquals(evictingScrape(evicting), Map("idle" -> 1.0, "active" -> 2.0))
  }

  test("EvictingCollector never evicts label sets that have not been written to") {
    var now      = 0L
    val counter  = PCounter.builder().name("evict_untouched_total").help("eviction").labelNames("status").build()
    val evicting = new EvictingCollector(counter, 100.nanos, () => now)

    counter.labelValues("preinitialised").inc()
    now = 1000
    assertEquals(evictingScrape(evicting), Map("preinitialised" -> 1.0))
    assertEquals(evictingScrape(evicting), Map("preinitialised" -> 1.0))
  }

  test("withStaleSeriesEviction evicts idle series for every stateful metric type and recreates them on write") {
    val promRegistry = new PrometheusRegistry()
    val ttl          = 50.millis
    val names        = List("evict_counter", "evict_gauge", "evict_histogram", "evict_summary")

    def dataPoints: IO[List[List[DataPointState]]] =
      IO.delay {
        val states = scrapeToFamilyStates(promRegistry)
        names.map(n => states.find(_.name == n).map(_.dataPoints).getOrElse(Nil))
      }

    val metrics = for {
      factory <- buildEvicting(promRegistry, ttl)
      counter <- factory.counter("evict_counter_total").help("eviction").label[String]("status").build
      gauge   <- factory.gauge("evict_gauge").help("eviction").label[String]("status").build
      histogram <-
        factory.histogram("evict_histogram").help("eviction").buckets(NonEmptySeq.one(1.0)).label[String]("status").build
      summary <- factory.summary("evict_summary").help("eviction").label[String]("status").build
    } yield (counter, gauge, histogram, summary)

    metrics.use { case (counter, gauge, histogram, summary) =>
      val writeAll =
        counter.inc("a") >> gauge.set(1.0, "a") >> histogram.observe(1.0, "a") >> summary.observe(1.0, "a")

      for {
        _       <- writeAll
        before  <- dataPoints
        _       <- IO.sleep(ttl * 3)
        _       <- dataPoints
        evicted <- dataPoints
        _       <- writeAll
        revived <- dataPoints
      } yield {
        assert(before.forall(_.nonEmpty), s"expected data points for all metrics before eviction, got $before")
        assert(evicted.forall(_.isEmpty), s"expected all series evicted after two scrapes past the TTL, got $evicted")
        assert(revived.forall(_.nonEmpty), s"expected all series recreated by the next write, got $revived")
      }
    }
  }

  test("withStaleSeriesEviction leaves metrics without dynamic labels untouched") {
    val promRegistry = new PrometheusRegistry()
    val ttl          = 50.millis
    val names        = List("evict_plain", "evict_common")

    def dataPoints: IO[List[List[DataPointState]]] =
      IO.delay {
        val states = scrapeToFamilyStates(promRegistry)
        names.map(n => states.find(_.name == n).map(_.dataPoints).getOrElse(Nil))
      }

    val metrics = for {
      factory      <- buildEvicting(promRegistry, ttl)
      commonFactory = factory.withCommonLabels(Metric.CommonLabels(Label.Name("app") -> "test"))
      plain        <- factory.counter("evict_plain_total").help("eviction").build
      common       <- commonFactory.counter("evict_common_total").help("eviction").build
    } yield (plain, common)

    metrics.use { case (plain, common) =>
      for {
        _     <- plain.inc >> common.inc
        _     <- IO.sleep(ttl * 3)
        _     <- dataPoints
        after <- dataPoints
      } yield assert(
        after.forall(_.exists { case CounterDP(_, 1.0, _) => true; case _ => false }),
        s"expected both counters to survive scrapes past the TTL, got $after"
      )
    }
  }

  test("EvictingCollector reclaims tracking entries whose data point was never created") {
    var now      = 0L
    val counter  = PCounter.builder().name("evict_orphan_total").help("eviction").labelNames("status").build()
    val evicting = new EvictingCollector(counter, 100.nanos, () => now)

    evicting.touch(Array("rejected"))
    assertEquals(evicting.trackedSeries, 1)
    assertEquals(evictingScrape(evicting), Map.empty[String, Double])
    assertEquals(evicting.trackedSeries, 1)

    now = 150
    assertEquals(evictingScrape(evicting), Map.empty[String, Double])
    assertEquals(evicting.trackedSeries, 0)
  }

  test("withStaleSeriesEviction rejects a non-positive TTL when the registry is built") {
    JavaMetricRegistry
      .Builder[IO]()
      .withRegistry(new PrometheusRegistry())
      .withStaleSeriesEviction(Duration.Zero)
      .build
      .use_
      .attempt
      .map {
        case Left(_: IllegalArgumentException) => ()
        case other                             => fail(s"expected an IllegalArgumentException, got $other")
      }
  }

  test("EvictingCollector preserves the registry's duplicate-name detection") {
    val promRegistry = new PrometheusRegistry()

    buildEvicting(promRegistry, 50.millis)
      .flatMap(_.counter("evict_dup_total").help("eviction").label[String]("status").build)
      .use { _ =>
        IO.delay(
          PCounter.builder().name("evict_dup_total").help("eviction").labelNames("status").register(promRegistry)
        ).attempt
          .map(res => assert(res.isLeft, "expected duplicate registration through the wrapper to be rejected"))
      }
  }

}
