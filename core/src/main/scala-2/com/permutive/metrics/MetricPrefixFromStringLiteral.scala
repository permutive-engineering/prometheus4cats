package com.permutive.metrics

import scala.reflect.macros.blackbox

trait MetricPrefixFromStringLiteral {

  implicit def fromStringLiteral(t: String): Metric.Prefix =
    macro MetricPrefixMacros.fromStringLiteral

}

private[metrics] class MetricPrefixMacros(val c: blackbox.Context)
    extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Metric.Prefix] = {
    val string: String = literal(t, or = "Metric.Prefix.from({string})")

    Metric.Prefix
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Metric.Prefix.from(t.splice).toOption.get)
      )
  }

}
