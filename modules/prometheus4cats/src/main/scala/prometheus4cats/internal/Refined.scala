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

import java.util.regex.Pattern

import cats.Hash
import cats.Order
import cats.Show
import cats.syntax.all._

@SuppressWarnings(Array("scalafix:DisableSyntax.valInAbstract"))
abstract private[prometheus4cats] class Refined[A: Hash: Order: Show, B <: Refined.Value[A]](
    make: A => B,
    test: A => Boolean,
    nonMatchMessage: A => String
) {

  /** Parse from the given cakye
    *
    * @param a
    *   value from which to parse a gauge name
    * @return
    *   a parsed `B` or failure message, represented by an [[scala.Either]]
    */
  def from(a: A): Either[String, B] =
    Either.cond(test(a), make(a), nonMatchMessage(a))

  /** Unsafely parse a `B` from the given `A`
    *
    * @param a
    *   value from which to parse a counter name
    * @return
    *   a parsed `B`
    * @throws java.lang.IllegalArgumentException
    *   if `a` is not valid
    */
  def unsafeFrom(a: A): B =
    from(a).fold(msg => throw new IllegalArgumentException(msg), identity) // scalafix:ok

  implicit val catsInstances: Hash[B] with Order[B] with Show[B] = new Hash[B] with Order[B] with Show[B] {

    override def hash(x: B): Int = x.value.hash

    override def compare(x: B, y: B): Int = x.value.compare(y.value)

    override def show(t: B): String = t.value.show

    override def eqv(x: B, y: B): Boolean = Order[A].eqv(x.value, y.value)

  }

  implicit val ordering: Ordering[B] = Order[A].contramap[B](_.value).toOrdering

}

object Refined {

  trait Value[A] extends Any {

    def value: A

    override def toString(): String = value.toString() // scalafix:ok

  }

  abstract class Regex[B <: Value[String]](regex: Pattern, make: String => B)
      extends Refined[String, B](make, regex.matcher(_).matches(), a => s"$a must match `$regex`")

}
