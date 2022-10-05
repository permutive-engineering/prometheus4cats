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
