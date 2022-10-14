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

import cats.{Eq, Hash, Order, Show}

object Label {

  /** Refined value class for a label name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal {

    override def toString: String = s"""Label.Name("$value")"""

  }

  object Name extends LabelNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern

    /** Parse a [[Name]] from the given string
      *
      * @param string
      *   value from which to parse a label name
      * @return
      *   a parsed [[Name]] or failure message, represented by an [[scala.Either]]
      */
    def from(string: String): Either[String, Name] =
      Either.cond(
        regex.matcher(string).matches(),
        new Name(string),
        s"$string must match `$regex`"
      )

    /** Unsafely parse a [[Name]] from the given string
      *
      * @param string
      *   value from which to parse a counter name
      * @return
      *   a parsed [[Name]]
      * @throws java.lang.IllegalArgumentException
      *   if `string` is not valid
      */
    def unsafeFrom(string: String): Name =
      from(string).fold(msg => throw new IllegalArgumentException(msg), identity)

    // prevents macro compilation problems with the status label
    private[prometheus4cats] val outcomeStatus = new Name("outcome_status")

    implicit val catsInstances: Hash[Name] with Order[Name] with Show[Name] = new Hash[Name]
      with Order[Name]
      with Show[Name] {
      override def hash(x: Name): Int = Hash[String].hash(x.value)

      override def compare(x: Name, y: Name): Int = Order[String].compare(x.value, y.value)

      override def show(t: Name): String = t.value

      override def eqv(x: Name, y: Name): Boolean = Eq[String].eqv(x.value, y.value)
    }

  }

}
