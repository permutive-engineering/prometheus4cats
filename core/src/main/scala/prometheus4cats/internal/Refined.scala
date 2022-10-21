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

import java.util.regex.Pattern

import cats.{Hash, Order, Show}

abstract class Refined[A, B <: Refined.Value[A]](implicit hashA: Hash[A], orderA: Order[A], showA: Show[A]) {
  protected def make(a: A): B
  protected def test(a: A): Boolean
  protected def nonMatchMessage(a: A): String

  /** Parse from the given cakye
    *
    * @param a
    *   value from which to parse a gauge name
    * @return
    *   a parsed `B` or failure message, represented by an [[scala.Either]]
    */
  def from(a: A): Either[String, B] =
    Either.cond(
      test(a),
      make(a),
      nonMatchMessage(a)
    )

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
    from(a).fold(msg => throw new IllegalArgumentException(msg), identity)

  implicit val catsInstances: Hash[B] with Order[B] with Show[B] = new Hash[B] with Order[B] with Show[B] {
    override def hash(x: B): Int = hashA.hash(x.value)

    override def compare(x: B, y: B): Int = orderA.compare(x.value, y.value)

    override def show(t: B): String = showA.show(t.value)

    override def eqv(x: B, y: B): Boolean = hashA.eqv(x.value, y.value)
  }
}

object Refined {
  trait Value[A] extends Any {
    def value: A
  }

  abstract class StringRegexRefinement[B <: Value[String]] extends Refined[String, B] {
    protected def regex: Pattern
    override protected def test(a: String): Boolean = regex.matcher(a).matches()
    override protected def nonMatchMessage(a: String): String = s"$a must match `$regex`"
  }
}
