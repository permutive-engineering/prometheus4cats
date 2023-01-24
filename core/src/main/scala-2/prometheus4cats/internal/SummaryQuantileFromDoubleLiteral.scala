/*
 * Copyright 2022 Permutive
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package prometheus4cats.internal

import prometheus4cats.Summary

import scala.reflect.macros.blackbox

private[prometheus4cats] trait SummaryQuantileFromDoubleLiteral {

  def apply(t: Double): Summary.Quantile =
    macro SummaryQuantileMacros.fromDoubleLiteral

  implicit def fromDoubleLiteral(t: Double): Summary.Quantile =
    macro SummaryQuantileMacros.fromDoubleLiteral

}

private[prometheus4cats] class SummaryQuantileMacros(val c: blackbox.Context) extends MacroUtils {

  def fromDoubleLiteral(t: c.Expr[Double]): c.Expr[Summary.Quantile] = {
    val double: Double = literal(t, or = "Summary.Quantile.from({double})")

    Summary.Quantile
      .from(double)
      .fold(
        abort,
        _ => c.universe.reify(Summary.Quantile.from(t.splice).toOption.get)
      )
  }

}
