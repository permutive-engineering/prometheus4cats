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
import cats.{Applicative, Contravariant, FlatMap, ~>}

import prometheus4cats.internal.Refined
import prometheus4cats.internal.Refined.Regex

sealed abstract class Counter[F[_], -A, B] extends Metric[A] with Metric.Labelled[B] {
  self =>

  protected def incWithExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit]

  protected def incWithExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit]

  def contramap[C](f: C => A): Counter[F, C, B] = new Counter[F, C, B] {
    override def incWithExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incWithExemplarImpl(labels, exemplar)

    override def incWithExemplarImpl(n: C, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incWithExemplarImpl(f(n), labels, exemplar)
  }

  def contramapLabels[C](f: C => B): Counter[F, A, C] = new Counter[F, A, C] {
    override def incWithExemplarImpl(labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incWithExemplarImpl(f(labels), exemplar)
    override def incWithExemplarImpl(n: A, labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incWithExemplarImpl(n, f(labels), exemplar)
  }

  final def mapK[G[_]](fk: F ~> G): Counter[G, A, B] =
    new Counter[G, A, B] {
      override def incWithExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): G[Unit] =
        fk(self.incWithExemplarImpl(labels, exemplar))

      override def incWithExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): G[Unit] =
        fk(self.incWithExemplarImpl(n, labels, exemplar))
    }
}

object Counter {

  implicit class CounterSyntax[F[_]: FlatMap, -A](counter: Counter[F, A, Unit]) {
    final def inc: F[Unit] = incWithExemplar(None)

    final def inc(n: A): F[Unit] = incWithExemplar(n, None)

    final def incWithExemplar(implicit exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incWithExemplar)

    final def incWithExemplar(n: A)(implicit exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incWithExemplar(n, _))

    final def incWithExemplar(exemplar: Option[Exemplar.Labels]): F[Unit] = counter.incWithExemplarImpl((), exemplar)
    final def incWithExemplar(n: A, exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incWithExemplarImpl(n, (), exemplar)
  }

  implicit class LabelledCounterSyntax[F[_]: FlatMap, -A, B](counter: Counter[F, A, B])(implicit ev: Unit =:!= B) {
    final def inc(labels: B): F[Unit] = incWithExemplar(labels, None)

    final def inc(n: A, labels: B): F[Unit] = incWithExemplar(n, labels, None)

    final def incWithExemplar(labels: B)(implicit exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incWithExemplar(labels, _))

    final def incWithExemplar(n: A, labels: B)(implicit exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incWithExemplar(n, labels, _))

    final def incWithExemplar(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incWithExemplarImpl(labels, exemplar)

    final def incWithExemplar(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incWithExemplarImpl(n, labels, exemplar)
  }

  /** Refined value class for a counter name that has been parsed from a string
    */
  final case class Name private (val value: String) extends AnyVal with Refined.Value[String]

  object Name
      extends Regex[Name]("^[a-zA-Z_:][a-zA-Z0-9_:]*_total$".r.pattern, new Name(_))
      with internal.CounterNameFromStringLiteral

  implicit def catsInstances[F[_], C]: Contravariant[Counter[F, *, C]] =
    new Contravariant[Counter[F, *, C]] {
      override def contramap[A, B](fa: Counter[F, A, C])(f: B => A): Counter[F, B, C] = fa.contramap(f)
    }

  implicit def labelsContravariant[F[_], C]: LabelsContravariant[Counter[F, C, *]] =
    new LabelsContravariant[Counter[F, C, *]] {
      override def contramapLabels[A, B](fa: Counter[F, C, A])(f: B => A): Counter[F, C, B] = fa.contramapLabels(f)
    }

  def make[F[_], A, B](default: A, _inc: (A, B, Option[Exemplar.Labels]) => F[Unit]): Counter[F, A, B] =
    new Counter[F, A, B] {
      override def incWithExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _inc(default, labels, exemplar)

      override def incWithExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _inc(n, labels, exemplar)
    }

  def make[F[_], A, B](_inc: (A, B, Option[Exemplar.Labels]) => F[Unit])(implicit
      A: Numeric[A]
  ): Counter[F, A, B] =
    make(A.one, _inc)

  def noop[F[_]: Applicative, A, B]: Counter[F, A, B] = new Counter[F, A, B] {
    override def incWithExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      Applicative[F].unit

    override def incWithExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      Applicative[F].unit
  }

}
