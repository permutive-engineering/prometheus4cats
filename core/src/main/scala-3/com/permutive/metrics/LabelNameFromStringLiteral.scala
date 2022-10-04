package com.permutive.metrics

import scala.quoted.*

trait LabelNameFromStringLiteral {

  inline def apply(inline t: String): Label.Name = ${
    LabelNameFromStringLiteral.nameLiteral('t)
  }

  inline implicit def fromStringLiteral(inline t: String): Label.Name = ${
    LabelNameFromStringLiteral.nameLiteral('t)
  }

}

object LabelNameFromStringLiteral extends MacroUtils {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Label.Name] =
    s.value match {
      case Some(string) =>
        Label.Name
          .from(string)
          .fold(
            error,
            _ =>
              '{
                Label.Name
                  .from(${
                    Expr(string)
                  })
                  .toOption
                  .get
              }
          )
      case None =>
        abort("Label.Name.from")
        '{
          ???
        }
    }
}
