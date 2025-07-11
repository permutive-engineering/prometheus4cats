/*
 * Copyright 2022-2025 Permutive Ltd. <https://permutive.com>
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

import prometheus4cats.Info

private[prometheus4cats] trait InfoNameFromStringLiteral {

  def apply(t: String): Info.Name =
    macro InfoNameMacros.fromStringLiteral

  implicit def fromStringLiteral(t: String): Info.Name =
    macro InfoNameMacros.fromStringLiteral

}

private[prometheus4cats] class InfoNameMacros(val c: blackbox.Context) extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Info.Name] = {
    val string: String = literal(t, or = "Info.Name.from({string})")

    Info.Name
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Info.Name.from(t.splice).toOption.get)
      )
  }

}
