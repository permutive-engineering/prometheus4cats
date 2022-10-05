package com.permutive.metrics

import munit.ScalaCheckSuite
import org.scalacheck.Prop._

class CounterNameSuite extends ScalaCheckSuite with NameSuite[Counter.Name] {

  override def make(str: String): Either[String, Counter.Name] = Counter.Name.from(str)

  override def stringValue(result: Counter.Name): String = result.value

  override val suffix: String = "_total"

  property("names must be appended with _total") {
    forAll { (s: String) =>
      assert(Counter.Name.from(s"prefix_$s").isLeft)
      assert(Counter.Name.from(s"prefix_${s}_total").isRight)
    }
  }

}
