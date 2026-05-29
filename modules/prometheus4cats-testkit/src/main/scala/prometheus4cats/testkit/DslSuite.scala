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

package prometheus4cats.testkit

import scala.concurrent.duration._

import cats.data.NonEmptyList
import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._

import munit.CatsEffectSuite
import prometheus4cats._

/** Cross-backend test suite for the user-facing `MetricFactory` DSL.
  *
  * Backends provide a fresh `MetricFactory.WithCallbacks[IO]` `Resource` plus a way to snapshot the registry's whole
  * observable state into the uniform `List[FamilyState]` representation defined here. The tests then exercise the DSL
  * surface (counter / gauge / histogram / native histogram / dual-mode / summary / info / callbacks / metric-collection
  * / collision contract) and assert against the snapshot as a literal — no narrow per-type getters.
  *
  * Unlike [[MetricRegistrySuite]] which pins the `MetricRegistry[F]` *trait* contract by calling low-level
  * `createAndRegisterDouble*` methods directly, this suite pins the *DSL → trait* handshake: every test goes through
  * `factory.<kind>(name).…build` so a backend can fail this suite even when [[MetricRegistrySuite]] passes (e.g. if the
  * DSL routes labels incorrectly into the registry).
  *
  * `getRegistryState` is parameterless — the backend is expected to close over its underlying registry (typically via a
  * `Ref` set inside the `resource` acquire) so that `getRegistryState` reads the registry that the *current* test is
  * exercising. Tests only call `getRegistryState` inside `resource.use { … }`, so it always runs within a live
  * registry's scope.
  *
  * A few protected `def`s capture values that may vary between backends (default native-histogram schema, collision
  * error messages). They're concrete with the current backend's defaults; future backends with different conventions
  * can override them.
  */
@SuppressWarnings(Array("all"))
trait DslSuite { self: CatsEffectSuite =>

  // full-state snapshot model

  sealed trait DataPointState { def labels: Map[String, String] }

  case class CounterDP(labels: Map[String, String], value: Double, exemplar: Option[Map[String, String]])
      extends DataPointState

  case class GaugeDP(labels: Map[String, String], value: Double) extends DataPointState

  case class ClassicBucket(upperBound: Double, count: Long, exemplar: Option[Map[String, String]])

  /** Minimal projection of a native-histogram data point — only `schema` is asserted today. */
  case class NativeHistogramState(schema: Int)

  case class HistogramDP(
      labels: Map[String, String],
      count: Long,
      sum: Double,
      classic: Option[List[ClassicBucket]],
      native: Option[NativeHistogramState]
  ) extends DataPointState

  case class SummaryDP(labels: Map[String, String], count: Long, sum: Double, quantiles: Map[Double, Double])
      extends DataPointState

  case class InfoDP(labels: Map[String, String]) extends DataPointState

  case class FamilyState(name: String, `type`: String, help: String, dataPoints: List[DataPointState])

  // abstract backend hooks

  def resource: Resource[IO, MetricFactory.WithCallbacks[IO]]

  def getRegistryState: IO[List[FamilyState]]

  // backend-tunable defaults

  /** Default `initialSchema` a backend picks for a `NativeHistogram` when none is configured. */
  protected def defaultNativeSchema: Int = 5

  /** Fixed `Exemplar[IO]` brought into implicit scope for every test. Counter `.inc` and histogram `.observe` calls
    * attach this exemplar when the DSL routes through the appropriate registry method.
    */
  implicit protected val exemplar: Exemplar[IO] = new Exemplar[IO] {
    override def get: IO[Option[Exemplar.Labels]] =
      IO(Exemplar.Labels.of(Exemplar.LabelName("trace_id") -> "abc123").toOption)
  }

  /** Concrete value the implicit `exemplar` returns — handy for asserting against in expected literals. */
  protected val exemplarLabels: Map[String, String] = Map("trace_id" -> "abc123")

  /** Error message a backend raises when a `factory.<kind>(name).callback(...)` is registered against a name already
    * owned by an active (push) metric. Backends that route through the prometheus4cats core get this for free.
    */
  protected def metricCollisionMessage(renderedName: String): String =
    s"A metric with the same name as '$renderedName' is already registered with different labels and/or type"

  // helpers

