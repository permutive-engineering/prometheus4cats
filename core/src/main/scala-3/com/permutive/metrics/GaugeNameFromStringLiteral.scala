package com.permutive.metrics

import scala.quoted.*

trait GaugeNameFromStringLiteral {

  implicit inline def fromStringLiteral(inline t: String): Gauge.Name = ${
    GaugeNameFromStringLiteral.nameLiteral('t)
  }

}

object GaugeNameFromStringLiteral {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Gauge.Name] =
    s.value match {
      case Some(string) =>
        Gauge.Name
          .from(string)
          .fold(
            e => throw new RuntimeException(e),
            _ =>
              '{
                Gauge.Name
                  .from(${ Expr(string) })
                  .fold(e => throw new RuntimeException(e), identity)
              }
          )
      case None =>
        q.reflect.report.error(
          "This method uses a macro to verify that a Name literal is valid. Use Gauge.Name.from if you have a dynamic value you want to parse as a name."
        )
        '{ ??? }
    }
}
