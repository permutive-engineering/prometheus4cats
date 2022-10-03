package com.permutive.metrics

import scala.quoted.*

trait LabelNameFromStringLiteral {

  implicit inline def fromStringLiteral(inline t: String): Label.Name = ${
    LabelNameFromStringLiteral.nameLiteral('t)
  }

}

object LabelNameFromStringLiteral {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Label.Name] =
    s.value match {
      case Some(string) =>
        Label.Name
          .from(string)
          .fold(
            e => throw new RuntimeException(e),
            _ =>
              '{
                Label.Name
                  .from(${ Expr(string) })
                  .fold(e => throw new RuntimeException(e), identity)
              }
          )
      case None =>
        q.reflect.report.error(
          "This method uses a macro to verify that a Name literal is valid. Use Label.Name.from if you have a dynamic value you want to parse as a name."
        )
        '{ ??? }
    }
}
