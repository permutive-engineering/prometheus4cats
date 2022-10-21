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

sealed abstract class Summary[F[_], -A] extends Metric[A] { self =>
  def observe(n: A): F[Unit]

  override def contramap[B](f: B => A): Summary[F, B] = new Summary[F, B] {
    override def observe(n: B): F[Unit] = self.observe(f(n))
  }

  def mapK[G[_]](fk: F ~> G): Summary[G, A] = new Summary[G, A] {
    override def observe(n: A): G[Unit] = fk(self.observe(n))
  }
}

object Summary {
  final class AgeBuckets(val value: Int) extends AnyVal with internal.Refined.Value[Int] {
    override def toString: String = s"""Summary.AgeBuckets(value: "$value")"""
  }

  object AgeBuckets extends internal.Refined[Int, AgeBuckets] with internal.SummaryAgeBucketsFromIntLiteral {

    val Default: AgeBuckets = new AgeBuckets(5)

    override protected def make(a: Int): AgeBuckets = new AgeBuckets(a)

    override protected def test(a: Int): Boolean = a > 0

    override protected def nonMatchMessage(a: Int): String = s"AgeBuckets value $a must be greater than 0"
  }

  final class Quantile(val value: Double) extends AnyVal with internal.Refined.Value[Double] {
    override def toString: String = s"""Summary.Quantile(value: "$value")"""
  }

  object Quantile extends internal.Refined[Double, Quantile] with internal.SummaryQuantileFromDoubleLiteral {
    override protected def make(a: Double): Quantile = new Quantile(a)

    override protected def test(a: Double): Boolean = a >= 0.0 && a <= 1.0

    override protected def nonMatchMessage(a: Double): String = s"Quantile value $a must be between 0.0 and 1.0"
  }

  final class AllowedError(val value: Double) extends AnyVal with internal.Refined.Value[Double] {
    override def toString: String = s"""Summary.ErrorRate(value: "$value")"""
  }

  object AllowedError
      extends internal.Refined[Double, AllowedError]
      with internal.SummaryAllowedErrorFromDoubleLiteral {
    override protected def make(a: Double): AllowedError = new AllowedError(a)

    override protected def test(a: Double): Boolean = a >= 0.0 && a <= 1.0

    override protected def nonMatchMessage(a: Double): String = s"AllowedError value $a must be between 0.0 and 1.0"
  }

  final case class QuantileDefinition(value: Quantile, error: AllowedError) {
    override def toString: String = s"""Summary.QuantileDefinition(value: "${value.value}", error: "${error.value}")"""
  }

  case class Value[A](count: A, sum: A, quantiles: Map[Double, A] = Map.empty) {
    def map[B](f: A => B): Value[B] = Value(f(count), f(sum), quantiles.map { case (q, v) => q -> f(v) })
  }

  /** Refined value class for a gauge name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal with internal.Refined.Value[String] {
    override def toString: String = s"""Summary.Name("$value")"""
  }

  object Name extends internal.Refined.StringRegexRefinement[Name] with internal.SummaryNameFromStringLiteral {
    final override protected val regex: Pattern = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern
    final override protected def make(a: String): Name = new Name(a)
  }

  implicit def catsInstances[F[_]]: Contravariant[Summary[F, *]] = new Contravariant[Summary[F, *]] {
    override def contramap[A, B](fa: Summary[F, A])(f: B => A): Summary[F, B] = fa.contramap(f)
  }

  def make[F[_], A](_observe: A => F[Unit]): Summary[F, A] = new Summary[F, A] {
    override def observe(n: A): F[Unit] = _observe(n)
  }

  def noop[F[_]: Applicative, A]: Summary[F, A] = new Summary[F, A] {
    override def observe(n: A): F[Unit] = Applicative[F].unit
  }

  sealed abstract class Labelled[F[_], -A, -B] extends Metric[A] with Metric.Labelled[B] {
    self =>

    def observe(n: A, labels: B): F[Unit]

    def contramap[C](f: C => A): Labelled[F, C, B] = new Labelled[F, C, B] {
      override def observe(n: C, labels: B): F[Unit] = self.observe(f(n), labels)
    }

    def contramapLabels[C](f: C => B): Labelled[F, A, C] = new Labelled[F, A, C] {
      override def observe(n: A, labels: C): F[Unit] = self.observe(n, f(labels))
    }

    final def mapK[G[_]](fk: F ~> G): Labelled[G, A, B] =
      new Labelled[G, A, B] {
        override def observe(n: A, labels: B): G[Unit] = fk(self.observe(n, labels))
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

    def make[F[_], A, B](_observe: (A, B) => F[Unit]): Labelled[F, A, B] =
      new Labelled[F, A, B] {
        override def observe(n: A, labels: B): F[Unit] = _observe(n, labels)
      }

    def noop[F[_]: Applicative, A, B]: Labelled[F, A, B] =
      new Labelled[F, A, B] {
        override def observe(n: A, labels: B): F[Unit] = Applicative[F].unit
      }
  }
}
