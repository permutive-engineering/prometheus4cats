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

package com.permutive.metrics

import scala.reflect.macros.blackbox

trait MetricHelpFromStringLiteral {

  def apply(t: String): Metric.Help =
    macro MetricHelpMacros.fromStringLiteral

  implicit def fromStringLiteral(t: String): Metric.Help =
    macro MetricHelpMacros.fromStringLiteral

}

private[metrics] class MetricHelpMacros(val c: blackbox.Context) extends MacroUtils {

  def fromStringLiteral(t: c.Expr[String]): c.Expr[Metric.Help] = {
    val string: String = literal(t, or = "Label.Name.from({string})")

    Metric.Help
      .from(string)
      .fold(
        abort,
        _ => c.universe.reify(Metric.Help.from(t.splice).toOption.get)
      )
  }

}
