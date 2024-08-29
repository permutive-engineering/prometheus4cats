/*
 * Copyright 2022-2024 Permutive Ltd. <https://permutive.com>
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

import scala.annotation.nowarn
import scala.concurrent.duration._

import cats.data.Chain
import cats.data.NonEmptySeq
import cats.effect._
import cats.syntax.all._

import munit.CatsEffectSuite
import prometheus4cats._

@SuppressWarnings(Array("all"))
@nowarn("msg=unused value")
class TestingMetricRegistrySuite extends CatsEffectSuite {

  suite[Counter[IO, Double, Unit]]("Counter")(
    (reg: TestingMetricRegistry[IO], prefix: Option[Metric.Prefix], name: String, commonLabels: Metric.CommonLabels) =>
      reg.createAndRegisterDoubleCounter(
        prefix, Counter.Name.unsafeFrom(name), Metric.Help("help"), commonLabels, IndexedSeq.empty
      )((_: Unit) => IndexedSeq.empty),
    (c: Counter[IO, Double, Unit], _: Metric.CommonLabels) => (c.inc >> c.inc(2.0), Chain(0.0, 1.0, 3.0)),
    (reg: TestingMetricRegistry[IO], prefix: Option[Metric.Prefix], name: String, commonLabels: Metric.CommonLabels) =>
      reg.counterHistory(prefix, Counter.Name.unsafeFrom(name), commonLabels)
  )

  labelledSuite[Counter[IO, Double, IndexedSeq[String]]]("Counter")(
    (
        reg: TestingMetricRegistry[IO],
        prefix: Option[Metric.Prefix],
        name: String,
        commonLabels: Metric.CommonLabels,
        labels: IndexedSeq[Label.Name]
    ) =>
      reg.createAndRegisterDoubleCounter(
        prefix, Counter.Name.unsafeFrom(name), Metric.Help("help"), commonLabels, labels
      )(identity[IndexedSeq[String]](_)),
    (
        c: Counter[IO, Double, IndexedSeq[String]],
        _: Metric.CommonLabels,
        labels: IndexedSeq[String]
    ) => (c.inc(labels) >> c.inc(2.0, labels), Chain(0.0, 1.0, 3.0)),
    (
        reg: TestingMetricRegistry[IO],
        prefix: Option[Metric.Prefix],
        name: String,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        labelValues: IndexedSeq[String]
    ) => reg.counterHistory(prefix, Counter.Name.unsafeFrom(name), commonLabels, labelNames, labelValues)
  )

  suite[Gauge[IO, Double, Unit]]("Gauge")(
    (reg: TestingMetricRegistry[IO], prefix: Option[Metric.Prefix], name: String, commonLabels: Metric.CommonLabels) =>
      reg.createAndRegisterDoubleGauge(
        prefix, Gauge.Name.unsafeFrom(name), Metric.Help("help"), commonLabels, IndexedSeq.empty
      )((_: Unit) => IndexedSeq.empty),
    (g: Gauge[IO, Double, Unit], _: Metric.CommonLabels) =>
      (g.inc >> g.dec >> g.inc(2.0) >> g.dec(2.0) >> g.set(-1.0) >> g.reset, Chain(0.0, 1.0, 0.0, 2.0, 0.0, -1.0, 0.0)),
    (reg: TestingMetricRegistry[IO], prefix: Option[Metric.Prefix], name: String, commonLabels: Metric.CommonLabels) =>
      reg.gaugeHistory(prefix, Gauge.Name.unsafeFrom(name), commonLabels)
  )

  labelledSuite[Gauge[IO, Double, IndexedSeq[String]]]("Gauge")(
    (
        reg: TestingMetricRegistry[IO],
        prefix: Option[Metric.Prefix],
        name: String,
        commonLabels: Metric.CommonLabels,
        labels: IndexedSeq[Label.Name]
    ) =>
      reg.createAndRegisterDoubleGauge(
        prefix, Gauge.Name.unsafeFrom(name), Metric.Help("help"), commonLabels, labels
      )(identity[IndexedSeq[String]](_)),
    (
        g: Gauge[IO, Double, IndexedSeq[String]],
        _: Metric.CommonLabels,
        labels: IndexedSeq[String]
    ) =>
      (
        g.inc(labels) >> g.dec(labels) >> g.inc(2.0, labels) >> g.dec(2.0, labels) >> g.set(-1.0, labels) >> g.reset(
          labels
        ),
        Chain(0.0, 1.0, 0.0, 2.0, 0.0, -1.0, 0.0)
      ),
    (
        reg: TestingMetricRegistry[IO],
        prefix: Option[Metric.Prefix],
        name: String,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        labelValues: IndexedSeq[String]
    ) => reg.gaugeHistory(prefix, Gauge.Name.unsafeFrom(name), commonLabels, labelNames, labelValues)
  )

  suite[Histogram[IO, Double, Unit]]("Histogram")(
    (reg: TestingMetricRegistry[IO], prefix: Option[Metric.Prefix], name: String, commonLabels: Metric.CommonLabels) =>
      reg.createAndRegisterDoubleHistogram(
        prefix,
        Histogram.Name.unsafeFrom(name),
        Metric.Help("help"),
        commonLabels,
        IndexedSeq.empty,
        NonEmptySeq.of(0.0, 5.0, 10.0)
      )((_: Unit) => IndexedSeq.empty),
    (h: Histogram[IO, Double, Unit], _: Metric.CommonLabels) =>
      (h.observe(1.0) >> h.observe(2.0) >> h.observe(3.0), Chain(1.0, 2.0, 3.0)),
    (reg: TestingMetricRegistry[IO], prefix: Option[Metric.Prefix], name: String, commonLabels: Metric.CommonLabels) =>
      reg.histogramHistory(prefix, Histogram.Name.unsafeFrom(name), commonLabels)
  )

  labelledSuite[Histogram[IO, Double, IndexedSeq[String]]]("Histogram")(
    (
        reg: TestingMetricRegistry[IO],
        prefix: Option[Metric.Prefix],
        name: String,
        commonLabels: Metric.CommonLabels,
        labels: IndexedSeq[Label.Name]
    ) =>
      reg.createAndRegisterDoubleHistogram(
        prefix,
        Histogram.Name.unsafeFrom(name),
        Metric.Help("help"),
        commonLabels,
        labels,
        NonEmptySeq.of(0.0, 5.0, 10.0)
      )(identity[IndexedSeq[String]](_)),
    (
        h: Histogram[IO, Double, IndexedSeq[String]],
        _: Metric.CommonLabels,
        labels: IndexedSeq[String]
    ) => (h.observe(1.0, labels) >> h.observe(2.0, labels) >> h.observe(3.0, labels), Chain(1.0, 2.0, 3.0)),
    (
        reg: TestingMetricRegistry[IO],
        prefix: Option[Metric.Prefix],
        name: String,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        labelValues: IndexedSeq[String]
    ) => reg.histogramHistory(prefix, Histogram.Name.unsafeFrom(name), commonLabels, labelNames, labelValues)
  )

  suite[Summary[IO, Double, Unit]]("Summary")(
    (reg: TestingMetricRegistry[IO], prefix: Option[Metric.Prefix], name: String, commonLabels: Metric.CommonLabels) =>
      reg.createAndRegisterDoubleSummary(
        prefix,
        Summary.Name.unsafeFrom(name),
        Metric.Help("help"),
        commonLabels,
        IndexedSeq.empty,
        Seq(Summary.QuantileDefinition(Summary.Quantile(0.5), Summary.AllowedError(0.1))),
        5.seconds,
        Summary.AgeBuckets(5)
      )((_: Unit) => IndexedSeq.empty),
    (s: Summary[IO, Double, Unit], _: Metric.CommonLabels) =>
      (s.observe(1.0) >> s.observe(2.0) >> s.observe(3.0), Chain(1.0, 2.0, 3.0)),
    (reg: TestingMetricRegistry[IO], prefix: Option[Metric.Prefix], name: String, commonLabels: Metric.CommonLabels) =>
      reg.summaryHistory(prefix, Summary.Name.unsafeFrom(name), commonLabels)
  )

  labelledSuite[Summary[IO, Double, IndexedSeq[String]]]("Summary")(
    (
        reg: TestingMetricRegistry[IO],
        prefix: Option[Metric.Prefix],
        name: String,
        commonLabels: Metric.CommonLabels,
        labels: IndexedSeq[Label.Name]
    ) =>
      reg.createAndRegisterDoubleSummary(
        prefix,
        Summary.Name.unsafeFrom(name),
        Metric.Help("help"),
        commonLabels,
        labels,
        Seq(Summary.QuantileDefinition(Summary.Quantile(0.5), Summary.AllowedError(0.1))),
        5.seconds,
        Summary.AgeBuckets(5)
      )(identity[IndexedSeq[String]](_)),
    (
        s: Summary[IO, Double, IndexedSeq[String]],
        _: Metric.CommonLabels,
        labels: IndexedSeq[String]
    ) => (s.observe(1.0, labels) >> s.observe(2.0, labels) >> s.observe(3.0, labels), Chain(1.0, 2.0, 3.0)),
    (
        reg: TestingMetricRegistry[IO],
        prefix: Option[Metric.Prefix],
        name: String,
        commonLabels: Metric.CommonLabels,
        labelNames: IndexedSeq[Label.Name],
        labelValues: IndexedSeq[String]
    ) => reg.summaryHistory(prefix, Summary.Name.unsafeFrom(name), commonLabels, labelNames, labelValues)
  )

  private def suite[M <: Metric[Double]](name: String)(
      create: (TestingMetricRegistry[IO], Option[Metric.Prefix], String, Metric.CommonLabels) => Resource[IO, M],
      use: (
          M,
          Metric.CommonLabels
      ) => (IO[Unit], Chain[Double]),
      extract: (
          TestingMetricRegistry[IO],
          Option[Metric.Prefix],
          String,
          Metric.CommonLabels
      ) => IO[Option[Chain[Double]]]
  )(implicit loc: munit.Location): Unit = {
    test(s"$name - history") {
      TestingMetricRegistry[IO].flatMap { reg =>
        create(reg, None, "test_total", Metric.CommonLabels.empty).use { c =>
          val (run, expected) = use(c, Metric.CommonLabels.empty)
          run >> extract(reg, None, "test_total", Metric.CommonLabels.empty).assertEquals(Some(expected))
        }
      }
    }

    test(s"$name - prefixed history") {
      TestingMetricRegistry[IO].flatMap { reg =>
        create(reg, Some(Metric.Prefix("permutive")), "test_total", Metric.CommonLabels.empty).use { c =>
          val (run, expected) = use(c, Metric.CommonLabels.empty)
          run >> extract(reg, Some(Metric.Prefix("permutive")), "test_total", Metric.CommonLabels.empty)
            .assertEquals(Some(expected))
        }
      }
    }

    test(s"$name - concurrent resource lifecycle") {
      TestingMetricRegistry[IO].flatMap { reg =>
        IO.deferred[Unit].flatMap { wait =>
          val m = create(reg, None, "test_total", Metric.CommonLabels.empty)
          m.use { c =>
            val (run, expected) = use(c, Metric.CommonLabels.empty)
            // Wait for other fiber to use and release counter
            wait.get >>
              // metric should still be valid as we still have a reference to it
              run >> extract(reg, None, "test_total", Metric.CommonLabels.empty).assertEquals(Some(expected))
          }.start.flatMap { fiber =>
            m.use_ >> wait.complete(()) >> fiber.joinWithNever
          }
        } >> extract(reg, None, "test_total", Metric.CommonLabels.empty)
          // All scopes which reference metric have closed so it should be removed from registry
          .assertEquals(None)
      }

    }

    test(s"$name - nested resource lifecycle") {
      TestingMetricRegistry[IO].flatMap { reg =>
        val m = create(reg, None, "test_total", Metric.CommonLabels.empty)
        m.use { c =>
          val (run, expected) = use(c, Metric.CommonLabels.empty)
          // Metric should still be valid as we still have a reference to it
          run >>
            m.use_ >>
            extract(reg, None, "test_total", Metric.CommonLabels.empty).assertEquals(Some(expected))
        } >> extract(reg, None, "test_total", Metric.CommonLabels.empty).assertEquals(None)
      }
    }

  }

  private def labelledSuite[M <: Metric[Double] with Metric.Labelled[IndexedSeq[String]]](name: String)(
      create: (
          TestingMetricRegistry[IO],
          Option[Metric.Prefix],
          String,
          Metric.CommonLabels,
          IndexedSeq[Label.Name]
      ) => Resource[IO, M],
      use: (M, Metric.CommonLabels, IndexedSeq[String]) => (
          IO[Unit],
          Chain[Double]
      ),
      extract: (
          TestingMetricRegistry[IO],
          Option[Metric.Prefix],
          String,
          Metric.CommonLabels,
          IndexedSeq[Label.Name],
          IndexedSeq[String]
      ) => IO[Option[Chain[Double]]]
  )(implicit loc: munit.Location): Unit =
    test(s"$name - labelled history") {
      TestingMetricRegistry[IO].flatMap { reg =>
        create(reg, None, "test_total", Metric.CommonLabels.empty, IndexedSeq(Label.Name("status"))).use { c =>
          val (run1, expected1) = use(c, Metric.CommonLabels.empty, IndexedSeq("success"))
          val (run2, expected2) = use(c, Metric.CommonLabels.empty, IndexedSeq("failure"))
          run1 >> run2 >> extract(
            reg, None, "test_total", Metric.CommonLabels.empty, IndexedSeq(Label.Name("status")), IndexedSeq("success")
          )
            .assertEquals(Some(expected1)) >> extract(
            reg, None, "test_total", Metric.CommonLabels.empty, IndexedSeq(Label.Name("status")), IndexedSeq("failure")
          )
            .assertEquals(Some(expected2))

        }
      }
    }

  test("Info - value") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg.createAndRegisterInfo(None, "test_info", Metric.Help("help")).use { _ =>
        reg.infoValue(None, "test_info").assertEquals(Some(1.0))
      } >> reg.infoValue(None, "test_info").assertEquals(None)
    }
  }

  test("Info - prefixed value") {
    TestingMetricRegistry[IO].flatMap { reg =>
      reg.createAndRegisterInfo(Some(Metric.Prefix("permutive")), "test_info", Metric.Help("help")).use { _ =>
        reg.infoValue(Some(Metric.Prefix("permutive")), "test_info").assertEquals(Some(1.0))
      } >> reg.infoValue(Some(Metric.Prefix("permutive")), "test_info").assertEquals(None)
    }
  }

  test("Info - concurrent resource lifecycle") {
    TestingMetricRegistry[IO].flatMap { reg =>
      IO.deferred[Unit].flatMap { wait =>
        val m = reg.createAndRegisterInfo(Some(Metric.Prefix("permutive")), "test_info", Metric.Help("help"))
        m.use { i =>
          // Wait for other fiber to use and release counter
          wait.get >>
            // metric should still be valid as we still have a reference to it
            i.info(Map.empty) >> reg.infoValue(Some(Metric.Prefix("permutive")), "test_info").assertEquals(Some(1.0))
        }.start.flatMap { fiber =>
          m.use_ >> wait.complete(()) >> fiber.joinWithNever
        }
      } >> reg
        .infoValue(Some(Metric.Prefix("permutive")), "test_info")
        // All scopes which reference metric have closed so it should be removed from registry
        .assertEquals(None)

    }

  }

  test("Info - nested resource lifecycle") {}
  TestingMetricRegistry[IO].flatMap { reg =>
    val m = reg.createAndRegisterInfo(Some(Metric.Prefix("permutive")), "test_info", Metric.Help("help"))
    m.use { c =>
      // Metric should still be valid as we still have a reference to it
      c.info(Map.empty) >>
        m.use_ >>
        reg.infoValue(None, "test_info").assertEquals(Some(1.0))

    } >> reg
      .infoValue(None, "test_info")
      // All scopes which reference metric have closed so it should be removed from registry
      .assertEquals(None)
  }

  test("Error if registering same name and labels but different type") {
    val labels = Metric.CommonLabels.of(Label.Name("one") -> "one", Label.Name("two") -> "two").toOption.get
    List.range(0, 100).traverse { _ =>
      TestingMetricRegistry[IO].flatMap { reg =>
        (
          reg.createAndRegisterDoubleCounter(
            None, Counter.Name("test_total"), Metric.Help("help"), labels, IndexedSeq.empty
          )((_: Unit) => IndexedSeq.empty),
          reg.createAndRegisterDoubleGauge(
            None, Gauge.Name("test_total"), Metric.Help("help"), labels, IndexedSeq.empty
          )((_: Unit) => IndexedSeq.empty)
          // TODO improve assertion when we have a common interface across registry implementations for this error
        ).parTupled.use_.intercept[RuntimeException]
      }
    }
  }

}
