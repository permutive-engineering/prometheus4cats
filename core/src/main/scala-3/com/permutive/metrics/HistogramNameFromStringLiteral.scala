package com.permutive.metrics

import scala.quoted.*

trait HistogramNameFromStringLiteral {

  inline def apply(inline t: String): Histogram.Name = ${
    HistogramNameFromStringLiteral.nameLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: String): Histogram.Name = ${
    HistogramNameFromStringLiteral.nameLiteral('t)
  }

}

object HistogramNameFromStringLiteral extends MacroUtils {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Histogram.Name] =
    s.value match {
      case Some(string) =>
        Histogram.Name
          .from(string)
          .fold(
            error,
            _ =>
              '{
                Histogram.Name.from(${ Expr(string) }).toOption.get
              }
          )
      case None =>
        abort("Histogram.Name.from")
        '{ ??? }
    }
}
