package com.permutive.metrics

import scala.quoted.*

trait MetricPrefixFromStringLiteral {

  inline def apply(inline t: String): Metric.Prefix = ${
    MetricPrefixFromStringLiteral.nameLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: String): Metric.Prefix = ${
    MetricPrefixFromStringLiteral.nameLiteral('t)
  }

}

object MetricPrefixFromStringLiteral extends MacroUtils {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Metric.Prefix] =
    s.value match {
      case Some(string) =>
        Metric.Prefix
          .from(string)
          .fold(
            error,
            _ =>
              '{
                Metric.Prefix.from(${ Expr(string) }).toOption.get
              }
          )
      case None =>
        abort("Metric.Prefix.from")
        '{ ??? }
    }
}
