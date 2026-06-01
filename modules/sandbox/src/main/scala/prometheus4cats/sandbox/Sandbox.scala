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

package prometheus4cats.sandbox

import java.lang.management.ManagementFactory
import java.time.Instant

import scala.concurrent.duration._

import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import cats.effect.std.Random

import io.prometheus.metrics.exporter.httpserver.HTTPServer
import io.prometheus.metrics.model.registry.PrometheusRegistry
import prometheus4cats.Exemplar
import prometheus4cats.ExemplarSampler
import prometheus4cats.Metric
import prometheus4cats.MetricFactory
import prometheus4cats.Summary
import prometheus4cats.javaclient.JavaMetricRegistry

object Sandbox extends IOApp.Simple {

  /** Latency-style bucket layout, in seconds. Spans 5ms..10s, finer-grained at the low end. */
  private val LatencyBuckets: NonEmptySeq[Double] =
    NonEmptySeq.of(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)

  /** Wrap the upstream HTTPServer's blocking `Builder.buildAndStart()` in a Resource so it's torn down cleanly on app
    * shutdown.
    */
  private def httpServer(promRegistry: PrometheusRegistry, port: Int): Resource[IO, HTTPServer] =
    Resource.make(
      IO.blocking(
        HTTPServer.builder().port(port).registry(promRegistry).buildAndStart()
      )
    )(server => IO.blocking(server.close()))

