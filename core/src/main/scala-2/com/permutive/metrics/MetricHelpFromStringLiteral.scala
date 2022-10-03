package com.permutive.metrics

import scala.reflect.macros.blackbox

trait MetricHelpFromStringLiteral {

  implicit def fromStringLiteral(t: String): Metric.Help =
    macro MetricHelpMacros.fromStringLiteral

}

private[metrics] class MetricHelpMacros(val c: blackbox.Context)
    extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Metric.Help] = {
    val string: String = literal(t, or = "Label.Name.from({string})")

    Metric.Help
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Metric.Help.from(t.splice).toOption.get)
      )
  }

}