  /** Replaces the algorithm-dependent quantile *values* of every summary data point with 0.0, leaving count, sum, and
    * the declared quantile *levels* intact. Use when a live `Summary` records observations — the streaming algorithm's
    * output is implementation-defined.
    */
  protected def zeroSummaryQuantiles(state: List[FamilyState]): List[FamilyState] =
    state.map(fs =>
      fs.copy(dataPoints = fs.dataPoints.map {
        case s: SummaryDP => s.copy(quantiles = s.quantiles.view.mapValues(_ => 0.0).toMap)
        case other        => other
      })
    )

  // counter

  test("counter — inc, scrape returns the incremented value") {
    resource.use { factory =>
      factory
        .counter("test_counter_total")
        .ofDouble
        .help("test counter")
        .label[String]("variant")
        .build
        .use { c =>
          for {
            _     <- c.inc(1.0, "alpha")
            snap1 <- getRegistryState
            _     <- c.inc(2.0, "alpha")
            snap2 <- getRegistryState
          } yield {
            def expected(value: Double) = List(
              FamilyState(
                name = "test_counter",
                `type` = "COUNTER",
                help = "test counter",
                dataPoints = List(CounterDP(Map("variant" -> "alpha"), value, None))
              )
            )
            assertEquals(snap1, expected(1.0))
            assertEquals(snap2, expected(3.0))
          }
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))

    }
  }

  // Each exemplar test below uses a freshly-built counter so v6's exemplar retention period (the
  // minimum interval between consecutive exemplars) doesn't suppress the assertion. Combining multiple
  // exemplar-attaching calls into one counter only the first one would survive.

  test("counter — incWithExemplar attaches the implicit Exemplar to the snapshot") {
    resource.use { factory =>
      factory
        .counter("test_implicit_exemplar_counter_total")
        .ofDouble
        .help("test implicit exemplar counter")
        .build
        .use { c =>
          for {
            _    <- c.incWithExemplar(1.0)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_implicit_exemplar_counter",
                `type` = "COUNTER",
                help = "test implicit exemplar counter",
                dataPoints = List(CounterDP(Map.empty, 1.0, Some(exemplarLabels)))
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("counter — incProvidedExemplar attaches the explicitly-supplied Exemplar.Labels") {
    resource.use { factory =>
      val explicitLabels = Exemplar.Labels
        .of(Exemplar.LabelName("trace_id") -> "def456", Exemplar.LabelName("span_id") -> "span789")
        .toOption
        .get
      factory
        .counter("test_provided_exemplar_counter_total")
        .ofDouble
        .help("test provided exemplar counter")
        .build
        .use { c =>
          for {
            _    <- c.incProvidedExemplar(1.0, Some(explicitLabels))
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_provided_exemplar_counter",
                `type` = "COUNTER",
                help = "test provided exemplar counter",
                dataPoints = List(
                  CounterDP(Map.empty, 1.0, Some(Map("trace_id" -> "def456", "span_id" -> "span789")))
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("counter — incWithSampledExemplar attaches the sampler's output to the snapshot") {
    resource.use { factory =>
      val sampledLabels = Exemplar.Labels
        .of(Exemplar.LabelName("trace_id") -> "sampled001")
        .toOption
        .get
      implicit val sampler: ExemplarSampler.Counter[IO, Double] = new ExemplarSampler.Counter[IO, Double] {
        // Both overloads always emit the same exemplar — enough to verify the sampler is being consulted.
        override def sample(previous: Option[Exemplar.Data]): IO[Option[Exemplar.Data]] =
          IO.pure(Some(Exemplar.Data(sampledLabels, java.time.Instant.now)))
        override def sample(value: Double, previous: Option[Exemplar.Data]): IO[Option[Exemplar.Data]] =
          IO.pure(Some(Exemplar.Data(sampledLabels, java.time.Instant.now)))
      }
      factory
        .counter("test_sampled_exemplar_counter_total")
        .ofDouble
        .help("test sampled exemplar counter")
        .build
        .use { c =>
          for {
            _    <- c.incWithSampledExemplar(1.0)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_sampled_exemplar_counter",
                `type` = "COUNTER",
                help = "test sampled exemplar counter",
                dataPoints = List(CounterDP(Map.empty, 1.0, Some(Map("trace_id" -> "sampled001"))))
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("counter callback — scrape invokes the user callback and propagates the value to the snapshot") {
    resource.use { factory =>
      val iterator = Iterator((42.0, "alpha"), (45.0, "alpha"))
      val callback = IO.delay(NonEmptyList.of(iterator.next()))
      factory
        .counter("test_callback_counter_total")
        .ofDouble
        .help("test counter callback")
        .label[String]("variant")
        .callback(callback)
        .build
        .use { _ =>
          def expected(value: Double, label: String) = List(
            FamilyState(
              name = "test_callback_counter",
              `type` = "COUNTER",
              help = "test counter callback",
              dataPoints = List(
                CounterDP(Map("variant" -> label), value, None)
              )
            )
          )
          for {
            snap1 <- getRegistryState
            snap2 <- getRegistryState
          } yield {
            assertEquals(snap1, expected(42.0, "alpha"))
            assertEquals(snap2, expected(45.0, "alpha"))
          }
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // gauge

  test("gauge — inc, dec, and set are each reflected in the scrape") {
    resource.use { factory =>
      factory
        .gauge("test_gauge")
        .ofDouble
        .help("test gauge")
        .build
        .use { g =>
          for {
            _  <- g.inc(5.0)
            s1 <- getRegistryState
            _  <- g.dec(2.0)
            s2 <- getRegistryState
            _  <- g.set(10.0)
            s3 <- getRegistryState
          } yield {
            def expected(value: Double) = List(
              FamilyState(
                name = "test_gauge",
                `type` = "GAUGE",
                help = "test gauge",
                dataPoints = List(GaugeDP(Map.empty, value))
              )
            )
            assertEquals(s1, expected(5.0))
            assertEquals(s2, expected(3.0))
            assertEquals(s3, expected(10.0))
          }
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("gauge callback — every scrape re-invokes the callback and propagates the current value") {
    resource.use { factory =>
      val iterator = Iterator(
        NonEmptyList.of((50.0, "n0"), (100.0, "n1")),
        NonEmptyList.of((75.0, "n0"), (150.0, "n1"))
      )
      val callback = IO.delay(iterator.next())
      factory
        .gauge("test_callback_gauge")
        .ofDouble
        .help("test gauge callback")
        .label[String]("node")
        .callback(callback)
        .build
        .use { _ =>
          def expected(v0: Double, v1: Double) = List(
            FamilyState(
              name = "test_callback_gauge",
              `type` = "GAUGE",
              help = "test gauge callback",
              dataPoints = List(
                GaugeDP(Map("node" -> "n0"), v0),
                GaugeDP(Map("node" -> "n1"), v1)
              )
            )
          )
          for {
            snap1 <- getRegistryState
            snap2 <- getRegistryState
          } yield {
            assertEquals(snap1, expected(50.0, 100.0))
            assertEquals(snap2, expected(75.0, 150.0))
          }
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // histogram

  test("classic histogram — scrape produces classic buckets, no native data") {
    resource.use { factory =>
      factory
        .histogram("test_classic_histogram")
        .ofDouble
        .help("test classic histogram")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0, 5.0))
        .build
        .use { h =>
          for {
            _    <- h.observe(0.05) >> h.observe(0.3) >> h.observe(0.7) >> h.observe(2.0)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_classic_histogram",
                `type` = "HISTOGRAM",
                help = "test classic histogram",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty,
                    count = 4L,
                    sum = 3.05,
                    classic = Some(
                      List(
                        ClassicBucket(0.1, 1L, None),
                        ClassicBucket(0.5, 1L, None),
                        ClassicBucket(1.0, 1L, None),
                        ClassicBucket(5.0, 1L, None),
                        ClassicBucket(Double.PositiveInfinity, 0L, None)
                      )
                    ),
                    native = None
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // Each histogram exemplar test below uses a single `observe*` call so v6's exemplar retention
  // period doesn't suppress subsequent exemplar updates. The observed value lands in exactly one
  // classic bucket — that bucket carries the exemplar; the other buckets stay `None`.

  test("classic histogram — observeWithExemplar attaches the implicit Exemplar to the matching bucket") {
    resource.use { factory =>
      factory
        .histogram("test_implicit_exemplar_histogram")
        .ofDouble
        .help("test implicit exemplar histogram")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0, 5.0))
        .build
        .use { h =>
          for {
            _    <- h.observeWithExemplar(0.05)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_implicit_exemplar_histogram",
                `type` = "HISTOGRAM",
                help = "test implicit exemplar histogram",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty,
                    count = 1L,
                    sum = 0.05,
                    classic = Some(
                      List(
                        ClassicBucket(0.1, 1L, Some(exemplarLabels)),
                        ClassicBucket(0.5, 0L, None),
                        ClassicBucket(1.0, 0L, None),
                        ClassicBucket(5.0, 0L, None),
                        ClassicBucket(Double.PositiveInfinity, 0L, None)
                      )
                    ),
                    native = None
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("classic histogram — observeProvidedExemplar attaches the explicitly-supplied Exemplar.Labels") {
    resource.use { factory =>
      val explicitLabels = Exemplar.Labels
        .of(Exemplar.LabelName("trace_id") -> "def456", Exemplar.LabelName("span_id") -> "span789")
        .toOption
        .get
      factory
        .histogram("test_provided_exemplar_histogram")
        .ofDouble
        .help("test provided exemplar histogram")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0, 5.0))
        .build
        .use { h =>
          for {
            _    <- h.observeProvidedExemplar(0.3, Some(explicitLabels))
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_provided_exemplar_histogram",
                `type` = "HISTOGRAM",
                help = "test provided exemplar histogram",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty,
                    count = 1L,
                    sum = 0.3,
                    classic = Some(
                      List(
                        ClassicBucket(0.1, 0L, None),
                        ClassicBucket(0.5, 1L, Some(Map("trace_id" -> "def456", "span_id" -> "span789"))),
                        ClassicBucket(1.0, 0L, None),
                        ClassicBucket(5.0, 0L, None),
                        ClassicBucket(Double.PositiveInfinity, 0L, None)
                      )
                    ),
                    native = None
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("classic histogram — observeWithSampledExemplar attaches the sampler's output to the matching bucket") {
    resource.use { factory =>
      val sampledLabels = Exemplar.Labels
        .of(Exemplar.LabelName("trace_id") -> "sampled001")
        .toOption
        .get
      implicit val sampler: ExemplarSampler.Histogram[IO, Double] = new ExemplarSampler.Histogram[IO, Double] {
        override def sample(
            value: Double,
            buckets: NonEmptySeq[Double],
            previous: Option[Exemplar.Data]
        ): IO[Option[Exemplar.Data]] =
          IO.pure(Some(Exemplar.Data(sampledLabels, java.time.Instant.now)))
      }
      factory
        .histogram("test_sampled_exemplar_histogram")
        .ofDouble
        .help("test sampled exemplar histogram")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0, 5.0))
        .build
        .use { h =>
          for {
            _    <- h.observeWithSampledExemplar(0.7)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_sampled_exemplar_histogram",
                `type` = "HISTOGRAM",
                help = "test sampled exemplar histogram",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty,
                    count = 1L,
                    sum = 0.7,
                    classic = Some(
                      List(
                        ClassicBucket(0.1, 0L, None),
                        ClassicBucket(0.5, 0L, None),
                        ClassicBucket(1.0, 1L, Some(Map("trace_id" -> "sampled001"))),
                        ClassicBucket(5.0, 0L, None),
                        ClassicBucket(Double.PositiveInfinity, 0L, None)
                      )
                    ),
                    native = None
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("native histogram — scrape produces native bucket data, no classic buckets") {
    resource.use { factory =>
      factory
        .nativeHistogram("test_native_histogram")
        .ofDouble
        .help("test native histogram")
        .build
        .use { h =>
          for {
            _    <- h.observe(0.05) >> h.observe(0.3) >> h.observe(0.7) >> h.observe(2.0)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_native_histogram",
                `type` = "HISTOGRAM",
                help = "test native histogram",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty, count = 4L, sum = 3.05, classic = None,
                    native = Some(NativeHistogramState(defaultNativeSchema))
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("dual-mode histogram (.withNative) — scrape produces BOTH classic and native data") {
    resource.use { factory =>
      factory
        .histogram("test_dual_histogram")
        .ofDouble
        .help("test dual-mode (NHCB-friendly) histogram")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0, 5.0))
        .withNative
        .build
        .use { h =>
          for {
            _    <- h.observe(0.05) >> h.observe(0.3) >> h.observe(0.7) >> h.observe(2.0)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_dual_histogram",
                `type` = "HISTOGRAM",
                help = "test dual-mode (NHCB-friendly) histogram",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty,
                    count = 4L,
                    sum = 3.05,
                    classic = Some(
                      List(
                        ClassicBucket(0.1, 1L, None),
                        ClassicBucket(0.5, 1L, None),
                        ClassicBucket(1.0, 1L, None),
                        ClassicBucket(5.0, 1L, None),
                        ClassicBucket(Double.PositiveInfinity, 0L, None)
                      )
                    ),
                    native = Some(NativeHistogramState(defaultNativeSchema))
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("native histogram — custom NativeHistogram config propagates initialSchema to the scraped snapshot") {
    resource.use { factory =>
      factory
        .nativeHistogram("test_native_tuned", NativeHistogram.Default.withInitialSchema(3))
        .ofDouble
        .help("native with custom schema")
        .build
        .use { h =>
          for {
            _    <- h.observe(1.0)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_native_tuned",
                `type` = "HISTOGRAM",
                help = "native with custom schema",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty, count = 1L, sum = 1.0, classic = None,
                    native = Some(NativeHistogramState(schema = 3))
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("histogram callback — every scrape re-invokes the callback and propagates the current value") {
    resource.use { factory =>
      // bucketValues are CUMULATIVE counts indexed by (declared-buckets ++ +Inf) — so for buckets
      // 0.1, 1.0, 5.0 the cumulative counts [1, 2, 3, 3] mean (≤0.1, ≤1.0, ≤5.0, ≤+Inf) which the
      // exposition writer flattens to per-bucket counts [1, 1, 1, 0] for the snapshot.
      val iterator = Iterator(
        NonEmptyList.of((Histogram.Value[Double](2.55, NonEmptySeq.of(1.0, 2.0, 3.0, 3.0)), "alpha")),
        NonEmptyList.of((Histogram.Value[Double](4.10, NonEmptySeq.of(2.0, 3.0, 4.0, 4.0)), "alpha"))
      )
      val callback = IO.delay(iterator.next())
      factory
        .histogram("test_callback_histogram")
        .ofDouble
        .help("test histogram callback")
        .buckets(NonEmptySeq.of(0.1, 1.0, 5.0))
        .label[String]("variant")
        .callback(callback)
        .build
        .use { _ =>
          def expected(count: Long, sum: Double, b1: Long, b2: Long, b3: Long, bInf: Long) = List(
            FamilyState(
              name = "test_callback_histogram",
              `type` = "HISTOGRAM",
              help = "test histogram callback",
              dataPoints = List(
                HistogramDP(
                  labels = Map("variant" -> "alpha"),
                  count = count,
                  sum = sum,
                  classic = Some(
                    List(
                      ClassicBucket(0.1, b1, None),
                      ClassicBucket(1.0, b2, None),
                      ClassicBucket(5.0, b3, None),
                      ClassicBucket(Double.PositiveInfinity, bInf, None)
                    )
                  ),
                  native = None
                )
              )
            )
          )
          for {
            snap1 <- getRegistryState
            snap2 <- getRegistryState
          } yield {
            // Cumulative [1, 2, 3, 3] → per-bucket [1, 1, 1, 0], count = 3, sum = 2.55
            assertEquals(snap1, expected(count = 3L, sum = 2.55, b1 = 1L, b2 = 1L, b3 = 1L, bInf = 0L))
            // Cumulative [2, 3, 4, 4] → per-bucket [2, 1, 1, 0], count = 4, sum = 4.10
            assertEquals(snap2, expected(count = 4L, sum = 4.10, b1 = 2L, b2 = 1L, b3 = 1L, bInf = 0L))
          }
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // summary

  test("summary — quantiles propagate and observations are aggregated") {
    resource.use { factory =>
      factory
        .summary("test_summary")
        .ofDouble
        .help("test summary")
        .quantile(Summary.Quantile.from(0.5).toOption.get, Summary.AllowedError.from(0.05).toOption.get)
        .quantile(Summary.Quantile.from(0.99).toOption.get, Summary.AllowedError.from(0.01).toOption.get)
        .build
        .use { sum =>
          for {
            _    <- sum.observe(0.1) >> sum.observe(0.5) >> sum.observe(1.0) >> sum.observe(2.5)
            snap <- getRegistryState.map(zeroSummaryQuantiles)
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_summary",
                `type` = "SUMMARY",
                help = "test summary",
                // Quantile *values* are zeroed by `zeroSummaryQuantiles` (streaming algorithm output is
                // implementation-defined); the assertion still pins count, sum and the declared levels.
                dataPoints = List(SummaryDP(Map.empty, 4L, 4.1, Map(0.5 -> 0.0, 0.99 -> 0.0)))
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("summary callback — every scrape re-invokes the callback and propagates the current value") {
    resource.use { factory =>
      val iterator = Iterator(
        NonEmptyList.of(
          (Summary.Value[Double](count = 10L, sum = 42.5, quantiles = Map(0.5 -> 1.2, 0.99 -> 9.5)), "alpha")
        ),
        NonEmptyList.of(
          (Summary.Value[Double](count = 20L, sum = 100.0, quantiles = Map(0.5 -> 2.4, 0.99 -> 18.0)), "alpha")
        )
      )
      val callback = IO.delay(iterator.next())
      factory
        .summary("test_callback_summary")
        .ofDouble
        .help("test summary callback")
        .label[String]("variant")
        .callback(callback)
        .build
        .use { _ =>
          def expected(count: Long, sum: Double, quantiles: Map[Double, Double]) = List(
            FamilyState(
              name = "test_callback_summary",
              `type` = "SUMMARY",
              help = "test summary callback",
              dataPoints = List(SummaryDP(Map("variant" -> "alpha"), count, sum, quantiles))
            )
          )
          for {
            snap1 <- getRegistryState
            snap2 <- getRegistryState
          } yield {
            assertEquals(snap1, expected(10L, 42.5, Map(0.5 -> 1.2, 0.99 -> 9.5)))
            assertEquals(snap2, expected(20L, 100.0, Map(0.5 -> 2.4, 0.99 -> 18.0)))
          }
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // info
  //
  // Upstream stores Info under its base name (without the `_info` suffix). The wire format still emits
  // `test_build_info{...} 1` — the suffix is added by the exposition writer, so the snapshot metadata
  // name is `test_build`.

  test("info — declared labels propagate to scrape output via setLabelValues") {
    resource.use { factory =>
      factory
        .info("test_build_info")
        .help("test build info")
        .label[String]("version")
        .label[String]("commit")
        .build
        .use { i =>
          for {
            _    <- i.info(("1.2.3", "abc1234"))
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_build",
                `type` = "INFO",
                help = "test build info",
                dataPoints = List(InfoDP(Map("version" -> "1.2.3", "commit" -> "abc1234")))
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // metric collection

  test("metric-collection callback — every scrape re-invokes the callback and propagates the current collection") {
    resource.use { factory =>
      def collection(counterValue: Double, gaugeValue: Double): MetricCollection =
        MetricCollection.empty
          .appendDoubleCounter(
            Counter.Name.unsafeFrom("collection_counter_total"),
            Metric.Help("test counter"),
            Map.empty[Label.Name, String],
            counterValue
          )
          .appendDoubleGauge(
            Gauge.Name.unsafeFrom("collection_gauge"),
            Metric.Help("test gauge"),
            Map.empty[Label.Name, String],
            gaugeValue
          )

      val iterator = Iterator(collection(42.0, 7.0), collection(100.0, 200.0))
      val callback = IO.delay(iterator.next())

      factory
        .metricCollectionCallback(callback)
        .build
        .use { _ =>
          def expected(counterValue: Double, gaugeValue: Double) = List(
            FamilyState(
              name = "collection_counter",
              `type` = "COUNTER",
              help = "test counter",
              dataPoints = List(CounterDP(Map.empty, counterValue, None))
            ),
            FamilyState(
              name = "collection_gauge",
              `type` = "GAUGE",
              help = "test gauge",
              dataPoints = List(GaugeDP(Map.empty, gaugeValue))
            )
          )
          for {
            snap1 <- getRegistryState
            snap2 <- getRegistryState
          } yield {
            assertEquals(snap1, expected(42.0, 7.0))
            assertEquals(snap2, expected(100.0, 200.0))
          }
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // timer
  //
  // `Timer` is a derived metric built on top of a Histogram via `.asTimer`. `recordTime` observes the
  // duration converted to seconds; `recordTimeWithExemplar` does the same and additionally attaches the
  // implicit `Exemplar[F]` to the bucket the duration lands in.

  test("histogram-backed Timer — recordTime observes the duration as seconds into the matching bucket") {
    resource.use { factory =>
      factory
        .histogram("test_timer_seconds")
        .ofDouble
        .help("test timer")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0))
        .asTimer
        .build
        .use { timer =>
          for {
            _    <- timer.recordTime(50.millis) // 0.05s → lands in bucket 0.1
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_timer_seconds",
                `type` = "HISTOGRAM",
                help = "test timer",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty,
                    count = 1L,
                    sum = 0.05,
                    classic = Some(
                      List(
                        ClassicBucket(0.1, 1L, None),
                        ClassicBucket(0.5, 0L, None),
                        ClassicBucket(1.0, 0L, None),
                        ClassicBucket(Double.PositiveInfinity, 0L, None)
                      )
                    ),
                    native = None
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("native histogram-backed Timer — recordTime observes the duration as seconds into native bucket data") {
    resource.use { factory =>
      factory
        .nativeHistogram("test_native_timer_seconds")
        .ofDouble
        .help("test native timer")
        .asTimer
        .build
        .use { timer =>
          for {
            _    <- timer.recordTime(50.millis)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_native_timer_seconds",
                `type` = "HISTOGRAM",
                help = "test native timer",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty, count = 1L, sum = 0.05, classic = None,
                    native = Some(NativeHistogramState(defaultNativeSchema))
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test("histogram-backed Timer — recordTimeWithExemplar attaches the implicit Exemplar to the matching bucket") {
    resource.use { factory =>
      factory
        .histogram("test_timer_exemplar_seconds")
        .ofDouble
        .help("test timer exemplar")
        .buckets(NonEmptySeq.of(0.1, 0.5, 1.0))
        .asTimer
        .build
        .use { timer =>
          for {
            _    <- timer.recordTimeWithExemplar(50.millis)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_timer_exemplar_seconds",
                `type` = "HISTOGRAM",
                help = "test timer exemplar",
                dataPoints = List(
                  HistogramDP(
                    labels = Map.empty,
                    count = 1L,
                    sum = 0.05,
                    classic = Some(
                      List(
                        ClassicBucket(0.1, 1L, Some(exemplarLabels)),
                        ClassicBucket(0.5, 0L, None),
                        ClassicBucket(1.0, 0L, None),
                        ClassicBucket(Double.PositiveInfinity, 0L, None)
                      )
                    ),
                    native = None
                  )
                )
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // outcome recorder
  //
  // `OutcomeRecorder` is a derived metric built on top of a Counter via `.asOutcomeRecorder`. Each
  // surrounded operation routes to one of three `outcome_status` label values (succeeded / errored /
  // canceled); only the label value actually observed appears in the snapshot. `surroundWithExemplar`
  // attaches the implicit `Exemplar[F]` to the relevant counter sample when the corresponding
  // `recordExemplarOn*` flag is `true`.

  test("counter-backed OutcomeRecorder — surround increments the succeeded counter on F[_] success") {
    resource.use { factory =>
      factory
        .counter("test_outcome_total")
        .ofDouble
        .help("test outcome")
        .asOutcomeRecorder
        .build
        .use { recorder =>
          for {
            _    <- recorder.surround(IO.unit)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_outcome",
                `type` = "COUNTER",
                help = "test outcome",
                dataPoints = List(CounterDP(Map("outcome_status" -> "succeeded"), 1.0, None))
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  test(
    "counter-backed OutcomeRecorder — surroundWithExemplar attaches the implicit Exemplar to the succeeded counter"
  ) {
    resource.use { factory =>
      factory
        .counter("test_outcome_exemplar_total")
        .ofDouble
        .help("test outcome exemplar")
        .asOutcomeRecorder
        .build
        .use { recorder =>
          for {
            _    <- recorder.surroundWithExemplar(IO.unit, recordExemplarOnSucceeded = true)
            snap <- getRegistryState
          } yield assertEquals(
            snap,
            List(
              FamilyState(
                name = "test_outcome_exemplar",
                `type` = "COUNTER",
                help = "test outcome exemplar",
                dataPoints = List(CounterDP(Map("outcome_status" -> "succeeded"), 1.0, Some(exemplarLabels)))
              )
            )
          )
        }
        .flatMap(_ => getRegistryState)
        .map(snap => assertEquals(snap, Nil))
    }
  }

  // name collisions
  //
  // Reformulated through the DSL: `factory.counter(...)`/`.gauge(...)` for active registration,
  // `.callback(io)` for pull registration. The error messages assert through `metricCollisionMessage`
  // / `callbackCollisionMessage` so a backend whose error strings diverge can override them centrally
  // rather than override every test.

  private val collisionHelp: Metric.Help = Metric.Help.from("collision contract test").toOption.get

  test("name-collision: returns an existing metric when labels and name are the same") {
    resource.use { factory =>
      val mk = factory.counter("collision_reuse_total").ofDouble.help(collisionHelp).label[String]("region").build
      def expected(value: Double) = List(
        FamilyState(
          name = "collision_reuse",
          `type` = "COUNTER",
          help = "collision contract test",
          dataPoints = List(
            CounterDP(
              labels = Map("region" -> "x"),
              value = value,
              exemplar = None
            )
          )
        )
      )
      mk.use { c1 =>
        mk.use { c2 =>
          // both refcount holders alive → one family
          c2.inc(1.0, "x") >>
            getRegistryState.map(assertEquals(_, expected(1.0)))
        } >> c1.inc(1.0, "x") >>
          // inner release decremented to 1; family still registered
          getRegistryState.map(assertEquals(_, expected(2.0)))
      } >>
        // outer release decremented to 0; family unregistered
        getRegistryState.map(assertEquals(_, Nil))
    }
  }

  test("name-collision: fails to build a metric when a callback of the same name exists") {
    resource.use { factory =>
      val callback = factory
        .counter("collision_metric_after_callback_total")
        .ofDouble
        .help(collisionHelp)
        .label[String]("region")
        .callback(IO.pure(NonEmptyList.one((0.0, "x"))))
        .build
      val metric = factory
        .counter("collision_metric_after_callback_total")
        .ofDouble
        .help(collisionHelp)
        .label[String]("region")
        .build

      (callback >> metric).use_.attempt.map { res =>
        assertEquals(
          res.leftMap(_.getMessage),
          Left(s"A callback with the same name as 'collision_metric_after_callback_total' is already registered with different labels and/or type")
        )
      }
    }
  }

  test("name-collision: fails to build a callback when a metric of the same name exists") {
    resource.use { factory =>
      val metric = factory
        .counter("collision_callback_after_metric_total")
        .ofDouble
        .help(collisionHelp)
        .label[String]("region")
        .build
      val callback = factory
        .counter("collision_callback_after_metric_total")
        .ofDouble
        .help(collisionHelp)
        .label[String]("region")
        .callback(IO.pure(NonEmptyList.one((0.0, "x"))))
        .build

      (metric >> callback).use_.attempt.map { res =>
        assertEquals(
          res.leftMap(_.getMessage),
          Left(metricCollisionMessage("collision_callback_after_metric_total"))
        )
      }
    }
  }

  test("name-collision: fails when a metric with the same name and different labels") {
    resource.use { factory =>
      val m1 = factory
        .counter("collision_different_labels_total")
        .ofDouble
        .help(collisionHelp)
        .label[String]("region")
        .build
      val m2 = factory
        .counter("collision_different_labels_total")
        .ofDouble
        .help(collisionHelp)
        .label[String]("different")
        .build

      (m1 >> m2).use_.attempt.map { res =>
        assertEquals(
          res.leftMap(_.getMessage),
          Left(metricCollisionMessage("collision_different_labels_total"))
        )
      }
    }
  }

  test("name-collision: counter callback emitting duplicate label values within one NonEmptyList") {
    resource.use { factory =>
      // Two tuples with the SAME label value `x` but different metric values. The registry sees two
      // CounterDataPointSnapshots with identical label sets in a single scrape — a duplicate-series
      // condition the wire format normally rejects.
      val callback = IO.pure(NonEmptyList.of((1.0, "x"), (2.0, "x")))
      factory
        .counter("collision_dup_label_total")
        .ofDouble
        .help(collisionHelp)
        .label[String]("name")
        .callback(callback)
        .build
        .use { _ =>
          getRegistryState.attempt.map { res =>
            // Behaviour to pin once we observe it on a test run. Likely one of:
            //   - registry raises at scrape time (`res` is Left with an upstream-validation message)
            //   - registry silently keeps the LAST entry (Right with one CounterDP value=2.0)
            //   - registry emits both as duplicate samples (Right with two CounterDPs sharing labels)
            // Asserting `isLeft` for now — adjust to the exact shape after first run.
            assert(res.isLeft, s"expected duplicate-label callback emission to fail, got: $res")
          }
        }
    }
  }

  test("name-collision: fails when a metric with the same name and different type") {
    resource.use { factory =>
      val counter = factory
        .counter("collision_different_type_total")
        .ofDouble
        .help(collisionHelp)
        .label[String]("region")
        .build
      val gauge = factory
        .gauge("collision_different_type")
        .ofDouble
        .help(collisionHelp)
        .label[String]("region")
        .build

      (counter >> gauge).use_.attempt.map { res =>
        assertEquals(
          res.leftMap(_.getMessage),
          Left(metricCollisionMessage("collision_different_type"))
        )
      }
    }
  }

}
