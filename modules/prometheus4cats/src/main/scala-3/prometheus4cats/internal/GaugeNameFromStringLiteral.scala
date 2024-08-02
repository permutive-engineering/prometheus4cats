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

private[prometheus4cats] trait GaugeNameFromStringLiteral {

  inline def apply(inline t: String): Gauge.Name = ${
    GaugeNameFromStringLiteral.nameLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: String): Gauge.Name = ${
    GaugeNameFromStringLiteral.nameLiteral('t)
  }

}

private[prometheus4cats] object GaugeNameFromStringLiteral extends MacroUtils {
  def nameLiteral(s: Expr[String])(using q: Quotes): Expr[Gauge.Name] =
    s.value match {
      case Some(string) =>
        Gauge.Name
          .from(string)
          .fold(
            error,
            _ =>
              '{
                Gauge.Name.from(${ Expr(string) }).toOption.get
              }
          )
      case None =>
        abort("Gauge.Name.from")
        '{ ??? }
    }
}
