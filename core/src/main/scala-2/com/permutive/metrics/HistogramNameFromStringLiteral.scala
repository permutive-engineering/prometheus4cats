package com.permutive.metrics

import scala.reflect.macros.blackbox

trait HistogramNameFromStringLiteral {

  def apply(t: String): Histogram.Name =
    macro HistogramNameMacros.fromStringLiteral

  implicit def fromStringLiteral(t: String): Histogram.Name =
    macro HistogramNameMacros.fromStringLiteral

}

private[metrics] class HistogramNameMacros(val c: blackbox.Context)
    extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Histogram.Name] = {
    val string: String = literal(t, or = "Histogram.Name.from({string})")

    Histogram.Name
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Histogram.Name.from(t.splice).toOption.get)
      )
  }

}
