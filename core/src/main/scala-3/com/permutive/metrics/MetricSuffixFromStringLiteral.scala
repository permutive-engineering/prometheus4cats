package com.permutive.metrics

import scala.quoted.*

trait MetricSuffixFromStringLiteral {

  inline def apply(inline t: String): Metric.Suffix = ${
    MetricSuffixFromStringLiteral.nameLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: String): Metric.Suffix = ${
    MetricSuffixFromStringLiteral.nameLiteral('t)
  }

}

object MetricSuffixFromStringLiteral extends MacroUtils {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Metric.Suffix] =
    s.value match {
      case Some(string) =>
        Metric.Suffix
          .from(string)
          .fold(
            error,
            _ =>
              '{
                Metric.Suffix.from(${ Expr(string) }).toOption.get
              }
          )
      case None =>
        abort("Metric.Suffix.from")
        '{ ??? }
    }
}
