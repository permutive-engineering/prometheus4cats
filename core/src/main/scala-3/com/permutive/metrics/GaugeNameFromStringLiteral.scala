package com.permutive.metrics

import scala.quoted.*

trait GaugeNameFromStringLiteral {

  inline def apply(inline t: String): Gauge.Name = ${
    GaugeNameFromStringLiteral.nameLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: String): Gauge.Name = ${
    GaugeNameFromStringLiteral.nameLiteral('t)
  }

}

object GaugeNameFromStringLiteral extends MacroUtils {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Gauge.Name] =
    s.value match {
      case Some(string) =>
        Gauge.Name
          .from(string)
          .fold(
            error,
            _ =>
              '{
                Gauge.Name.from(${ Expr(string) }).toOption.get
              }
          )
      case None =>
        abort("Gauge.Name.from")
        '{ ??? }
    }
}
