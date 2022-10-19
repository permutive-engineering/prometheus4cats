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

import cats.{Applicative, Contravariant, Eq, Hash, Order, Show, ~>}

sealed abstract class Info[F[_], -A] extends Metric[A] { self =>

  def info(labels: A): F[Unit]

  override def contramap[B](f: B => A): Info[F, B] = new Info[F, B] {
    override def info(labels: B): F[Unit] = self.info(f(labels))
  }

  final def mapK[G[_]](fk: F ~> G): Info[G, A] = new Info[G, A] {
    override def info(labels: A): G[Unit] = fk(self.info(labels))
  }
}

object Info {

  /** Refined value class for a info name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal {
    override def toString: String = s"""Info.Name("$value")"""
  }

  object Name extends InfoNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*_info$".r.pattern

    /** Parse a [[Name]] from the given string
      *
      * @param string
      *   value from which to parse a counter name
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

    implicit val catsInstances: Hash[Name] with Order[Name] with Show[Name] = new Hash[Name]
      with Order[Name]
      with Show[Name] {
      override def hash(x: Name): Int = Hash[String].hash(x.value)

      override def compare(x: Name, y: Name): Int = Order[String].compare(x.value, y.value)

      override def show(t: Name): String = t.value

      override def eqv(x: Name, y: Name): Boolean = Eq[String].eqv(x.value, y.value)
    }
  }

  implicit def catsInstances[F[_]]: Contravariant[Info[F, *]] = new Contravariant[Info[F, *]] {
    override def contramap[A, B](fa: Info[F, A])(f: B => A): Info[F, B] = fa.contramap(f)
  }

  implicit class InfoMapSyntax[F[_]](info: Info[F, Map[Label.Name, String]]) {
    def info(labels: (Label.Name, String)*): F[Unit] = info.info(labels.toMap)
  }

  def make[F[_], A](_info: A => F[Unit]): Info[F, A] = new Info[F, A] {
    override def info(labels: A): F[Unit] = _info(labels)
  }

  def noop[F[_]: Applicative, A]: Info[F, A] = new Info[F, A] {
    override def info(labels: A): F[Unit] = Applicative[F].unit
  }
}
