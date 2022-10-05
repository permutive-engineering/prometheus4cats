package com.permutive.metrics

import munit.ScalaCheckSuite

class GaugeNameSuite extends ScalaCheckSuite with NameSuite[Gauge.Name] {
  override def make(str: String): Either[String, Gauge.Name] = Gauge.Name.from(str)

  override def stringValue(result: Gauge.Name): String = result.value
}
