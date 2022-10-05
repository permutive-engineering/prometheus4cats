package com.permutive.metrics

import munit.ScalaCheckSuite

class HistogramNameSuite extends ScalaCheckSuite with NameSuite[Histogram.Name] {
  override def make(str: String): Either[String, Histogram.Name] = Histogram.Name.from(str)

  override def stringValue(result: Histogram.Name): String = result.value
}
