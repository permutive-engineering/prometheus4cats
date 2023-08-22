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

import cats.{Applicative, Contravariant, ~>}
import prometheus4cats.internal.{Neq, Refined}
import prometheus4cats.internal.Refined.Regex

abstract class Gauge[F[_], -A, B] extends Metric[A] with Metric.Labelled[B] {
  self =>

  protected def incImpl(labels: B): F[Unit]

  protected def incImpl(n: A, labels: B): F[Unit]

  protected def decImpl(labels: B): F[Unit]

  protected def decImpl(n: A, labels: B): F[Unit]

  protected def setImpl(n: A, labels: B): F[Unit]

  protected def resetImpl(labels: B): F[Unit]

  def contramap[C](f: C => A): Gauge[F, C, B] = new Gauge[F, C, B] {
    override def incImpl(labels: B): F[Unit] = self.incImpl(labels)

    override def incImpl(n: C, labels: B): F[Unit] = self.incImpl(f(n), labels)

    override def decImpl(labels: B): F[Unit] = self.decImpl(labels)

    override def decImpl(n: C, labels: B): F[Unit] = self.decImpl(f(n), labels)

    override def setImpl(n: C, labels: B): F[Unit] = self.setImpl(f(n), labels)

    override def resetImpl(labels: B): F[Unit] = self.resetImpl(labels)
  }

  def contramapLabels[C](f: C => B): Gauge[F, A, C] = new Gauge[F, A, C] {
    override def incImpl(labels: C): F[Unit] = self.incImpl(f(labels))

    override def incImpl(n: A, labels: C): F[Unit] = self.incImpl(n, f(labels))

    override def decImpl(labels: C): F[Unit] = self.decImpl(f(labels))

    override def decImpl(n: A, labels: C): F[Unit] = self.decImpl(n, f(labels))

    override def setImpl(n: A, labels: C): F[Unit] = self.setImpl(n, f(labels))

    override def resetImpl(labels: C): F[Unit] = self.resetImpl(f(labels))
  }

  final def mapK[G[_]](fk: F ~> G): Gauge[G, A, B] =
    new Gauge[G, A, B] {

      override def incImpl(labels: B): G[Unit] = fk(self.incImpl(labels))

      override def incImpl(n: A, labels: B): G[Unit] = fk(
        self.incImpl(n, labels)
      )

      override def decImpl(labels: B): G[Unit] = fk(self.decImpl(labels))

      override def decImpl(n: A, labels: B): G[Unit] = fk(
        self.decImpl(n, labels)
      )

      override def setImpl(n: A, labels: B): G[Unit] = fk(
        self.setImpl(n, labels)
      )

      override def resetImpl(labels: B): G[Unit] = fk(self.resetImpl(labels))
    }

}

object Gauge {

  implicit class GaugeSyntax[F[_], -A](gauge: Gauge[F, A, Unit]) {
    def inc: F[Unit] = gauge.incImpl(())

    def inc(n: A): F[Unit] = gauge.incImpl(n, ())

    def dec: F[Unit] = gauge.decImpl(())

    def dec(n: A): F[Unit] = gauge.decImpl(n, ())

    def set(n: A): F[Unit] = gauge.setImpl(n, ())

    def reset: F[Unit] = gauge.resetImpl(())
  }

  implicit class LabelledGaugeSyntax[F[_], -A, B](gauge: Gauge[F, A, B])(implicit ev: Unit Neq B) {
    def inc(labels: B): F[Unit] = gauge.incImpl(labels)

    def inc(n: A, labels: B): F[Unit] = gauge.incImpl(n, labels)

    def dec(labels: B): F[Unit] = gauge.decImpl(labels)

    def dec(n: A, labels: B): F[Unit] = gauge.decImpl(n, labels)

    def set(n: A, labels: B): F[Unit] = gauge.setImpl(n, labels)

    def reset(labels: B): F[Unit] = gauge.resetImpl(labels)
  }

  /** Refined value class for a gauge name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal with Refined.Value[String]

  object Name
      extends Regex[Name]("^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern, new Name(_))
      with internal.GaugeNameFromStringLiteral

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
    override def incImpl(labels: B): F[Unit] = incImpl(default, labels)

    override def incImpl(n: A, labels: B): F[Unit] = _inc(n, labels)

    override def decImpl(labels: B): F[Unit] = decImpl(default, labels)

    override def decImpl(n: A, labels: B): F[Unit] = _dec(n, labels)

    override def setImpl(n: A, labels: B): F[Unit] = _set(n, labels)

    override def resetImpl(labels: B): F[Unit] = _reset(labels)
  }

  def make[F[_], A, B](
      _incImpl: (A, B) => F[Unit],
      _dec: (A, B) => F[Unit],
      _set: (A, B) => F[Unit]
  )(implicit A: Numeric[A]): Gauge[F, A, B] =
    make(A.one, _incImpl, _dec, _set, _set(A.zero, _))

  def noop[F[_]: Applicative, A, B]: Gauge[F, A, B] = new Gauge[F, A, B] {
    override def incImpl(labels: B): F[Unit] = Applicative[F].unit

    override def incImpl(n: A, labels: B): F[Unit] = Applicative[F].unit

    override def decImpl(labels: B): F[Unit] = Applicative[F].unit

    override def decImpl(n: A, labels: B): F[Unit] = Applicative[F].unit

    override def setImpl(n: A, labels: B): F[Unit] = Applicative[F].unit

    override def resetImpl(labels: B): F[Unit] = Applicative[F].unit
  }

}
