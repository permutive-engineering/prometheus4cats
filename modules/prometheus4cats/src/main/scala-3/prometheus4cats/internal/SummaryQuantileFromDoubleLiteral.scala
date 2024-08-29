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

import scala.quoted.*

import prometheus4cats.*

private[prometheus4cats] trait SummaryQuantileFromDoubleLiteral {

  inline def apply(inline t: Double): Summary.Quantile = ${
    SummaryQuantileFromDoubleLiteral.quantileLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: Double): Summary.Quantile = ${
    SummaryQuantileFromDoubleLiteral.quantileLiteral('t)
  }

}

private[prometheus4cats] object SummaryQuantileFromDoubleLiteral extends MacroUtils {
  def quantileLiteral(d: Expr[Double])(using q: Quotes): Expr[Summary.Quantile] =
    d.value match {
      case Some(int) =>
        Summary.Quantile
          .from(int)
          .fold(
            error,
            _ =>
              '{
                Summary.Quantile.from(${ Expr(int) }).toOption.get
              }
          )
      case None =>
        abort("Summary.Quantile.from")
        '{ ??? }
    }
}
