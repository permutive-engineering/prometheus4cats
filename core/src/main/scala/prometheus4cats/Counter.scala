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

import cats.effect.kernel.{Clock, Ref}
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.{Applicative, Contravariant, FlatMap, Monad, ~>}
import prometheus4cats.Counter.ExemplarState
import prometheus4cats.internal.Refined.Regex
import prometheus4cats.internal.{Neq, Refined}

sealed abstract class Counter[F[_], A, B](
    protected[prometheus4cats] val exemplarState: ExemplarState[F]
) extends Metric[A]
    with Metric.Labelled[B] {
  self =>

  protected def incProvidedExemplarImpl(
      labels: B,
      exemplar: Option[Exemplar.Labels]
  ): F[Unit]

  protected def incProvidedExemplarImpl(
      n: A,
      labels: B,
      exemplar: Option[Exemplar.Labels]
  ): F[Unit]

  def contramap[C](f: C => A): Counter[F, C, B] = new Counter[F, C, B](exemplarState) {
    override def incProvidedExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incProvidedExemplarImpl(labels, exemplar)

    override def incProvidedExemplarImpl(n: C, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incProvidedExemplarImpl(f(n), labels, exemplar)
  }

  def contramapLabels[C](f: C => B): Counter[F, A, C] = new Counter[F, A, C](exemplarState) {
    override def incProvidedExemplarImpl(labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incProvidedExemplarImpl(f(labels), exemplar)
    override def incProvidedExemplarImpl(n: A, labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
      self.incProvidedExemplarImpl(n, f(labels), exemplar)
  }

  final def mapK[G[_]](fk: F ~> G): Counter[G, A, B] =
    new Counter[G, A, B](exemplarState.mapK(fk)) {
      override def incProvidedExemplarImpl(
          labels: B,
          exemplar: Option[Exemplar.Labels]
      ): G[Unit] =
        fk(self.incProvidedExemplarImpl(labels, exemplar))

      override def incProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): G[Unit] =
        fk(self.incProvidedExemplarImpl(n, labels, exemplar))
    }
}

object Counter {
  sealed trait ExemplarState[F[_]] { self =>
    def surround[A](
        fa: Option[Exemplar.Labels] => F[Unit]
    )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit]

    def surround[A](n: A)(
        fa: Option[Exemplar.Labels] => F[Unit]
    )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit]

    def mapK[G[_]](fk: F ~> G): ExemplarState[G]
  }

  object ExemplarState {
    def getSet[F[_]](get: F[Option[Exemplar.Data]], set: Option[Exemplar.Data] => F[Unit]): ExemplarState[F] =
      new ExemplarState[F] {
        override def surround[A](
            fa: Option[Exemplar.Labels] => F[Unit]
        )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit] =
          for {
            previous <- get
            next <- exemplarSampler.sample(previous)
            _ <- fa(next)
            _ <- next.traverse_(labels =>
              Clock[F].realTimeInstant.flatMap(time => set(Some(Exemplar.Data(labels, time))))
            )
          } yield ()

        override def surround[A](n: A)(
            fa: Option[Exemplar.Labels] => F[Unit]
        )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit] =
          for {
            previous <- get
            next <- exemplarSampler.sample(n, previous)
            _ <- fa(next)
            _ <- next.traverse_(labels =>
              Clock[F].realTimeInstant.flatMap(time => set(Some(Exemplar.Data(labels, time))))
            )
          } yield ()

        def mapK[G[_]](fk: F ~> G): ExemplarState[G] = getSet(fk(get), ex => fk(set(ex)))
      }

    def fromRef[F[_]](ref: Ref[F, Option[Exemplar.Data]]): ExemplarState[F] = getSet(ref.get, ref.set)

    def noop[F[_]]: ExemplarState[F] = new ExemplarState[F] { self =>
      override def surround[A](n: A)(
          fa: Option[Exemplar.Labels] => F[Unit]
      )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit] =
        Applicative[F].unit

      override def surround[A](
          fa: Option[Exemplar.Labels] => F[Unit]
      )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit] =
        Applicative[F].unit

      override def mapK[G[_]](fk: F ~> G): ExemplarState[G] = noop[G]

    }
  }

  implicit class CounterSyntax[F[_], A](counter: Counter[F, A, Unit]) {
    final def inc: F[Unit] = incProvidedExemplar(None)

    final def inc(n: A): F[Unit] = incProvidedExemplar(n, None)

    final def incWithSampledExemplar(implicit
        F: Monad[F],
        clock: Clock[F],
        exemplarSampler: ExemplarSampler.Counter[F, A]
    ): F[Unit] = counter.exemplarState.surround(incProvidedExemplar(_))

    final def incWithSampledExemplar(
        n: A
    )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit] =
      counter.exemplarState.surround(n)(incProvidedExemplar(n, _))

    final def incWithExemplar(implicit F: FlatMap[F], exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incProvidedExemplar)

    final def incWithExemplar(n: A)(implicit F: FlatMap[F], exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(incProvidedExemplar(n, _))

    final def incProvidedExemplar(exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incProvidedExemplarImpl((), exemplar)
    final def incProvidedExemplar(n: A, exemplar: Option[Exemplar.Labels]): F[Unit] =
      counter.incProvidedExemplarImpl(n, (), exemplar)
  }

  implicit class LabelledCounterSyntax[F[_], A, B](counter: Counter[F, A, B])(implicit
      ev: Unit Neq B
  ) {
    final def inc(labels: B): F[Unit] = incProvidedExemplar(labels, None)

    final def inc(n: A, labels: B): F[Unit] = incProvidedExemplar(n, labels, None)

    final def incWithSampledExemplar(labels: B)(implicit
        F: Monad[F],
        clock: Clock[F],
        exemplarSampler: ExemplarSampler.Counter[F, A]
    ): F[Unit] =
      counter.exemplarState.surround(incProvidedExemplar(labels, _))

    final def incWithSampledExemplar(
        n: A,
        labels: B
    )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit] =
      counter.exemplarState.surround(n)(incProvidedExemplar(n, labels, _))

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
      exemplarState: ExemplarState[F],
      default: A,
      _inc: (A, B, Option[Exemplar.Labels]) => F[Unit]
  ): Counter[F, A, B] =
    new Counter[F, A, B](exemplarState) {
      override def incProvidedExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _inc(default, labels, exemplar)

      override def incProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _inc(n, labels, exemplar)
    }

  def make[F[_], A, B](
      exemplarState: ExemplarState[F],
      _inc: (A, B, Option[Exemplar.Labels]) => F[Unit]
  )(implicit
      A: Numeric[A]
  ): Counter[F, A, B] =
    make(exemplarState, A.one, _inc)

  def noop[F[_]: Applicative, A, B]: Counter[F, A, B] =
    new Counter[F, A, B](ExemplarState.noop[F]) {
      override def incProvidedExemplarImpl(labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        Applicative[F].unit

      override def incProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        Applicative[F].unit
    }

}
