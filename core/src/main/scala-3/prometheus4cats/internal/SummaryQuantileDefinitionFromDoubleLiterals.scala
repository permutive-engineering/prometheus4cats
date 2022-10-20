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

import prometheus4cats._

import scala.quoted.*

trait SummaryQuantileDefinitionFromDoubleLiterals {

  inline def apply(inline v: Double, e: Double): Summary.QuantileDefinition = ${
    SummaryQuantileDefinitionFromDoubleLiterals.quantileDefinitionLiteral('v, 'e)
  }
}

object SummaryQuantileDefinitionFromDoubleLiterals extends MacroUtils {
  def quantileDefinitionLiteral(d: Expr[Double], e: Expr[Double])(using q: Quotes): Expr[Summary.QuantileDefinition] =
    (d.value, e.value) match {
      case (Some(value), Some(err)) =>
        Summary.QuantileDefinition
          .from(value, err)
          .fold(
            error,
            _ =>
            '{
              Summary.QuantileDefinition.from(${ Expr(value) }, ${ Expr(err) }).toOption.get
            }
          )
      case _ =>
        abort("Summary.QuantileDefinition.from")
        '{ ??? }
    }
}
