/*
 * Copyright 2022 Permutive
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

package prometheus4cats.testing

import cats.data.{Chain, NonEmptySeq}
import cats.effect._
import munit.CatsEffectSuite
import prometheus4cats._
import scala.concurrent.duration._

//TODO test resource lifetimes with multiple references
class TestingMetricRegistrySuite extends CatsEffectSuite {

  test("Counter - history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterDoubleCounter(
          None,
          Counter.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty
        )
        .use { c =>
          c.inc >> c.inc(2.0) >> reg
            .counterHistory(Counter.Name("test_total"), Metric.CommonLabels.empty)
            .assertEquals(Some(Chain(0.0, 1.0, 3.0)))
        }
    }
  }

  test("Counter - prefixed history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterDoubleCounter(
          Some(Metric.Prefix("permutive")),
          Counter.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty
        )
        .use { c =>
          c.inc >> c.inc(2.0) >> reg
            .counterHistory(Some(Metric.Prefix("permutive")), Counter.Name("test_total"), Metric.CommonLabels.empty)
            .assertEquals(Some(Chain(0.0, 1.0, 3.0)))
        }
    }
  }

  test("Counter - labelled history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterLabelledDoubleCounter(
          None,
          Counter.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty,
          IndexedSeq(Label.Name("status"))
        )((s: String) => IndexedSeq(s))
        .use { case c =>
          c.inc("success") >> c.inc(2.0, "failure") >> reg
            .counterHistory(
              Counter.Name("test_total"),
              Metric.CommonLabels.empty,
              IndexedSeq(Label.Name("status")),
              IndexedSeq("success")
            )
            .assertEquals(Some(Chain(0.0, 1.0))) >> reg
            .counterHistory(
              Counter.Name("test_total"),
              Metric.CommonLabels.empty,
              IndexedSeq(Label.Name("status")),
              IndexedSeq("failure")
            )
            .assertEquals(Some(Chain(0.0, 2.0)))

        }
    }
  }

  test("Counter - resource lifecycle") {
    TestingMetricRegistry[IO].flatMap { reg =>
      IO.deferred[Unit].flatMap { wait =>
        val counter = reg
          .createAndRegisterDoubleCounter(
            None,
            Counter.Name("test_total"),
            Metric.Help("help"),
            Metric.CommonLabels.empty
          )
        counter.use { c =>
          // Wait for other fiber to use and release counter
          wait.get >>
            // Counter should still be valid as we still have a reference to it
            c.inc >> reg
              .counterHistory(Counter.Name("test_total"), Metric.CommonLabels.empty)
              .assertEquals(Some(Chain(0.0, 1.0)))
        }.start.flatMap { fiber =>
          counter.use_ >> wait.complete(()) >> fiber.joinWithNever
        }
      } >> reg
        .counterHistory(Counter.Name("test_total"), Metric.CommonLabels.empty)
        // All scopes which reference coutner have closed so it should be removed from registry
        .assertEquals(None)

    }

  }

  test("Gauge - history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterDoubleGauge(
          None,
          Gauge.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty
        )
        .use { g =>
          g.inc >> g.dec >> g.inc(2.0) >> g.dec(2.0) >> g.set(-1.0) >> g.reset >>
            reg
              .gaugeHistory(Gauge.Name("test_total"), Metric.CommonLabels.empty)
              .assertEquals(Some(Chain(0.0, 1.0, 0.0, 2.0, 0.0, -1.0, 0.0)))
        }
    }
  }

  test("Gauge - prefixed history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterDoubleGauge(
          Some(Metric.Prefix("permutive")),
          Gauge.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty
        )
        .use { g =>
          g.inc >> g.dec >> g.inc(2.0) >> g.dec(2.0) >> g.set(-1.0) >> g.reset >>
            reg
              .gaugeHistory(Some(Metric.Prefix("permutive")), Gauge.Name("test_total"), Metric.CommonLabels.empty)
              .assertEquals(Some(Chain(0.0, 1.0, 0.0, 2.0, 0.0, -1.0, 0.0)))
        }
    }
  }

  test("Gauge - labelled history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterLabelledDoubleGauge(
          None,
          Gauge.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty,
          IndexedSeq(Label.Name("status"))
        )((s: String) => IndexedSeq(s))
        .use { case c =>
          c.inc("success") >> c.inc(2.0, "failure") >> reg
            .gaugeHistory(
              Gauge.Name("test_total"),
              Metric.CommonLabels.empty,
              IndexedSeq(Label.Name("status")),
              IndexedSeq("success")
            )
            .assertEquals(Some(Chain(0.0, 1.0))) >> reg
            .gaugeHistory(
              Gauge.Name("test_total"),
              Metric.CommonLabels.empty,
              IndexedSeq(Label.Name("status")),
              IndexedSeq("failure")
            )
            .assertEquals(Some(Chain(0.0, 2.0)))

        }
    }
  }

  test("Histogram - history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterDoubleHistogram(
          None,
          Histogram.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty,
          NonEmptySeq(0.0, Seq(5.0, 10.0))
        )
        .use { h =>
          h.observe(1.0) >> h.observe(2.0) >> h.observe(3.0) >>
            reg
              .histogramHistory(Histogram.Name("test_total"), Metric.CommonLabels.empty)
              .assertEquals(Some(Chain(1.0, 2.0, 3.0)))
        }
    }
  }

  test("Histogram - prefixed history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterDoubleHistogram(
          Some(Metric.Prefix("permutive")),
          Histogram.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty,
          NonEmptySeq(0.0, Seq(5.0, 10.0))
        )
        .use { h =>
          h.observe(1.0) >> h.observe(2.0) >> h.observe(3.0) >>
            reg
              .histogramHistory(
                Some(Metric.Prefix("permutive")),
                Histogram.Name("test_total"),
                Metric.CommonLabels.empty
              )
              .assertEquals(Some(Chain(1.0, 2.0, 3.0)))
        }
    }
  }

  test("Histogram - labelled history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterLabelledDoubleHistogram(
          None,
          Histogram.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty,
          IndexedSeq(Label.Name("status")),
          NonEmptySeq(0.0, Seq(5.0, 10.0))
        )((s: String) => IndexedSeq(s))
        .use { case h =>
          h.observe(1.0, "success") >> h.observe(2.0, "failure") >> reg
            .histogramHistory(
              Histogram.Name("test_total"),
              Metric.CommonLabels.empty,
              IndexedSeq(Label.Name("status")),
              IndexedSeq("success")
            )
            .assertEquals(Some(Chain(1.0))) >> reg
            .histogramHistory(
              Histogram.Name("test_total"),
              Metric.CommonLabels.empty,
              IndexedSeq(Label.Name("status")),
              IndexedSeq("failure")
            )
            .assertEquals(Some(Chain(2.0)))

        }
    }
  }

  test("Summary - history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterDoubleSummary(
          None,
          Summary.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty,
          Seq(Summary.QuantileDefinition(Summary.Quantile(0.5), Summary.AllowedError(0.1))),
          5.seconds,
          Summary.AgeBuckets(5)
        )
        .use { s =>
          s.observe(1.0) >> s.observe(2.0) >> s.observe(3.0) >>
            reg
              .summaryHistory(Summary.Name("test_total"), Metric.CommonLabels.empty)
              .assertEquals(Some(Chain(1.0, 2.0, 3.0)))
        }
    }
  }

  test("Summary - prefixed history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterDoubleSummary(
          Some(Metric.Prefix("permutive")),
          Summary.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty,
          Seq(Summary.QuantileDefinition(Summary.Quantile(0.5), Summary.AllowedError(0.1))),
          5.seconds,
          Summary.AgeBuckets(5)
        )
        .use { s =>
          s.observe(1.0) >> s.observe(2.0) >> s.observe(3.0) >>
            reg
              .summaryHistory(Some(Metric.Prefix("permutive")), Summary.Name("test_total"), Metric.CommonLabels.empty)
              .assertEquals(Some(Chain(1.0, 2.0, 3.0)))
        }
    }
  }

  test("Summary - labelled history") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg
        .createAndRegisterLabelledDoubleSummary(
          None,
          Summary.Name("test_total"),
          Metric.Help("help"),
          Metric.CommonLabels.empty,
          IndexedSeq(Label.Name("status")),
          Seq(Summary.QuantileDefinition(Summary.Quantile(0.5), Summary.AllowedError(0.1))),
          5.seconds,
          Summary.AgeBuckets(5)
        )((s: String) => IndexedSeq(s))
        .use { case s =>
          s.observe(1.0, "success") >> s.observe(2.0, "failure") >> reg
            .summaryHistory(
              Summary.Name("test_total"),
              Metric.CommonLabels.empty,
              IndexedSeq(Label.Name("status")),
              IndexedSeq("success")
            )
            .assertEquals(Some(Chain(1.0))) >> reg
            .summaryHistory(
              Summary.Name("test_total"),
              Metric.CommonLabels.empty,
              IndexedSeq(Label.Name("status")),
              IndexedSeq("failure")
            )
            .assertEquals(Some(Chain(2.0)))

        }
    }
  }

}
