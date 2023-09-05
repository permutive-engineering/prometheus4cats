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
import cats.syntax.functor._
import cats.{Applicative, Contravariant, FlatMap, Functor, ~>}
import prometheus4cats.internal.{Neq, Refined}
import prometheus4cats.internal.Refined.Regex

sealed abstract class Counter[F[_], -A, B] extends Metric[A] with Metric.Labelled[B] {
  self =>

  protected def incProvidedExemplarImpl(
      labels: B,
      exemplar: Option[Exemplar.Labels]
  ): F[Option[Exemplar.Data] => F[Unit]]

  protected def incProvidedExemplarImpl(
      n: A,
      labels: B,
      exemplar: Either[Option[Exemplar.Data] => F[Option[Exemplar.Labels]], Option[Exemplar.Labels]]
  ): F[Unit]

  def contramap[C](f: C => A): Counter[F, C, B] = new Counter[F, C, B] {
    override def incProvidedExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incProvidedExemplarImpl(labels, exemplar)

    override def incProvidedExemplarImpl(n: C, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incProvidedExemplarImpl(f(n), labels, exemplar)
  }

  def contramapLabels[C](f: C => B): Counter[F, A, C] = new Counter[F, A, C] {
    override def incProvidedExemplarImpl(labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incProvidedExemplarImpl(f(labels), exemplar)
    override def incProvidedExemplarImpl(n: A, labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incProvidedExemplarImpl(n, f(labels), exemplar)
  }

  final def mapK[G[_]: Functor](fk: F ~> G): Counter[G, A, B] =
    new Counter[G, A, B] {
      override def incProvidedExemplarImpl(
          labels: B,
          exemplar: Option[Exemplar.Labels]
      ): G[Option[Exemplar.Data] => G[Unit]] =
        fk(self.incProvidedExemplarImpl(labels, exemplar)).map(x => x.andThen(fk.apply))

      override def incProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): G[Unit] =
        fk(self.incProvidedExemplarImpl(n, labels, exemplar))
    }
}

object Counter {

  implicit class CounterSyntax[F[_], -A](counter: Counter[F, A, Unit])(implicit
      exemplarSampler: CounterExemplarSampler[F, A]
  ) {
    final def inc: F[Unit] =
      counter.incProvidedExemplarImpl((), Left((data: Option[Exemplar.Data]) => exemplarSampler.sample(data)))

    final def inc(n: A): F[Unit] =
      counter.incProvidedExemplarImpl((), Left((data: Option[Exemplar.Data]) => exemplarSampler.sample(n, data)))

    final def incWithExemplar(implicit F: FlatMap[F], exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incProvidedExemplar)

    final def incWithExemplar(n: A)(implicit F: FlatMap[F], exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incProvidedExemplar(n, _))

    final def incProvidedExemplar(exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incProvidedExemplarImpl((), Right(exemplar))
    final def incProvidedExemplar(n: A, exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incProvidedExemplarImpl(n, (), Right(exemplar))
  }

  implicit class LabelledCounterSyntax[F[_], -A, B](counter: Counter[F, A, B])(implicit
      ev: Unit Neq B,
      exemplarSampler: CounterExemplarSampler[F, A]
  ) {
    final def inc(labels: B)(implicit F: FlatMap[F]): F[Unit] =
      exemplarSampler.sample.flatMap(incProvidedExemplar(labels, _))

    final def inc(n: A, labels: B)(implicit F: FlatMap[F]): F[Unit] =
      exemplarSampler.sample(n).flatMap(incProvidedExemplar(n, labels, _))

    final def incWithExemplar(labels: B)(implicit F: FlatMap[F], exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incProvidedExemplar(labels, _))

    final def incWithExemplar(n: A, labels: B)(implicit F: FlatMap[F], exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incProvidedExemplar(n, labels, _))

    final def incProvidedExemplar(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incProvidedExemplarImpl(labels, exemplar)

    final def incProvidedExemplar(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incProvidedExemplarImpl(n, labels, exemplar)
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

  def make[F[_], A, B](
      default: A,
      _inc: (A, B, Either[Option[Exemplar.Data] => F[Option[Exemplar.Labels]], Option[Exemplar.Labels]]) => F[Unit]
  ): Counter[F, A, B] =
    new Counter[F, A, B] {
      override def incProvidedExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _inc(default, labels, exemplar)

      override def incProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _inc(n, labels, exemplar)
    }

  def make[F[_], A, B](
      _inc: (A, B, Either[Option[Exemplar.Data] => F[Option[Exemplar.Labels]], Option[Exemplar.Labels]]) => F[Unit]
  )(implicit
      A: Numeric[A]
  ): Counter[F, A, B] =
    make(A.one, _inc)

  def noop[F[_]: Applicative, A, B]: Counter[F, A, B] = new Counter[F, A, B] {
    override def incProvidedExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      Applicative[F].unit

    override def incProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      Applicative[F].unit
  }

}
