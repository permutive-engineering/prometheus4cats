package com.permutive.metrics

import scala.reflect.macros.blackbox

trait GaugeNameFromStringLiteral {

  implicit def fromStringLiteral(t: String): Gauge.Name =
    macro GaugeNameMacros.fromStringLiteral

}

private[metrics] class GaugeNameMacros(val c: blackbox.Context)
    extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Gauge.Name] = {
    val string: String = literal(t, or = "Gauge.Name.from({string})")

    Gauge.Name
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Gauge.Name.from(t.splice).toOption.get)
      )
  }

}
