package com.permutive.metrics

import scala.quoted.*

trait CounterNameFromStringLiteral {

  inline def apply(inline t: String): Counter.Name = ${
    CounterNameFromStringLiteral.nameLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: String): Counter.Name = ${
    CounterNameFromStringLiteral.nameLiteral('t)
  }

}

object CounterNameFromStringLiteral extends MacroUtils {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Counter.Name] =
    s.value match {
      case Some(string) =>
        Counter.Name
          .from(string)
          .fold(
            error,
            _ =>
              '{
                Counter.Name.from(${ Expr(string) }).toOption.get
              }
          )
      case None =>
        abort("Counter.Name.from")
        '{ ??? }
    }
}
