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

import cats.syntax.flatMap._
import cats.{Applicative, Contravariant, FlatMap, Monad, ~>}

import java.util.regex.Pattern

sealed abstract class Counter[F[_]: FlatMap, -A, B] extends Metric[A] with Metric.Labelled[B] {
  self =>

  final def inc(labels: B): F[Unit] = incWithExemplar(labels, None)
  final def inc(implicit ev: Unit =:= B): F[Unit] = inc(labels = ev(()))

  final def inc(n: A, labels: B): F[Unit] = incWithExemplar(n, labels, None)
  final def inc(n: A)(implicit ev: Unit =:= B): F[Unit] = inc(n = n, labels = ev(()))

  final def incWithExemplar(labels: B)(implicit exemplar: Exemplar[F]): F[Unit] =
    exemplar.get.flatMap(incWithExemplar(labels, _))
  final def incWithExemplar(implicit ev: Unit =:= B, exemplar: Exemplar[F]): F[Unit] = incWithExemplar(labels = ev(()))

  final def incWithExemplar(n: A, labels: B)(implicit exemplar: Exemplar[F]): F[Unit] =
    exemplar.get.flatMap(incWithExemplar(n, labels, _))
  final def incWithExemplar(n: A)(implicit ev: Unit =:= B, exemplar: Exemplar[F]): F[Unit] =
    incWithExemplar(n = n, labels = ev(()))

  def incWithExemplar(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit]
  def incWithExemplar(exemplar: Option[Exemplar.Labels])(implicit ev: Unit =:= B): F[Unit] =
    incWithExemplar(labels = ev(()), exemplar = exemplar)

  def incWithExemplar(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit]
  def incWithExemplar(n: A, exemplar: Option[Exemplar.Labels])(implicit ev: Unit =:= B): F[Unit] =
    incWithExemplar(n = n, labels = ev(()), exemplar = exemplar)

  def contramap[C](f: C => A): Counter[F, C, B] = new Counter[F, C, B] {
    override def incWithExemplar(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incWithExemplar(labels, exemplar)

    override def incWithExemplar(n: C, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incWithExemplar(f(n), labels, exemplar)
  }

  def contramapLabels[C](f: C => B): Counter[F, A, C] = new Counter[F, A, C] {
    override def incWithExemplar(labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incWithExemplar(f(labels), exemplar)
    override def incWithExemplar(n: A, labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incWithExemplar(n, f(labels), exemplar)
  }

  final def mapK[G[_]: FlatMap](fk: F ~> G): Counter[G, A, B] =
    new Counter[G, A, B] {
      override def incWithExemplar(labels: B, exemplar: Option[Exemplar.Labels]): G[Unit] =
        fk(self.incWithExemplar(labels, exemplar))

      override def incWithExemplar(n: A, labels: B, exemplar: Option[Exemplar.Labels]): G[Unit] =
        fk(self.incWithExemplar(n, labels, exemplar))
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

  implicit def catsInstances[F[_], C]: Contravariant[Counter[F, *, C]] =
    new Contravariant[Counter[F, *, C]] {
      override def contramap[A, B](fa: Counter[F, A, C])(f: B => A): Counter[F, B, C] = fa.contramap(f)
    }

  implicit def labelsContravariant[F[_], C]: LabelsContravariant[Counter[F, C, *]] =
    new LabelsContravariant[Counter[F, C, *]] {
      override def contramapLabels[A, B](fa: Counter[F, C, A])(f: B => A): Counter[F, C, B] = fa.contramapLabels(f)
    }

  def make[F[_]: FlatMap, A, B](default: A, _inc: (A, B, Option[Exemplar.Labels]) => F[Unit]): Counter[F, A, B] =
    new Counter[F, A, B] {
      override def incWithExemplar(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _inc(default, labels, exemplar)

      override def incWithExemplar(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _inc(n, labels, exemplar)
    }

  def make[F[_]: FlatMap, A, B](_inc: (A, B, Option[Exemplar.Labels]) => F[Unit])(implicit
      A: Numeric[A]
  ): Counter[F, A, B] =
    make(A.one, _inc)

  def noop[F[_]: Monad, A, B]: Counter[F, A, B] = new Counter[F, A, B] {
    override def incWithExemplar(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      Applicative[F].unit

    override def incWithExemplar(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      Applicative[F].unit
  }

}
