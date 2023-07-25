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

import cats.FlatMap

import java.util.regex.Pattern
import cats.{Applicative, Contravariant, ~>}
import cats.syntax.flatMap._

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

  sealed abstract class Exemplar[F[_], -A] { self =>
    def inc: F[Unit]
    def incWithExemplar: F[Unit]
    def inc(n: A): F[Unit]
    def incWithExemplar(n: A): F[Unit]

    def contramap[B](f: B => A): Exemplar[F, B] = new Exemplar[F, B] {
      override def inc: F[Unit] = self.inc
      override def incWithExemplar: F[Unit] = self.incWithExemplar
      override def inc(n: B): F[Unit] = self.inc(f(n))
      override def incWithExemplar(n: B): F[Unit] = self.incWithExemplar(f(n))
    }

    final def mapK[G[_]](fk: F ~> G): Exemplar[G, A] = new Exemplar[G, A] {
      override def inc: G[Unit] = fk(self.inc)
      override def incWithExemplar: G[Unit] = fk(self.incWithExemplar)
      override def inc(n: A): G[Unit] = fk(self.inc(n))
      override def incWithExemplar(n: A): G[Unit] = fk(self.incWithExemplar(n))
    }
  }

  object Exemplar {
    def make[F[_]: FlatMap: prometheus4cats.Exemplar, A](
        default: A,
        _inc: (A, Option[prometheus4cats.Exemplar.Labels]) => F[Unit]
    ): Exemplar[F, A] = new Exemplar[F, A] {
      override def inc: F[Unit] = inc(default)
      override def inc(n: A): F[Unit] = _inc(n, None)
      override def incWithExemplar: F[Unit] = incWithExemplar(default)
      override def incWithExemplar(n: A): F[Unit] = prometheus4cats.Exemplar[F].get.flatMap(_inc(n, _))
    }

    def make[F[_]: FlatMap: prometheus4cats.Exemplar, A](_inc: (A, Option[prometheus4cats.Exemplar.Labels]) => F[Unit])(
        implicit A: Numeric[A]
    ): Exemplar[F, A] =
      make(A.one, _inc)

    private[prometheus4cats] def fromCounter[F[_], A](counter: Counter[F, A]) = new Exemplar[F, A] {
      override def inc: F[Unit] = counter.inc

      override def inc(n: A): F[Unit] = counter.inc(n)

      override def incWithExemplar: F[Unit] = inc

      override def incWithExemplar(n: A): F[Unit] = inc(n)
    }

    def noop[F[_]: Applicative, A]: Exemplar[F, A] = new Exemplar[F, A] {
      override def inc: F[Unit] = Applicative[F].unit
      override def inc(n: A): F[Unit] = Applicative[F].unit
      override def incWithExemplar: F[Unit] = Applicative[F].unit
      override def incWithExemplar(n: A): F[Unit] = Applicative[F].unit
    }
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
    sealed abstract class Exemplar[F[_], -A, -B] extends Metric[A] with Metric.Labelled[B] {
      self =>
      def inc(labels: B): F[Unit]

      def incWithExemplar(labels: B): F[Unit]

      def inc(n: A, labels: B): F[Unit]

      def incWithExemplar(n: A, labels: B): F[Unit]

      def contramap[C](f: C => A): Exemplar[F, C, B] = new Exemplar[F, C, B] {
        override def inc(labels: B): F[Unit] = self.inc(labels)

        override def inc(n: C, labels: B): F[Unit] = self.inc(f(n), labels)

        override def incWithExemplar(labels: B): F[Unit] = self.incWithExemplar(labels)

        override def incWithExemplar(n: C, labels: B): F[Unit] = self.incWithExemplar(f(n), labels)
      }

      def contramapLabels[C](f: C => B): Exemplar[F, A, C] = new Exemplar[F, A, C] {
        override def inc(labels: C): F[Unit] = self.inc(f(labels))

        override def inc(n: A, labels: C): F[Unit] = self.inc(n, f(labels))

        override def incWithExemplar(labels: C): F[Unit] = self.incWithExemplar(f(labels))

        override def incWithExemplar(n: A, labels: C): F[Unit] = self.incWithExemplar(n, f(labels))
      }

      final def mapK[G[_]](fk: F ~> G): Counter.Labelled.Exemplar[G, A, B] =
        new Exemplar[G, A, B] {
          override def inc(labels: B): G[Unit] = fk(self.inc(labels))

          override def inc(n: A, labels: B): G[Unit] = fk(
            self.inc(n, labels)
          )

          override def incWithExemplar(labels: B): G[Unit] = fk(self.incWithExemplar(labels))

          override def incWithExemplar(n: A, labels: B): G[Unit] = fk(
            self.incWithExemplar(n, labels)
          )
        }
    }

    object Exemplar {
      implicit def catsInstances[F[_], C]: Contravariant[Exemplar[F, *, C]] =
        new Contravariant[Exemplar[F, *, C]] {
          override def contramap[A, B](fa: Exemplar[F, A, C])(f: B => A): Exemplar[F, B, C] = fa.contramap(f)
        }

      implicit def labelsContravariant[F[_], C]: LabelsContravariant[Exemplar[F, C, *]] =
        new LabelsContravariant[Exemplar[F, C, *]] {
          override def contramapLabels[A, B](fa: Exemplar[F, C, A])(f: B => A): Exemplar[F, C, B] =
            fa.contramapLabels(f)
        }

      def make[F[_]: FlatMap: prometheus4cats.Exemplar, A, B](
          default: A,
          _inc: (A, B, Option[prometheus4cats.Exemplar.Labels]) => F[Unit]
      ): Exemplar[F, A, B] =
        new Exemplar[F, A, B] {
          override def inc(labels: B): F[Unit] = inc(default, labels)

          override def inc(n: A, labels: B): F[Unit] = _inc(n, labels, None)

          override def incWithExemplar(labels: B): F[Unit] = incWithExemplar(default, labels)

          override def incWithExemplar(n: A, labels: B): F[Unit] =
            prometheus4cats.Exemplar[F].get.flatMap(_inc(n, labels, _))
        }

      def make[F[_]: FlatMap: prometheus4cats.Exemplar, A, B](
          _inc: (A, B, Option[prometheus4cats.Exemplar.Labels]) => F[Unit]
      )(implicit
          A: Numeric[A]
      ): Exemplar[F, A, B] = make(A.one, _inc)

      private[prometheus4cats] def fromCounter[F[_], A, B](counter: Counter.Labelled[F, A, B]): Exemplar[F, A, B] =
        new Exemplar[F, A, B] {
          override def inc(labels: B): F[Unit] = counter.inc(labels)

          override def inc(n: A, labels: B): F[Unit] = counter.inc(n, labels)

          override def incWithExemplar(labels: B): F[Unit] = counter.inc(labels)

          override def incWithExemplar(n: A, labels: B): F[Unit] = counter.inc(n, labels)
        }

      def noop[F[_]: Applicative, A, B]: Exemplar[F, A, B] = new Exemplar[F, A, B] {
        override def inc(labels: B): F[Unit] = Applicative[F].unit

        override def inc(n: A, labels: B): F[Unit] = Applicative[F].unit

        override def incWithExemplar(labels: B): F[Unit] = Applicative[F].unit

        override def incWithExemplar(n: A, labels: B): F[Unit] = Applicative[F].unit
      }
    }

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
