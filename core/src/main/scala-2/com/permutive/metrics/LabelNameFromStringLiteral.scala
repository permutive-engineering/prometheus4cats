package com.permutive.metrics

import scala.reflect.macros.blackbox

trait LabelNameFromStringLiteral {

  def apply(t: String): Label.Name =
    macro LabelNameMacros.fromStringLiteral

  implicit def fromStringLiteral(t: String): Label.Name =
    macro LabelNameMacros.fromStringLiteral

}

private[metrics] class LabelNameMacros(val c: blackbox.Context)
    extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Label.Name] = {
    val string: String = literal(t, or = "Label.Name.from({string})")

    Label.Name
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Label.Name.from(t.splice).toOption.get)
      )
  }

}
