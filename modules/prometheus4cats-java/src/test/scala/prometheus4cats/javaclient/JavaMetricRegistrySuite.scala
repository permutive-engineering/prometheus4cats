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

import cats.effect.IO
import cats.effect.Ref
import cats.effect.kernel.Resource

import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot
import io.prometheus.metrics.model.snapshots.InfoSnapshot
import io.prometheus.metrics.model.snapshots.SummarySnapshot
import munit.CatsEffectSuite
import prometheus4cats._
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

  test("withStaleSeriesEviction removes idle label sets and recreates them on the next write") {
    val promRegistry = new PrometheusRegistry()

    def series(labelValue: String): Option[Double] =
      promRegistry
        .scrape()
        .asScala
        .toList
        .collectFirst { case s: CounterSnapshot =>
          s.getDataPoints.asScala.collectFirst {
            case dp if promLabelsToMap(dp.getLabels).get("status").contains(labelValue) => dp.getValue
          }
        }
        .flatten

    JavaMetricRegistry
      .Builder[IO]()
      .withRegistry(promRegistry)
      .withStaleSeriesEviction(500.millis)
      .build
      .map(MetricFactory.builder.build[IO](_))
      .flatMap(_.counter("eviction_test_total").help("eviction test").label[String]("status").build)
      .use { counter =>
        for {
          _      <- counter.inc("idle") >> counter.inc("active")
          before <- IO.delay((series("idle"), series("active")))
          _      <- (IO.sleep(100.millis) >> counter.inc("active")).replicateA_(10)
          after  <- IO.delay((series("idle"), series("active")))
          _      <- counter.inc("idle")
          revived = series("idle")
        } yield {
          assertEquals(before, (Some(1.0), Some(1.0)))
          assertEquals(after._1, None)
          assert(after._2.exists(_ >= 10.0))
          assertEquals(revived, Some(1.0))
        }
      }
  }

}
