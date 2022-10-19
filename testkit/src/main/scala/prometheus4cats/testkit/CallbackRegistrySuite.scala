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
import org.scalacheck.effect.PropF._
import prometheus4cats._

trait CallbackRegistrySuite[State] extends RegistrySuite[State] { self: CatsEffectSuite =>
  def callbackRegistryResource(state: State): Resource[IO, CallbackRegistry[IO]]

  test("register and set a counter") {
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
            reg
              .registerDoubleCounterCallback(prefix, name, help, commonLabels, IO(value)) >> getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            ).map(res => if (value >= 0) assertEquals(res, Some(value)) else assertEquals(res, Some(0.0)))

          }
        }
    }
  }

  test("register and set a labelled counter") {
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
            reg
              .registerLabelledDoubleCounterCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                IO(value, labels)
              )(_.values.toIndexedSeq) >> getCounterValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels
            ).map(res => if (value >= 0) assertEquals(res, Some(value)) else assertEquals(res, Some(0.0)))
          }
        }
    }
  }

  test("register and set a gauge") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Gauge.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          value: Double
      ) =>
        stateResource.use { state =>
          callbackRegistryResource(state).use { reg =>
            reg
              .registerDoubleGaugeCallback(prefix, name, help, commonLabels, IO(value)) >> getGaugeValue(
              state,
              prefix,
              name,
              help,
              commonLabels
            ).map(assertEquals(_, Some(value)))
          }
        }
    }
  }

  test("register and set a labelled gauge") {
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
            reg
              .registerLabelledDoubleGaugeCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                IO(value, labels)
              )(_.values.toIndexedSeq) >> getGaugeValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              labels
            ).map(assertEquals(_, Some(value)))
          }
        }
    }
  }

  test("register and set a histogram") {
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

            reg
              .registerDoubleHistogramCallback(
                prefix,
                name,
                help,
                commonLabels,
                buckets,
                IO(Histogram.Value(sum, bucketValues))
              ) >> getHistogramValue(
              state,
              prefix,
              name,
              help,
              commonLabels,
              buckets
            ).map { res =>
              assertEquals(res, Some(expected))
            }

          }
        }
    }
  }

  test("register and set a labelled histogram") {
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

            reg
              .registerLabelledDoubleHistogramCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.keys.toIndexedSeq,
                buckets,
                IO(Histogram.Value(sum, bucketValues), labels)
              )(_.values.toIndexedSeq) >> getHistogramValue(
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