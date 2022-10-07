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

package openmetrics4s

import java.math.BigInteger

import cats.effect.IO

object MetricsFactoryDslTest {
  val factory: MetricsFactory[IO] = MetricsFactory.builder.withPrefix("prefix").noop

  val gaugeBuilder = factory.gauge("test")

  val doubleGaugeBuilder = gaugeBuilder.ofDouble.help("help")
  doubleGaugeBuilder.build
  doubleGaugeBuilder.resource
  doubleGaugeBuilder.asTimer.build
  doubleGaugeBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString).asTimer.build
  doubleGaugeBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).asTimer.build

  val longGaugeBuilder = gaugeBuilder.ofLong.help("help")
  longGaugeBuilder.build
  longGaugeBuilder.resource
  longGaugeBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString).build
  longGaugeBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build

  val counterBuilder = factory.counter("test_total")

  val doubleCounterBuilder = counterBuilder.ofDouble.help("help")
  doubleCounterBuilder.build
  doubleCounterBuilder.resource
  doubleCounterBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString).build
  doubleCounterBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build

  val longCounterBuilder = counterBuilder.ofLong.help("help")
  longCounterBuilder.build
  longCounterBuilder.resource
  longCounterBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString).build
  longCounterBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build

  val histogramBuilder = factory.histogram("test2")

  val doubleHistogramBuilder = histogramBuilder.ofDouble.help("help").defaultHttpBuckets
  doubleHistogramBuilder.build
  doubleHistogramBuilder.resource
  doubleHistogramBuilder.asTimer.build
  doubleHistogramBuilder
    .label[String]("label1")
    .label[Int]("label2")
    .label[BigInteger]("label3", _.toString)
    .asTimer
    .build
  doubleHistogramBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).asTimer.build

  val longHistogramBuilder = histogramBuilder.ofLong.help("help").buckets(1, 2)
  longHistogramBuilder.build
  longHistogramBuilder.resource
  longHistogramBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString).build
  longHistogramBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build
}
