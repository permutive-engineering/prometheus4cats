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

import scala.concurrent.duration._

import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import cats.effect.std.Random

import io.prometheus.metrics.exporter.httpserver.HTTPServer
import io.prometheus.metrics.model.registry.PrometheusRegistry
import prometheus4cats.MetricFactory
import prometheus4cats.javaclient.JavaMetricRegistry

/** Local-only sandbox app for poking at metric shapes against the docker-compose Prometheus + Grafana stack at the repo
  * root.
  *
  * Run `docker compose up -d`, then `sbt sandbox/run`.
  *
  * Exposes on `:9400/metrics`:
  *   - `test_counter_total` — incrementing counter with a single label
  *   - `test_classic_histogram_seconds` — classic-only histogram with curated bucket boundaries
  *   - `test_native_histogram_seconds` — native (exponential / sparse) histogram, no declared buckets
  *   - `test_dual_histogram_seconds` — dual-mode histogram: BOTH classic and native data from one declaration,
  *     NHCB-friendly
  *
  * Each observation per iteration draws a random duration in `[0s, 5s]` so the bucket distribution fills out across the
  * curated boundaries over time.
  *
  *   - http://localhost:9400/metrics raw scrape output
  *   - http://localhost:9090 Prometheus UI
  *   - http://localhost:3123 Grafana
  */
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
      factory   = MetricFactory.builder.build(registry)

      counter <- factory.counter("test_counter_total").ofDouble.help("Test counter").label[String]("label").build

      classic <- factory
                   .histogram("test_classic_histogram_seconds")
                   .ofDouble
                   .help("Classic histogram with curated buckets")
                   .buckets(LatencyBuckets)
                   .build

      native <- factory
                  .nativeHistogram("test_native_histogram_seconds")
                  .ofDouble
                  .help("Native (sparse / exponential) histogram, no declared buckets")
                  .build

      dual <- factory
                .histogram("test_dual_histogram_seconds")
                .ofDouble
                .help("Dual-mode histogram: classic + native from one declaration")
                .buckets(LatencyBuckets)
                .withNative
                .build

      random <- Resource.eval(Random.scalaUtilRandom[IO])
    } yield (counter, classic, native, dual, random)

    app.use { case (counter, classic, native, dual, random) =>
      IO.println("Metrics endpoint live at http://localhost:9400/metrics — Ctrl-C to stop.") >>
        (
          for {
            value <- random.betweenDouble(0.0, 5.0)
            _     <- counter.inc(1.0, "value1")
            _     <- classic.observe(value)
            _     <- native.observe(value)
            _     <- dual.observe(value)
            _     <- IO.sleep(1.second)
          } yield ()
        ).foreverM
    }
  }

}
