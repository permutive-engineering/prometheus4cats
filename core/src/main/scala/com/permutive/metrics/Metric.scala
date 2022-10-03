package com.permutive.metrics

import cats.{Eq, Hash, Order, Show}

object Metric {

  type CommonLabels = Map[Label.Name, String]

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

  final class Prefix private (val value: String) extends AnyVal

  object Prefix {
    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r

    def from(string: String): Either[String, Prefix] =
      Either.cond(
        regex.matches(string),
        new Prefix(string),
        s"$string must match `$regex`"
      )

    // TODO cats instances
  }

  final class Suffix private (val value: String) extends AnyVal

  object Suffix {
    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r

    def from(string: String): Either[String, Suffix] =
      Either.cond(
        regex.matches(string),
        new Suffix(string),
        s"$string must match `$regex`"
      )

    // TODO cats instances
  }
}
