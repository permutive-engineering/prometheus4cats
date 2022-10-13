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

package openmetrics4s.testkit

import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.testkit.TestControl
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import openmetrics4s.Metric.CommonLabels
import openmetrics4s._
import org.scalacheck.effect.PropF._
import org.scalacheck.{Arbitrary, Gen}

trait MetricsRegistrySuite[State] extends ScalaCheckEffectSuite { self: CatsEffectSuite =>
  def stateResource: Resource[IO, State]
  def registryResource(state: State): Resource[IO, MetricsRegistry[IO]]

  private val niceStringGen: Gen[String] = for {
    c1 <- Gen.alphaChar
    c2 <- Gen.alphaChar
    s <- Gen.alphaNumStr
  } yield s"$c1$c2$s"

  private def niceStringArb[A](f: String => Either[String, A]): Arbitrary[A] = Arbitrary(
    niceStringGen.flatMap(s => Gen.oneOf(f(s).toOption))
  )

  implicit val prefixArb: Arbitrary[Metric.Prefix] = niceStringArb(Metric.Prefix.from)

  implicit val counterNameArb: Arbitrary[Counter.Name] = niceStringArb(s => Counter.Name.from(s"${s}_total"))

  implicit val gaugeNameArb: Arbitrary[Gauge.Name] = niceStringArb(s => Gauge.Name.from(s))

  implicit val histogramNameArb: Arbitrary[Histogram.Name] = niceStringArb(s => Histogram.Name.from(s))

  implicit val helpArb: Arbitrary[Metric.Help] = niceStringArb(Metric.Help.from)

  implicit val labelArb: Arbitrary[Label.Name] = niceStringArb(Label.Name.from)

  implicit val labelMapArb: Arbitrary[Map[Label.Name, String]] = Arbitrary(
    for {
      size <- Gen.choose(0, 10)
      map <- Gen.mapOfN(size, Arbitrary.arbitrary[(Label.Name, String)])
    } yield map
  )

  implicit val commonLabelsArb: Arbitrary[Metric.CommonLabels] =
    Arbitrary(labelMapArb.arbitrary.flatMap(map => Gen.oneOf(CommonLabels.from(map).toOption)))

  def getCounterValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String] = Map.empty
  ): IO[Option[Double]]

  def getGaugeValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String] = Map.empty
  ): IO[Option[Double]]

  def getHistogramValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double],
      extraLabels: Map[Label.Name, String] = Map.empty
  ): IO[Option[Map[String, Double]]]

  test("create and increment a counter") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          incBy: Double
      ) =>
        stateResource.use { state =>
          registryResource(state).use { reg =>
            reg
              .createAndRegisterDoubleCounter(prefix, name, help, commonLabels)
              .flatMap(_.inc(incBy)) >> getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            ).map(res => if (incBy >= 0) assertEquals(res, Some(incBy)) else assertEquals(res, Some(0.0)))

          }
        }
    }
  }

  test("create and increment a labelled counter") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Map[Label.Name, String],
          incBy: Double
      ) =>
        stateResource.use { state =>
          registryResource(state).use { reg =>
            reg
              .createAndRegisterLabelledDoubleCounter[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq
              )(_.values.toIndexedSeq)
              .flatMap(_.inc(incBy, labels)) >> getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels
            ).map(res => if (incBy >= 0) assertEquals(res, Some(incBy)) else assertEquals(res, Some(0.0)))
          }
        }
    }
  }

  test("create and update a gauge") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          set: Double,
          inc: Double,
          dec: Double
      ) =>
        TestControl
          .executeEmbed(stateResource.use { state =>
            registryResource(state).use { reg =>
              val get = getGaugeValue(
                state,
                prefix,
                name,
                help,
                commonLabels
              )

              for {
                gauge <- reg.createAndRegisterDoubleGauge(prefix, name, help, commonLabels)
                _ <- gauge.set(set)
                setValue <- get
                _ <- gauge.inc
                incOneValue <- get
                _ <- gauge.inc(inc)
                incValue <- get
                _ <- gauge.dec
                decOneValue <- get
                _ <- gauge.dec(dec)
                decValue <- get
              } yield (setValue, incOneValue, incValue, decOneValue, decValue)
            }
          })
          .map { case (setValue, incOneValue, incValue, decOneValue, decValue) =>
            assertEquals(setValue, Some(set))
            assertEquals(incOneValue, setValue.map(_ + 1))
            assertEquals(incValue, incOneValue.map(_ + inc))
            assertEquals(decOneValue, incValue.map(_ - 1))
            assertEquals(decValue, decOneValue.map(_ - dec))
          }
    }
  }

  test("create and set a labelled gauge") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Map[Label.Name, String],
          set: Double,
          inc: Double,
          dec: Double
      ) =>
        stateResource.use { state =>
          registryResource(state).use { reg =>
            val get = getGaugeValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels
            )

            for {
              gauge <- reg.createAndRegisterLabelledDoubleGauge[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq
              )(_.values.toIndexedSeq)
              _ <- gauge.set(set, labels)
              setValue <- get
              _ <- gauge.inc(labels = labels)
              incOneValue <- get
              _ <- gauge.inc(inc, labels)
              incValue <- get
              _ <- gauge.dec(labels = labels)
              decOneValue <- get
              _ <- gauge.dec(dec, labels)
              decValue <- get
            } yield {
              assertEquals(setValue, Some(set))
              assertEquals(incOneValue, setValue.map(_ + 1))
              assertEquals(incValue, incOneValue.map(_ + inc))
              assertEquals(decOneValue, incValue.map(_ - 1))
              assertEquals(decValue, decOneValue.map(_ - dec))
            }
          }
        }
    }
  }

  test("create and increment a histogram") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double
      ) =>
        stateResource.use { state =>
          registryResource(state).use { reg =>
            val buckets = NonEmptySeq.of[Double](0, value).sorted

            val expected =
              if (value > 0) Map("0.0" -> 0.0, value.toString -> 1.0, "+Inf" -> 1.0)
              else Map(value.toString -> 1.0, "0.0" -> 1.0, "+Inf" -> 1.0)

            reg
              .createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets)
              .flatMap(_.observe(value)) >> getHistogramValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              buckets
            ).map(res => assertEquals(res, Some(expected)))

          }
        }
    }
  }

  test("create and increment a labelled histogram") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Map[Label.Name, String],
          value: Double
      ) =>
        stateResource.use { state =>
          registryResource(state).use { reg =>
            val buckets = NonEmptySeq.of[Double](0, value).sorted

            val expected =
              if (value > 0) Map("0.0" -> 0.0, value.toString -> 1.0, "+Inf" -> 1.0)
              else Map(value.toString -> 1.0, "0.0" -> 1.0, "+Inf" -> 1.0)

            reg
              .createAndRegisterLabelledDoubleHistogram[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                buckets
              )(_.values.toIndexedSeq)
              .flatMap(_.observe(value, labels)) >> getHistogramValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              buckets,
              labels
            ).map(res => assertEquals(res, Some(expected)))
          }
        }
    }
  }
}
