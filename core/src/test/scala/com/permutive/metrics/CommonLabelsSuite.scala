package com.permutive.metrics

import com.permutive.metrics.Fixtures.ordinaryChars
import com.permutive.metrics.Metric.CommonLabels
import munit.ScalaCheckSuite
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop._

class CommonLabelsSuite extends ScalaCheckSuite {
  implicit val labelNameArb: Arbitrary[Label.Name] = Arbitrary(for {
    c <- Gen.alphaChar
    s <- Gen.alphaNumStr
    l <- Gen.oneOf(Label.Name.from(s"$c$s").toOption)
  } yield l)

  test("labels can be empty") {
    assertEquals(CommonLabels.from(), Right(CommonLabels.empty))
  }

  property("parses successfully label count is no more than 10") {
    implicit val mapArb: Arbitrary[Map[Label.Name, String]] =
      Arbitrary(
        for {
          size <- Gen.choose(1, 10)
          map <- Gen.mapOfN(size, Arbitrary.arbitrary[(Label.Name, String)])
        } yield map
      )

    forAll { (ls: Map[Label.Name, String]) =>
      assertEquals(CommonLabels.from(ls).map(_.value), Right(ls))
    }
  }

  property("fails to parse when label count is more than 10") {
    implicit val mapArb: Arbitrary[Map[Label.Name, String]] =
      Arbitrary(
        for {
          size <- Gen.choose(11, 100)
          map <- Gen.mapOfN(size, Arbitrary.arbitrary[(Label.Name, String)])
        } yield map
      )

    forAll { (ls: Map[Label.Name, String]) =>
      assert(CommonLabels.from(ls).isLeft)
    }
  }

  property("fails to parse when label names are invalid") {
    implicit val stringArb: Arbitrary[String] = Arbitrary(Gen.asciiStr.suchThat(!_.forall(ordinaryChars.contains)))

    implicit val mapArb: Arbitrary[Map[String, String]] =
      Arbitrary(
        for {
          size <- Gen.choose(1, 10)
          map <- Gen.mapOfN(size, Arbitrary.arbitrary[(String, String)])
        } yield map
      )

    forAll { (ls: Map[String, String]) =>
      assert(CommonLabels.fromStrings(ls).isLeft)
    }
  }
}
