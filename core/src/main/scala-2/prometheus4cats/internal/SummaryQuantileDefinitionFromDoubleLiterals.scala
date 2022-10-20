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

trait SummaryQuantileDefinitionFromDoubleLiterals {

  def apply(v: Double, e: Double): Summary.QuantileDefinition =
    macro SummaryQuantileDefinitionMacros.fromDoubleLiterals

}

private[prometheus4cats] class SummaryQuantileDefinitionMacros(val c: blackbox.Context) extends MacroUtils {
  def fromDoubleLiterals(v: c.Expr[Double], e: c.Expr[Double]): c.Expr[Summary.QuantileDefinition] = {
    val value: Double = literal(v, or = "Summary.QuantileDefinition.from({double}, {double})")
    val error: Double = literal(e, or = "Summary.QuantileDefinition.from({double}, {double})")

    Summary.QuantileDefinition
      .from(value, error)
      .fold(
        abort,
        _ => c.universe.reify(Summary.QuantileDefinition.from(v.splice, e.splice).toOption.get)
      )
  }

}
