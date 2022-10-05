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

package com.permutive.metrics

import java.math.BigInteger

import cats.effect.IO
import com.permutive.metrics.internal.counter.CounterDsl
import com.permutive.metrics.internal.gauge.GaugeDsl
import com.permutive.metrics.internal.histogram.HistogramDsl

object MetricsFactoryDslTest {
  val factory: MetricsFactory[IO] = MetricsFactory.builder.withPrefix("prefix").noop

  val gaugeBuilder: GaugeDsl[IO] = factory.gauge("test").help("help")
  gaugeBuilder.build
  gaugeBuilder.resource
  gaugeBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString).build
  gaugeBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2")).build

  val counterBuilder: CounterDsl[IO] = factory.counter("test_total").help("help")
  counterBuilder.build
  counterBuilder.resource
  counterBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString)
  counterBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2"))

  val histogramBuilder: HistogramDsl[IO] = factory.histogram("test2").help("help").buckets(0.1, 1.0)
  histogramBuilder.build
  histogramBuilder.resource
  histogramBuilder.label[String]("label1").label[Int]("label2").label[BigInteger]("label3", _.toString)
  histogramBuilder.unsafeLabels(Label.Name("label1"), Label.Name("label2"))
}
