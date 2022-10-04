package com.permutive.metrics

import scala.quoted.*

trait MetricHelpFromStringLiteral {

  inline def apply(inline t: String): Metric.Help = ${
    MetricHelpFromStringLiteral.nameLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: String): Metric.Help = ${
    MetricHelpFromStringLiteral.nameLiteral('t)
  }

}

object MetricHelpFromStringLiteral extends MacroUtils {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Metric.Help] =
    s.value match {
      case Some(string) =>
        Metric.Help
          .from(string)
          .fold(
            error,
            _ =>
            '{
              Metric.Help.from(${
                Expr(string)
              }).toOption.get
            }
          )
      case None =>
        abort("Metric.Help.from")
        '{???}
    }
}
