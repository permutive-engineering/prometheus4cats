package com.permutive.metrics

import scala.reflect.macros.blackbox

trait MetricSuffixFromStringLiteral {

  implicit def fromStringLiteral(t: String): Metric.Suffix =
    macro MetricSuffixMacros.fromStringLiteral

}

private[metrics] class MetricSuffixMacros(val c: blackbox.Context)
    extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Metric.Suffix] = {
    val string: String = literal(t, or = "Metric.Suffix.from({string})")

    Metric.Suffix
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Metric.Suffix.from(t.splice).toOption.get)
      )
  }

}
