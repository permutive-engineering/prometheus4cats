package com.permutive.metrics

import munit.ScalaCheckSuite

class MetricPrefixSuite extends ScalaCheckSuite with NameSuite[Metric.Prefix] {
  override def make(str: String): Either[String, Metric.Prefix] = Metric.Prefix.from(str)

  override def stringValue(result: Metric.Prefix): String = result.value
}
