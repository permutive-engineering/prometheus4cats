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

import cats.data.{NonEmptyList, NonEmptySeq}
import cats.syntax.all._
import cats.effect.IO
import cats.effect.kernel.Resource
import munit.CatsEffectSuite
import org.scalacheck.effect.PropF._
import org.scalacheck.{Arbitrary, Gen}
import prometheus4cats._

trait CallbackRegistrySuite[State] extends RegistrySuite[State] { self: CatsEffectSuite =>

  implicit val quantileValuesArb: Arbitrary[Map[Summary.Quantile, Double]] = Arbitrary(
    Gen.mapOf[Summary.Quantile, Double](Arbitrary.arbitrary[(Summary.Quantile, Double)])
  )

  def callbackRegistryResource(state: State): Resource[IO, CallbackRegistry[IO]]

  test("register, set and de-register a counter") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double
      ) =>
        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            val get = IO.cede >> getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            )

            reg
              .registerDoubleCounterCallback(prefix, name, help, commonLabels, IO(value))
              .surround(
                get.map(res => if (value >= 0) assertEquals(res, Some(value)) else assertEquals(res, Some(0.0)))
              ) >> get.map(assertEquals(_, None))

          }
        }
    }
  }

  test("register, set and de-register a labelled counter") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Map[Label.Name, String],
          value: Double
      ) =>
        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            val get = IO.cede >> getCounterValue(state, prefix, name, help, commonLabels, labels)

            reg
              .registerLabelledDoubleCounterCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                IO(NonEmptyList.one(value -> labels))
              )(_.values.toIndexedSeq)
              .surround(
                get.map(res => if (value >= 0) assertEquals(res, Some(value)) else assertEquals(res, Some(0.0)))
              ) >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("register, set and de-register a gauge") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double
      ) =>
        stateResource.use { state =>
          val get = IO.cede >> getGaugeValue(
            state,
            prefix,
            name,
            help,
            commonLabels
          )

          callbackRegistryResource(state).use { reg =>
            reg
              .registerDoubleGaugeCallback(prefix, name, help, commonLabels, IO(value))
              .surround(
                get.map(assertEquals(_, Some(value)))
              ) >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("register, set and de-register a labelled gauge") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Map[Label.Name, String],
          value: Double
      ) =>
        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            val get = IO.cede >> getGaugeValue(state, prefix, name, help, commonLabels, labels)

            reg
              .registerLabelledDoubleGaugeCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                IO(NonEmptyList.one(value -> labels))
              )(_.values.toIndexedSeq)
              .surround(
                get.map(assertEquals(_, Some(value)))
              ) >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("register, set and de-register a histogram") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double,
          sum: Double
      ) =>
        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            val buckets = NonEmptySeq.of[Double](0, value).sorted

            val expected =
              if (value > 0) Map("0.0" -> 0.0, value.toString -> 1.0, "+Inf" -> 1.0)
              else Map(value.toString -> 1.0, "0.0" -> 1.0, "+Inf" -> 1.0)

            val bucketValues =
              if (value > 0) NonEmptySeq.of(0.0, 1.0, 1.0)
              else NonEmptySeq.of(1.0, 1.0, 1.0)

            val get = IO.cede >> getHistogramValue(state, prefix, name, help, commonLabels, buckets)

            reg
              .registerDoubleHistogramCallback(
                prefix,
                name,
                help,
                commonLabels,
                buckets,
                IO(Histogram.Value(sum, bucketValues))
              )
              .surround(get.map { res =>
                assertEquals(res, Some(expected))
              }) >> get.map(assertEquals(_, None))

          }
        }
    }
  }

  test("register, set and de-register a labelled histogram") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Histogram.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Map[Label.Name, String],
          value: Double,
          sum: Double
      ) =>
        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            val buckets = NonEmptySeq.of[Double](0, value).sorted

            val expected =
              if (value > 0) Map("0.0" -> 0.0, value.toString -> 1.0, "+Inf" -> 1.0)
              else Map(value.toString -> 1.0, "0.0" -> 1.0, "+Inf" -> 1.0)

            val bucketValues =
              if (value > 0) NonEmptySeq.of(0.0, 1.0, 1.0)
              else NonEmptySeq.of(1.0, 1.0, 1.0)

            val get = IO.cede >> getHistogramValue(state, prefix, name, help, commonLabels, buckets, labels)

            reg
              .registerLabelledDoubleHistogramCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                buckets,
                IO(NonEmptyList.one(Histogram.Value(sum, bucketValues) -> labels))
              )(_.values.toIndexedSeq)
              .surround(
                get.map(res => assertEquals(res, Some(expected)))
              ) >> get.map(assertEquals(_, None))
          }
        }
    }
  }

  test("register, set and de-register a summary") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          sum: Double,
          count: Double,
          quantiles: Map[Summary.Quantile, Double]
      ) =>
        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            val get = IO.cede >> getSummaryValue(state, prefix, name, help, commonLabels, Map.empty)

            reg
              .registerDoubleSummaryCallback(
                prefix,
                name,
                help,
                commonLabels,
                IO(Summary.Value(count, sum, quantiles.map { case (q, v) => q.value -> v }))
              )
              .surround(get.map { case (q, c, s) =>
                assertEquals(q, Some(quantiles.map { case (q, v) => q.value.toString -> v }))
                assertEquals(c, Some(count))
                assertEquals(s, Some(sum))
              }) >> get.map { case (q, c, s) =>
              assertEquals(q, None)
              assertEquals(c, None)
              assertEquals(s, None)
            }
          }
        }
    }
  }

  test("register, set and de-register a labelled summary") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Summary.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Map[Label.Name, String],
          sum: Double,
          count: Double,
          quantiles: Map[Summary.Quantile, Double]
      ) =>
        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            val get = IO.cede >> getSummaryValue(state, prefix, name, help, commonLabels, labels)

            reg
              .registerLabelledDoubleSummaryCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                IO(NonEmptyList.one(Summary.Value(count, sum, quantiles.map { case (q, v) => q.value -> v }) -> labels))
              )(_.values.toIndexedSeq)
              .surround(get.map { case (q, c, s) =>
                assertEquals(q, Some(quantiles.map { case (q, v) => q.value.toString -> v }))
                assertEquals(c, Some(count))
                assertEquals(s, Some(sum))
              }) >> get.map { case (q, c, s) =>
              assertEquals(q, None)
              assertEquals(c, None)
              assertEquals(s, None)
            }
          }
        }
    }
  }

  test("register and de-register metric collection") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name1: Counter.Name,
          name2: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels1: Map[Label.Name, String],
          labels2: Map[Label.Name, String],
          values: (Double, Double)
      ) =>
        stateResource.use { state =>
          val get1 = getCounterValue(
            state,
            prefix,
            name1,
            help,
            commonLabels,
            labels1
          )

          val get2 = getCounterValue(
            state,
            prefix,
            name2,
            help,
            commonLabels,
            labels2
          )

          callbackRegistryResource(state).use { reg =>
            reg
              .registerMetricCollectionCallback(
                prefix,
                commonLabels,
                IO(
                  MetricCollection.empty
                    .appendDoubleCounter(name1, help, labels1, values._1)
                    .appendDoubleCounter(name2, help, labels2, values._2)
                )
              )
              .surround(
                get1
                  .map(assertEquals(_, Some(values._1))) >> get2.map(assertEquals(_, Some(values._2)))
              ) >> get1.map(assertEquals(_, None)) >> get2.map(assertEquals(_, None))
          }
        }
    }
  }

  test("register and de-register metric collection with the same name and different labels") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels1: Map[Label.Name, String],
          value1: Double,
          value2: Double
      ) =>
        val labels2 = labels1.map { case (n, v) => n -> s"$v+" }

        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            val get1 = getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels1
            )

            val get2 = getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels2
            )

            reg
              .registerMetricCollectionCallback(
                prefix,
                commonLabels,
                IO(
                  if (labels1.isEmpty) MetricCollection.empty.appendDoubleCounter(name, help, labels2, value2)
                  else
                    MetricCollection.empty
                      .appendDoubleCounter(name, help, labels1, value1)
                      .appendDoubleCounter(name, help, labels2, value2)
                )
              )
              .surround(
                if (labels1.isEmpty)
                  get1.map(assertEquals(_, Some(value2)))
                else
                  get1.map { res =>
                    assertEquals(res, Some(value1))
                  } >> get2.map { res =>
                    assertEquals(res, Some(value2))
                  }
              ) >> get1.map(assertEquals(_, None)) >> get2.map(assertEquals(_, None))

          }
        }
    }
  }

  test("allows building a callback when a callback of the same name exists") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Set[Label.Name]
      ) =>
        stateResource
          .flatMap(callbackRegistryResource(_))
          .use { reg =>
            val callback = reg
              .registerLabelledDoubleCounterCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.toIndexedSeq,
                IO(NonEmptyList.one(0.0 -> Map.empty[Label.Name, String]))
              )(_.values.toIndexedSeq)

            (callback >> callback).use_
          }
    }
  }

}
