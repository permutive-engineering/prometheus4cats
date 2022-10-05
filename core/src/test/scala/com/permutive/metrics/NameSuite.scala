package com.permutive.metrics

import com.permutive.metrics.Fixtures.{alphaChars, ordinaryChars}
import munit.ScalaCheckSuite
import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen}

trait NameSuite[A] { self: ScalaCheckSuite =>
  val suffix = ""

  def make(str: String): Either[String, A]
  def stringValue(result: A): String

  implicit val stringArb: Arbitrary[String] = Arbitrary(Gen.alphaNumStr)

  test("names must not be empty") {
    assert(make("").isLeft)
  }

  property("names must start with an alpha char") {
    implicit val charArb: Arbitrary[Char] = Arbitrary(Gen.oneOf(alphaChars))

    forAll { (s: String, c: Char, i: Int) =>
      assert(make(s"$i$s$suffix").isLeft)

      val v = s"$c$s$suffix"
      assertEquals(make(v).map(stringValue), Right(v))
    }
  }

  property("names must not contain special chars") {
    implicit val stringArb: Arbitrary[String] = Arbitrary(Gen.asciiStr.suchThat(!_.forall(ordinaryChars.contains)))
    implicit val charArb: Arbitrary[Char] = Arbitrary(Gen.asciiChar.suchThat(!ordinaryChars.contains(_)))

    forAll { (s: String, c: Char) =>
      assert(make(s"$c$s$suffix").isLeft)
    }
  }
}
