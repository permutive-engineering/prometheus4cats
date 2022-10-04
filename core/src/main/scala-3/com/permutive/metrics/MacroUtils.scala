package com.permutive.metrics

import scala.quoted.*
import scala.util.control.NoStackTrace

case class RefinementError(msg: String)
    extends RuntimeException(msg)
    with NoStackTrace

trait MacroUtils {
  def error(msg: String): Nothing = throw RefinementError(msg)

  def abort(constructorName: String)(using q: Quotes): Unit =
    q.reflect.report.error(
      s"This method uses a macro to verify that a literal is valid. Use $constructorName if you have a dynamic value you want to parse."
    )
}
