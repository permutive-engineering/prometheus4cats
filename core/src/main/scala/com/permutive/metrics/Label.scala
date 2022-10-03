package com.permutive.metrics

import cats.Eq
import cats.Hash
import cats.Order
import cats.Show

object Label {

  final class Name private (val value: String) extends AnyVal {

    override def toString: String = value

  }

  object Name extends LabelNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r

    def from(string: String): Either[String, Name] =
      Either.cond(
        regex.matches(string),
        new Name(string),
        s"$string must match `$regex`"
      )

    implicit val LabelNameHash: Hash[Name] = Hash.by(_.value)

    implicit val LabelNameEq: Eq[Name] = Eq.by(_.value)

    implicit val LabelNameShow: Show[Name] = Show.show(_.value)

    implicit val LabelNameOrder: Order[Name] = Order.by(_.value)

  }

}
