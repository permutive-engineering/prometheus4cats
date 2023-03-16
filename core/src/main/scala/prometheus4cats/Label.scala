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

package prometheus4cats

import java.util.regex.Pattern

import prometheus4cats.internal.LabelNameFromStringLiteral

object Label {

  /** Refined value class for a label name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal with internal.Refined.Value[String] {

    override def toString: String = s"""Label.Name("$value")"""

  }

  object Name extends internal.Refined[String, Name] with LabelNameFromStringLiteral {
    // prevents macro compilation problems with the status label
    private[prometheus4cats] val outcomeStatus = new Name("outcome_status")

    private val invalidNames: Set[String] = Set("quantile", "le")

    protected val regex: Pattern = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern

    override protected def make(a: String): Name = new Name(a)

    override protected def test(a: String): Boolean =
      !invalidNames.contains(a) && regex.matcher(a).matches()

    override protected def nonMatchMessage(a: String): String =
      s"Label.Name must match pattern `$regex` and not be one of ${invalidNames.mkString(",")}"
  }

}
