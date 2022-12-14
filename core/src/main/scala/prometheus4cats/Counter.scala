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

import cats.{Applicative, Contravariant, ~>}

sealed abstract class Counter[F[_], -A] extends Metric[A] { self =>

  def inc: F[Unit]
  def inc(n: A): F[Unit]

  def contramap[B](f: B => A): Counter[F, B] = new Counter[F, B] {
    override def inc: F[Unit] = self.inc

    override def inc(n: B): F[Unit] = self.inc(f(n))
  }

  final def mapK[G[_]](fk: F ~> G): Counter[G, A] = new Counter[G, A] {
    override def inc: G[Unit] = fk(self.inc)
    override def inc(n: A): G[Unit] = fk(self.inc(n))
  }
}

object Counter {

  /** Refined value class for a counter name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal with internal.Refined.Value[String] {
    override def toString: String = s"""Counter.Name("$value")"""
  }

  object Name extends internal.Refined.StringRegexRefinement[Name] with internal.CounterNameFromStringLiteral {
    override protected val regex: Pattern = "^[a-zA-Z_:][a-zA-Z0-9_:]*_total$".r.pattern
    override protected def make(a: String): Name = new Name(a)
  }

  implicit def catsInstances[F[_]]: Contravariant[Counter[F, *]] = new Contravariant[Counter[F, *]] {
    override def contramap[A, B](fa: Counter[F, A])(f: B => A): Counter[F, B] = fa.contramap(f)
  }

  def make[F[_], A](default: A, _inc: A => F[Unit]): Counter[F, A] = new Counter[F, A] {
    override def inc: F[Unit] = inc(default)

    override def inc(n: A): F[Unit] = _inc(n)
  }

  def make[F[_], A](_inc: A => F[Unit])(implicit A: Numeric[A]): Counter[F, A] = make(A.one, _inc)

  def noop[F[_]: Applicative, A]: Counter[F, A] = new Counter[F, A] {
    override def inc: F[Unit] = Applicative[F].unit

    override def inc(n: A): F[Unit] = Applicative[F].unit
  }

  sealed abstract class Labelled[F[_], -A, -B] extends Metric[A] with Metric.Labelled[B] {
    self =>
    def inc(labels: B): F[Unit]

    def inc(n: A, labels: B): F[Unit]

    def contramap[C](f: C => A): Labelled[F, C, B] = new Labelled[F, C, B] {
      override def inc(labels: B): F[Unit] = self.inc(labels)

      override def inc(n: C, labels: B): F[Unit] = self.inc(f(n), labels)
    }

    def contramapLabels[C](f: C => B): Labelled[F, A, C] = new Labelled[F, A, C] {
      override def inc(labels: C): F[Unit] = self.inc(f(labels))

      override def inc(n: A, labels: C): F[Unit] = self.inc(n, f(labels))
    }

    final def mapK[G[_]](fk: F ~> G): Counter.Labelled[G, A, B] =
      new Labelled[G, A, B] {
        override def inc(labels: B): G[Unit] = fk(self.inc(labels))

        override def inc(n: A, labels: B): G[Unit] = fk(
          self.inc(n, labels)
        )
      }
  }

  object Labelled {
    implicit def catsInstances[F[_], C]: Contravariant[Labelled[F, *, C]] =
      new Contravariant[Labelled[F, *, C]] {
        override def contramap[A, B](fa: Labelled[F, A, C])(f: B => A): Labelled[F, B, C] = fa.contramap(f)
      }

    implicit def labelsContravariant[F[_], C]: LabelsContravariant[Labelled[F, C, *]] =
      new LabelsContravariant[Labelled[F, C, *]] {
        override def contramapLabels[A, B](fa: Labelled[F, C, A])(f: B => A): Labelled[F, C, B] = fa.contramapLabels(f)
      }

    def make[F[_], A, B](default: A, _inc: (A, B) => F[Unit]): Labelled[F, A, B] =
      new Labelled[F, A, B] {
        override def inc(labels: B): F[Unit] = inc(default, labels)

        override def inc(n: A, labels: B): F[Unit] = _inc(n, labels)
      }

    def make[F[_], A, B](_inc: (A, B) => F[Unit])(implicit A: Numeric[A]): Labelled[F, A, B] = make(A.one, _inc)

    def noop[F[_]: Applicative, A, B]: Labelled[F, A, B] = new Labelled[F, A, B] {
      override def inc(labels: B): F[Unit] = Applicative[F].unit

      override def inc(n: A, labels: B): F[Unit] = Applicative[F].unit
    }
  }

}
