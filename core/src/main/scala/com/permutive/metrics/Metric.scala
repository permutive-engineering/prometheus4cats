package com.permutive.metrics

import cats.{Eq, Hash, Order, Show}

object Metric {

  final class Help private (val value: String) extends AnyVal {

    override def toString: String = value

  }

  object Help extends MetricHelpFromStringLiteral {

    def from(string: String): Either[String, Help] =
      Either.cond(!string.isBlank, new Help(string), s"must not be empty blank")

    implicit val MetricHelpHash: Hash[Help] = Hash.by(_.value)

    implicit val MetricHelpEq: Eq[Help] = Eq.by(_.value)

    implicit val MetricHelpShow: Show[Help] = Show.show(_.value)

    implicit val MetricHelpOrder: Order[Help] = Order.by(_.value)

  }

}
