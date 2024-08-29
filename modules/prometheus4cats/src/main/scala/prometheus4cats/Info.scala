/*
 * Copyright 2022-2024 Permutive Ltd. <https://permutive.com>
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

import cats.Applicative
import cats.Contravariant
import cats.~>

import prometheus4cats.internal.Refined
import prometheus4cats.internal.Refined.Regex

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

  /** Refined value class for a info name that has been parsed from a string */
  final class Name private (val value: String) extends AnyVal with Refined.Value[String]

  object Name
      extends Regex[Name]("^[a-zA-Z_:][a-zA-Z0-9_:]*_info$".r.pattern, new Name(_))
      with internal.InfoNameFromStringLiteral

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
