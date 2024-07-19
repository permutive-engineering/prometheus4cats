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

import prometheus4cats.Summary

private[prometheus4cats] trait SummaryNameFromStringLiteral {

  def apply(t: String): Summary.Name =
    macro SummaryNameMacros.fromStringLiteral

  implicit def fromStringLiteral(t: String): Summary.Name =
    macro SummaryNameMacros.fromStringLiteral

}

private[prometheus4cats] class SummaryNameMacros(val c: blackbox.Context) extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Summary.Name] = {
    val string: String = literal(t, or = "Summary.Name.from({string})")

    Summary.Name
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Summary.Name.from(t.splice).toOption.get)
      )
  }

}
