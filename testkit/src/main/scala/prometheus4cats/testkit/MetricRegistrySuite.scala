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

package prometheus4cats.testkit

import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.kernel.Resource
import munit.CatsEffectSuite
import org.scalacheck.Arbitrary
import org.scalacheck.effect.PropF._
import prometheus4cats.Summary.QuantileDefinition
import prometheus4cats._

import scala.concurrent.duration._

trait MetricRegistrySuite[State] extends RegistrySuite[State] { self: CatsEffectSuite =>

  implicit val infoNameArb: Arbitrary[Info.Name] = niceStringArb(s => Info.Name.from(s"${s}_info"))

  def metricRegistryResource(state: State): Resource[IO, MetricRegistry[IO]]

  def getInfoValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String]
  ): IO[Option[Double]]

  test("create, increment and de-register a counter") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          incBy: Double
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            )

            reg
              .createAndRegisterDoubleCounter(prefix, name, help, commonLabels)
              .evalMap(_.inc(incBy))
              .surround(
                get.map(res => if (incBy >= 0) assertEquals(res, Some(incBy)) else assertEquals(res, Some(0.0)))
              ) >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("create, increment and de-register a labelled counter") {
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
          metricRegistryResource(state).use { reg =>
            val get = getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels
            )

            reg
              .createAndRegisterLabelledDoubleCounter[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq
              )(_.values.toIndexedSeq)
              .evalMap(_.inc(incBy, labels))
              .surround(
                get.map(res => if (incBy >= 0) assertEquals(res, Some(incBy)) else assertEquals(res, Some(0.0)))
              ) >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("create, update and de-register a gauge") {
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
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getGaugeValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            )

            reg
              .createAndRegisterDoubleGauge(prefix, name, help, commonLabels)
              .use(gauge =>
                for {
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
              )
              .map { case (setValue, incOneValue, incValue, decOneValue, decValue) =>
                assertEquals(setValue, Some(set))
                assertEquals(incOneValue, setValue.map(_ + 1))
                assertEquals(incValue, incOneValue.map(_ + inc))
                assertEquals(decOneValue, incValue.map(_ - 1))
                assertEquals(decValue, decOneValue.map(_ - dec))
              } >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("create, update and de-register a labelled gauge") {
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
          metricRegistryResource(state).use { reg =>
            val get = getGaugeValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels
            )

            reg
              .createAndRegisterLabelledDoubleGauge[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq
              )(_.values.toIndexedSeq)
              .use { gauge =>
                for {
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
              } >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("create, increment and de-register a histogram") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val buckets = NonEmptySeq.of[Double](0, value).sorted

            val expected =
              if (value > 0) Map("0.0" -> 0.0, value.toString -> 1.0, "+Inf" -> 1.0)
              else Map(value.toString -> 1.0, "0.0" -> 1.0, "+Inf" -> 1.0)

            val get = getHistogramValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              buckets
            )

            reg
              .createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets)
              .evalMap(_.observe(value))
              .surround(
                get.map(res => assertEquals(res, Some(expected)))
              ) >> get.map(assertEquals(_, None))

          }
        }
    }
  }

  test("create, increment and de-register a labelled histogram") {
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
          metricRegistryResource(state).use { reg =>
            val buckets = NonEmptySeq.of[Double](0, value).sorted

            val expected =
              if (value > 0) Map("0.0" -> 0.0, value.toString -> 1.0, "+Inf" -> 1.0)
              else Map(value.toString -> 1.0, "0.0" -> 1.0, "+Inf" -> 1.0)

            val get = getHistogramValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              buckets,
              labels
            )

            reg
              .createAndRegisterLabelledDoubleHistogram[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                buckets
              )(_.values.toIndexedSeq)
              .evalMap(_.observe(value, labels))
              .surround(
                get.map(res => assertEquals(res, Some(expected)))
              ) >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("create, increment and de-register a summary") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double,
          quantiles: Seq[QuantileDefinition],
          age: (FiniteDuration, Summary.AgeBuckets)
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getSummaryValue(state, prefix, name, help, commonLabels, Map.empty)

            reg
              .createAndRegisterDoubleSummary(
                prefix,
                name,
                help,
                commonLabels,
                quantiles,
                age._1 + 1.second,
                age._2
              )
              .evalTap(_.observe(value))
              .surround(get.map { case (q, count, sum) =>
                assertEquals(q, Some(quantiles.map(qd => qd.value.value.toString -> value).toMap))
                assertEquals(count, Some(1.0))
                assertEquals(sum, Some(value))
              }) >> get.map { case (q, c, s) =>
              assertEquals(q, None)
              assertEquals(c, None)
              assertEquals(s, None)
            }
          }
        }
    }
  }

  test("create, increment and de-register a labelled summary") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Map[Label.Name, String],
          value: Double,
          quantiles: Seq[QuantileDefinition],
          age: (FiniteDuration, Summary.AgeBuckets)
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getSummaryValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels
            )

            reg
              .createAndRegisterLabelledDoubleSummary[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                quantiles,
                age._1 + 1.second,
                age._2
              )(_.values.toIndexedSeq)
              .evalTap(_.observe(value, labels))
              .surround(
                get.map { case (q, count, sum) =>
                  assertEquals(q, Some(quantiles.map(qd => qd.value.value.toString -> value).toMap))
                  assertEquals(count, Some(1.0))
                  assertEquals(sum, Some(value))
                }
              ) >> get.map { case (q, c, s) =>
              assertEquals(q, None)
              assertEquals(c, None)
              assertEquals(s, None)
            }
          }
        }
    }
  }

  test("create, set and de-register info") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help,
          labels: Map[Label.Name, String]
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getInfoValue(state, prefix, name, help, labels)

            reg
              .createAndRegisterInfo(prefix, name, help)
              .evalTap(_.info(labels))
              .surround(
                get.map(
                  assertEquals(_, Some(1.0))
                )
              ) >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("nested counter resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            )

            val create = reg.createAndRegisterDoubleCounter(prefix, name, help, commonLabels)

            create.use { c =>
              c.inc(1.0) >> create.use_ >> get.map(_.isDefined).assert
            } >> get.assertEquals(None)
          }
        }
    }
  }

  test("nested gauge resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getGaugeValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            )

            val create = reg.createAndRegisterDoubleGauge(prefix, name, help, commonLabels)

            create.use { g =>
              g.set(1.0) >> create.use_ >> get.map(_.isDefined).assert
            } >> get.assertEquals(None)
          }
        }
    }
  }

  test("nested histogram resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val buckets = NonEmptySeq.of[Double](0, value).sorted

            val get = getHistogramValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              buckets
            )

            val create = reg.createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets)

            create.use { h =>
              h.observe(value) >> create.use_ >> get.map(_.isDefined).assert
            } >> get.assertEquals(None)
          }
        }
    }
  }

  test("nested summary resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double,
          quantiles: Seq[QuantileDefinition],
          age: (FiniteDuration, Summary.AgeBuckets)
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getSummaryValue(state, prefix, name, help, commonLabels, Map.empty)

            val create = reg
              .createAndRegisterDoubleSummary(
                prefix,
                name,
                help,
                commonLabels,
                quantiles,
                age._1 + 1.second,
                age._2
              )

            create.use { s =>
              s.observe(value) >> create.use_ >> get.map(_._2.isDefined) assert
            } >> get.assertEquals((None, None, None))
          }
        }
    }
  }

  test("nested info resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help,
          labels: Map[Label.Name, String]
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getInfoValue(state, prefix, name, help, labels)

            val create = reg.createAndRegisterInfo(prefix, name, help)

            create.use { c =>
              c.info(labels) >> create.use_ >> get.assertEquals(Some(1.0))
            } >> get.assertEquals(None)
          }
        }
    }
  }

  test("concurrent counter resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            )

            val create = reg.createAndRegisterDoubleCounter(prefix, name, help, commonLabels)

            IO.deferred[Unit].flatMap { wait =>
              create.use { c =>
                wait.get >>
                  c.inc(1.0) >> get.map(_.isDefined).assert
              }.start.flatMap(fiber =>
                create.use_ >> wait.complete(()) >>
                  fiber.joinWithNever
              ) >> get.assertEquals(
                None
              )
            }
          }
        }
    }
  }

  test("concurrent gauge resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getGaugeValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            )

            val create = reg.createAndRegisterDoubleGauge(prefix, name, help, commonLabels)

            IO.deferred[Unit].flatMap { wait =>
              create.use { g =>
                wait.get >>
                  g.set(1.0) >> get.map(_.isDefined).assert
              }.start.flatMap(fiber =>
                create.use_ >> wait.complete(()) >>
                  fiber.joinWithNever
              ) >> get.assertEquals(
                None
              )
            }
          }
        }
    }
  }

  test("concurrent histogram resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val buckets = NonEmptySeq.of[Double](0, value).sorted

            val get = getHistogramValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              buckets
            )

            val create = reg.createAndRegisterDoubleHistogram(prefix, name, help, commonLabels, buckets)

            IO.deferred[Unit].flatMap { wait =>
              create.use { h =>
                wait.get >>
                  h.observe(value) >> get.map(_.isDefined).assert
              }.start.flatMap(fiber =>
                create.use_ >> wait.complete(()) >>
                  fiber.joinWithNever
              ) >> get.assertEquals(
                None
              )
            }
          }
        }
    }
  }

  test("concurrent summary resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double,
          quantiles: Seq[QuantileDefinition],
          age: (FiniteDuration, Summary.AgeBuckets)
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getSummaryValue(state, prefix, name, help, commonLabels, Map.empty)

            val create = reg
              .createAndRegisterDoubleSummary(
                prefix,
                name,
                help,
                commonLabels,
                quantiles,
                age._1 + 1.second,
                age._2
              )

            IO.deferred[Unit].flatMap { wait =>
              create.use { s =>
                wait.get >>
                  s.observe(value) >> get.map(_._2.isDefined).assert
              }.start.flatMap(fiber =>
                create.use_ >> wait.complete(()) >>
                  fiber.joinWithNever
              ) >> get.assertEquals(
                (None, None, None)
              )
            }
          }
        }
    }
  }

  test("concurrent info resource usage") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Info.Name,
          help: Metric.Help,
          labels: Map[Label.Name, String]
      ) =>
        stateResource.use { state =>
          metricRegistryResource(state).use { reg =>
            val get = getInfoValue(state, prefix, name, help, labels)

            val create = reg.createAndRegisterInfo(prefix, name, help)

            IO.deferred[Unit].flatMap { wait =>
              create.use { i =>
                wait.get >>
                  i.info(labels) >> get.map(_.isDefined).assert
              }.start.flatMap(fiber =>
                create.use_ >> wait.complete(()) >>
                  fiber.joinWithNever
              ) >> get.assertEquals(
                None
              )
            }
          }
        }
    }
  }

}
