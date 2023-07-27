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

// in a different package to the rest of the codebase to verify private annotations work
package test

import java.math.BigInteger
import java.util.concurrent.TimeUnit

import cats.effect.IO

import prometheus4cats._

import scala.concurrent.duration._

object MetricsFactoryDslTest {
  val factory: MetricFactory.WithCallbacks[IO] = MetricFactory.builder.withPrefix("prefix").noop[IO]

  val gaugeBuilder = factory.gauge("test")

  val doubleGaugeBuilder = gaugeBuilder.ofDouble.help("help")
  doubleGaugeBuilder.build
  doubleGaugeBuilder.asCurrentTimeRecorder
  doubleGaugeBuilder.asCurrentTimeRecorder(_.toUnit(TimeUnit.NANOSECONDS))
  doubleGaugeBuilder.asTimer.build
  doubleGaugeBuilder.asOutcomeRecorder.build

  val doubleLabelledGaugeBuilder =
    doubleGaugeBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString)
  doubleLabelledGaugeBuilder.build
  doubleLabelledGaugeBuilder.contramap[Int](_.toDouble).contramapLabels[(Int, Short, Long)] { case (i, s, l) =>
    (i.toString, s.toInt, BigInteger.valueOf(l))
  }
  doubleLabelledGaugeBuilder.asTimer.build
  doubleLabelledGaugeBuilder.asCurrentTimeRecorder
  doubleLabelledGaugeBuilder.asCurrentTimeRecorder(_.toUnit(TimeUnit.NANOSECONDS))
  doubleLabelledGaugeBuilder.asOutcomeRecorder.build

  val doubleLabelsGaugeBuilder = doubleGaugeBuilder.labels(Label.Name("test") -> ((s: String) => s)).build

  val longGaugeBuilder = gaugeBuilder.ofLong.help("help")
  longGaugeBuilder.build
  longGaugeBuilder.asCurrentTimeRecorder
  longGaugeBuilder.asCurrentTimeRecorder(_.toDays)
  longGaugeBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString).build
  longGaugeBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build

  val counterBuilder = factory.counter("test_total")

  counterBuilder.ofDouble.help("sdfs").build.map(_.inc)

  val doubleCounterBuilder = counterBuilder.ofDouble.help("help")
  doubleCounterBuilder.build
  doubleCounterBuilder.asOutcomeRecorder.build
  doubleCounterBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build

  val doubleLabelledCounterBuilder =
    doubleCounterBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString)
  doubleLabelledCounterBuilder.build
  doubleLabelledCounterBuilder.contramap[Int](_.toDouble).build
  doubleLabelledCounterBuilder.asOutcomeRecorder.build

  val longCounterBuilder = counterBuilder.ofLong.help("help")
  longCounterBuilder.build
  longCounterBuilder
    .label[String]("label1")
    .label[Int]("label2")
    .label[BigInteger]("label3", _.toString)
    .build
    .map(_.inc(("dsfsf", 1, BigInteger.ONE)))
  longCounterBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build

  val histogramBuilder = factory.histogram("test2")

  val doubleHistogramBuilder = histogramBuilder.ofDouble.help("help").defaultHttpBuckets
  doubleHistogramBuilder.build
  doubleHistogramBuilder.asTimer.build
  doubleHistogramBuilder
    .label[String]("label1")
    .label[Int]("label2")
    .label[BigInteger]("label3", _.toString)
    .asTimer
    .build
  doubleHistogramBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).asTimer.build

  histogramBuilder.ofLong.help("me").linearBuckets[Nat._1](1, 10)
  histogramBuilder.ofDouble.help("me").exponentialBuckets[Nat._1](1, 10)

  val longHistogramBuilder = histogramBuilder.ofLong.help("help").buckets(1, 2)
  longHistogramBuilder.build
  longHistogramBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString).build
  longHistogramBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build

  val infoBuilder = factory.info("test_info").help("help")
  infoBuilder.build

  val doubleSummaryBuilder =
    factory
      .summary("summary")
      .ofDouble
      .help("some summary")
      .quantile(1.0, 0.1)
      .maxAge(10.seconds)
      .ageBuckets(5)
}
