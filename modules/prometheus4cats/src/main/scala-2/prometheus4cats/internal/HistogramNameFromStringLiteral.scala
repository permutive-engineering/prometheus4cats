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

import scala.reflect.macros.blackbox

import prometheus4cats.Histogram

private[prometheus4cats] trait HistogramNameFromStringLiteral {

  def apply(t: String): Histogram.Name =
    macro HistogramNameMacros.fromStringLiteral

  implicit def fromStringLiteral(t: String): Histogram.Name =
    macro HistogramNameMacros.fromStringLiteral

}

private[prometheus4cats] class HistogramNameMacros(val c: blackbox.Context) extends MacroUtils {

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
