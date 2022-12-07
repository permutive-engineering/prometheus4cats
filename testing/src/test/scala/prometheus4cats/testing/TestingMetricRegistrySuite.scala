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

//TODO test same labels, different values
//TODO test resource lifetimes with multiple references
class TestingMetricRegistrySuite extends CatsEffectSuite {

  test("Counter history") {
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

  test("Gauge history") {
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

  test("Histogram history") {
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

  test("Summary history") {
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

}
