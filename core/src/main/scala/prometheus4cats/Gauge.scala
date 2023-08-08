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

abstract class Gauge[F[_], -A, B] extends Metric[A] with Metric.Labelled[B] {
  self =>

  def inc(labels: B): F[Unit]
  def inc(implicit ev: Unit =:= B): F[Unit] = inc(labels = ev(()))

  def inc(n: A, labels: B): F[Unit]
  def inc(n: A)(implicit ev: Unit =:= B): F[Unit] = inc(n = n, labels = ev(()))

  def dec(labels: B): F[Unit]
  def dec(implicit ev: Unit =:= B): F[Unit] = dec(labels = ev(()))

  def dec(n: A, labels: B): F[Unit]
  def dec(n: A)(implicit ev: Unit =:= B): F[Unit] = dec(n = n, labels = ev(()))

  def set(n: A, labels: B): F[Unit]
  def set(n: A)(implicit ev: Unit =:= B): F[Unit] = set(n = n, labels = ev(()))

  def reset(labels: B): F[Unit]
  def reset(implicit ev: Unit =:= B): F[Unit] = reset(labels = ev(()))

  def contramap[C](f: C => A): Gauge[F, C, B] = new Gauge[F, C, B] {
    override def inc(labels: B): F[Unit] = self.inc(labels)

    override def inc(n: C, labels: B): F[Unit] = self.inc(f(n), labels)

    override def dec(labels: B): F[Unit] = self.dec(labels)

    override def dec(n: C, labels: B): F[Unit] = self.dec(f(n), labels)

    override def set(n: C, labels: B): F[Unit] = self.set(f(n), labels)

    override def reset(labels: B): F[Unit] = self.reset(labels)
  }

  def contramapLabels[C](f: C => B): Gauge[F, A, C] = new Gauge[F, A, C] {
    override def inc(labels: C): F[Unit] = self.inc(f(labels))

    override def inc(n: A, labels: C): F[Unit] = self.inc(n, f(labels))

    override def dec(labels: C): F[Unit] = self.dec(f(labels))

    override def dec(n: A, labels: C): F[Unit] = self.dec(n, f(labels))

    override def set(n: A, labels: C): F[Unit] = self.set(n, f(labels))

    override def reset(labels: C): F[Unit] = self.reset(f(labels))
  }

  final def mapK[G[_]](fk: F ~> G): Gauge[G, A, B] =
    new Gauge[G, A, B] {

      override def inc(labels: B): G[Unit] = fk(self.inc(labels))

      override def inc(n: A, labels: B): G[Unit] = fk(
        self.inc(n, labels)
      )

      override def dec(labels: B): G[Unit] = fk(self.dec(labels))

      override def dec(n: A, labels: B): G[Unit] = fk(
        self.dec(n, labels)
      )

      override def set(n: A, labels: B): G[Unit] = fk(
        self.set(n, labels)
      )

      override def reset(labels: B): G[Unit] = fk(self.reset(labels))
    }

}

object Gauge {

  /** Refined value class for a gauge name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal with internal.Refined.Value[String] {
    override def toString: String = s"""Gauge.Name("$value")"""
  }

  object Name extends internal.Refined.StringRegexRefinement[Name] with internal.GaugeNameFromStringLiteral {
    override protected val regex: Pattern = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern
    override protected def make(a: String): Name = new Name(a)
  }

  implicit def catsInstances[F[_], C]: Contravariant[Gauge[F, *, C]] =
    new Contravariant[Gauge[F, *, C]] {
      override def contramap[A, B](fa: Gauge[F, A, C])(f: B => A): Gauge[F, B, C] = fa.contramap(f)
    }

  implicit def labelsContravariant[F[_], C]: LabelsContravariant[Gauge[F, C, *]] =
    new LabelsContravariant[Gauge[F, C, *]] {
      override def contramapLabels[A, B](fa: Gauge[F, C, A])(f: B => A): Gauge[F, C, B] = fa.contramapLabels(f)
    }

  def make[F[_], A, B](
      default: A,
      _inc: (A, B) => F[Unit],
      _dec: (A, B) => F[Unit],
      _set: (A, B) => F[Unit],
      _reset: B => F[Unit]
  ): Gauge[F, A, B] = new Gauge[F, A, B] {
    override def inc(labels: B): F[Unit] = inc(default, labels)

    override def inc(n: A, labels: B): F[Unit] = _inc(n, labels)

    override def dec(labels: B): F[Unit] = dec(default, labels)

    override def dec(n: A, labels: B): F[Unit] = _dec(n, labels)

    override def set(n: A, labels: B): F[Unit] = _set(n, labels)

    override def reset(labels: B): F[Unit] = _reset(labels)
  }

  def make[F[_], A, B](
      _inc: (A, B) => F[Unit],
      _dec: (A, B) => F[Unit],
      _set: (A, B) => F[Unit]
  )(implicit A: Numeric[A]): Gauge[F, A, B] =
    make(A.one, _inc, _dec, _set, _set(A.zero, _))

  def noop[F[_]: Applicative, A, B]: Gauge[F, A, B] = new Gauge[F, A, B] {
    override def inc(labels: B): F[Unit] = Applicative[F].unit

    override def inc(n: A, labels: B): F[Unit] = Applicative[F].unit

    override def dec(labels: B): F[Unit] = Applicative[F].unit

    override def dec(n: A, labels: B): F[Unit] = Applicative[F].unit

    override def set(n: A, labels: B): F[Unit] = Applicative[F].unit

    override def reset(labels: B): F[Unit] = Applicative[F].unit
  }

}
