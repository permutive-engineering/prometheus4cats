package com.permutive.metrics

import munit.ScalaCheckSuite

class LabelNameSuite extends ScalaCheckSuite with NameSuite[Label.Name] {
  override def make(str: String): Either[String, Label.Name] = Label.Name.from(str)

  override def stringValue(result: Label.Name): String = result.value
}