  override def run: IO[Unit] = {
    val promRegistry = new PrometheusRegistry()

    val app = for {
      registry <- JavaMetricRegistry.Builder[IO]().withRegistry(promRegistry).build
      _        <- httpServer(promRegistry, port = 9400)
      factory   = MetricFactory.builder.withPrefix(Metric.Prefix("sandbox")).build(registry)

      random <- Resource.eval(Random.scalaUtilRandom[IO])

      counter <- factory.counter("counter_total").ofDouble.help("Test counter").label[String]("label").build

      sampledCounter <- factory
                          .counter("sampled_counter_total")
                          .ofDouble
                          .help("Counter with downsampled exemplars (~1 in 10)")
                          .label[String]("label")
                          .build

      classic <- factory
                   .histogram("classic_histogram_seconds")
                   .ofDouble
                   .help("Classic histogram with curated buckets")
                   .buckets(LatencyBuckets)
                   .build

      native <- factory
                  .nativeHistogram("native_histogram_seconds")
                  .ofDouble
                  .help("Native (sparse / exponential) histogram, no declared buckets")
                  .build

      dual <- factory
                .histogram("dual_histogram_seconds")
                .ofDouble
                .help("Dual-mode histogram: classic + native from one declaration")
                .buckets(LatencyBuckets)
                .withNative
                .build

      gauge <- factory.gauge("gauge_value").ofDouble.help("Test gauge — sine wave 0..100").build

      summary <- factory
                   .summary("summary_seconds")
                   .ofDouble
                   .help("Test summary with p50 / p90 / p99 quantiles")
                   .quantile(
                     Summary.Quantile.from(0.5).toOption.get,
                     Summary.AllowedError.from(0.05).toOption.get
                   )
                   .quantile(
                     Summary.Quantile.from(0.9).toOption.get,
                     Summary.AllowedError.from(0.01).toOption.get
                   )
                   .quantile(
                     Summary.Quantile.from(0.99).toOption.get,
                     Summary.AllowedError.from(0.001).toOption.get
                   )
                   .build

      info <- factory
                .info("build_info")
                .help("Build info — set once at startup, never changes")
                .label[String]("version")
                .label[String]("commit")
                .label[String]("environment")
                .build

      timer <- factory
                 .histogram("operation_duration_seconds")
                 .ofDouble
                 .help("Histogram-backed Timer (.asTimer) — observes durations of timed operations")
                 .buckets(LatencyBuckets)
                 .asTimer
                 .build

      outcomeRecorder <-
        factory
          .counter("operation_total")
          .ofDouble
          .help("Counter-backed OutcomeRecorder (.asOutcomeRecorder) — counts succeeded/errored/canceled outcomes")
          .asOutcomeRecorder
          .build

      // Pull-mode example: a gauge whose value is computed at scrape time. No state to maintain —
      // the JVM is the source of truth, we just surface it. Build yields `Unit` (callbacks don't
      // expose a metric handle), so we discard with `_ <-`.
      _ <-
        factory
          .gauge("process_uptime_seconds")
          .ofDouble
          .help("Process uptime in seconds (pull-mode gauge callback example)")
          .callback(IO.delay(ManagementFactory.getRuntimeMXBean.getUptime / 1000.0))
          .build
    } yield (counter, sampledCounter, classic, native, dual, gauge, summary, info, timer, outcomeRecorder, random)

    app.use {
      case (counter, sampledCounter, classic, native, dual, gauge, summary, info, timer, outcomeRecorder, random) =>
        // Per-call fresh trace_id — every observation attaches a different exemplar so the heatmap
        // and per-bucket exemplar diamonds in Grafana stay populated rather than collapsing to one.
        // Note: v6 enforces a minimum retention period between consecutive exemplars in the SAME
        // bucket, so a bucket that fires multiple times in rapid succession keeps only the first
        // exemplar — that's upstream behaviour, not a sandbox issue.
        implicit val exemplar: Exemplar[IO] = new Exemplar[IO] {
          override def get: IO[Option[Exemplar.Labels]] =
            random.nextLong.map { raw =>
              val id = raw & 0x7fffffffffffffffL
              Exemplar.Labels.of(Exemplar.LabelName("trace_id") -> f"$id%016x").toOption
            }
        }

        implicit val s: ExemplarSampler.Counter[IO, Double] = new ExemplarSampler.Counter[IO, Double] {
          val fraction = 0.1 // ≈ one in ten calls gets an exemplar
          val maybeSample: IO[Option[Exemplar.Data]] =
            random.nextDouble.flatMap { r =>
              if (r >= fraction) IO.pure(None)
              else
                for {
                  raw   <- random.nextLong
                  id     = raw & 0x7fffffffffffffffL
                  labels = Exemplar.Labels.of(Exemplar.LabelName("trace_id") -> f"$id%016x").toOption.get
                  now   <- IO.realTime
                } yield Some(Exemplar.Data(labels, Instant.ofEpochMilli(now.toMillis)))
            }

          override def sample(previous: Option[Exemplar.Data]): IO[Option[Exemplar.Data]] = maybeSample

          override def sample(value: Double, previous: Option[Exemplar.Data]): IO[Option[Exemplar.Data]] = maybeSample
        }

        // Info: set once. Stays in the registry for the lifetime of the resource. Querying
        // `sandbox_build_info` in Prometheus will always return value 1 with these label values.
        val setInfo = info.info(("1.2.3", "abc1234567890def", "sandbox"))

        // Simulated work for the Timer + OutcomeRecorder demo. Three outcomes — each produces a
        // distinct `outcome_status` label value on the counter:
        //   ~80% succeeded — random short duration
        //   ~15% errored   — simulated exception
        //   ~5%  canceled  — IO.canceled, which would normally kill the loop fiber; we contain it
        //                    by running the whole recorded operation on a child fiber and joining
        //                    for the outcome instead of attempting it.
        val simulatedWork: IO[Unit] =
          random.betweenDouble(0.0, 1.0).flatMap { r =>
            if (r < 0.80) random.betweenLong(10L, 200L).flatMap(ms => IO.sleep(ms.millis))
            else if (r < 0.95) IO.raiseError(new RuntimeException("simulated failure"))
            else IO.canceled
          }

        IO.println("Metrics endpoint live at http://localhost:9400/metrics — Ctrl-C to stop.") >>
          setInfo >>
          (
            for {
              value <- random.betweenDouble(0.0, 5.0)
              now   <- IO.realTime
              // 0..100 sine wave, ~31s period — recognisable on the dashboard.
              gaugeValue = 50.0 + 50.0 * math.sin(now.toMillis / 5000.0)
              _         <- counter.incWithExemplar(1.0, "value1")
              _         <- sampledCounter.incWithSampledExemplar(1.0, "value1")
              _         <- classic.observeWithExemplar(value)
              _         <- native.observeWithExemplar(value)
              _         <- dual.observeWithExemplar(value)
              _         <- gauge.set(gaugeValue)
              _         <- summary.observe(value)
              // .attempt swallows the simulated error so the loop keeps running; the outcome recorder
              // and timer both already saw it via .surround / .time before the error re-surfaced here.
              _ <- outcomeRecorder.surround(timer.time(simulatedWork)).start.flatMap(_.join).void
              _ <- IO.sleep(1.second)
            } yield ()
          ).foreverM
    }
  }

}
