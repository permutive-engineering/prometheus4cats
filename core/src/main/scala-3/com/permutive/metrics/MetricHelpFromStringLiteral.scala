package com.permutive.metrics

import scala.quoted.*

trait MetricHelpFromStringLiteral {

  implicit inline def fromStringLiteral(inline t: String): Metric.Help = ${
    MetricHelpFromStringLiteral.nameLiteral('t)
  }

}

object MetricHelpFromStringLiteral {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Metric.Help] =
    s.value match {
      case Some(string) =>
        Metric.Help
          .from(string)
          .fold(
            e => throw new RuntimeException(e),
            _ =>
              '{
                Metric.Help
                  .from(${ Expr(string) })
                  .fold(e => throw new RuntimeException(e), identity)
              }
          )
      case None =>
        q.reflect.report.error(
          "This method uses a macro to verify that a Name literal is valid. Use Gauge.Name.from if you have a dynamic value you want to parse as a name."
        )
        '{ ??? }
    }
}
