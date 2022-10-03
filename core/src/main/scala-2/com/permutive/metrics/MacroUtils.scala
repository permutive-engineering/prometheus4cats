package com.permutive.metrics

import scala.reflect.macros.blackbox

private[metrics] trait MacroUtils {

  val c: blackbox.Context

  import c.universe._

  def abort(msg: String) = c.abort(c.enclosingPosition, msg)

  def literal(t: c.Expr[String], or: String): String = t.tree match {
    case Literal(Constant(value)) => value.asInstanceOf[String]
    case _                        => abort(s"compile-time refinement only works with literals, use $or instead")
  }

}
