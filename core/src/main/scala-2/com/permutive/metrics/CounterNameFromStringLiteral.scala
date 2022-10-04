package com.permutive.metrics

import scala.reflect.macros.blackbox

trait CounterNameFromStringLiteral {

  implicit def fromStringLiteral(t: String): Counter.Name =
    macro CounterNameMacros.fromStringLiteral

}

private[metrics] class CounterNameMacros(val c: blackbox.Context)
    extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Counter.Name] = {
    val string: String = literal(t, or = "Counter.Name.from({string})")

    Counter.Name
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Counter.Name.from(t.splice).toOption.get)
      )
  }

}
