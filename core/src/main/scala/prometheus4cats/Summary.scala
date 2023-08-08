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

sealed abstract class Summary[F[_], -A, B] extends Metric[A] with Metric.Labelled[B] {
  self =>

  def observe(n: A, labels: B): F[Unit]
  def observe(n: A)(implicit ev: Unit =:= B): F[Unit] = observe(n = n, labels = ev(()))

  def contramap[C](f: C => A): Summary[F, C, B] = new Summary[F, C, B] {
    override def observe(n: C, labels: B): F[Unit] = self.observe(f(n), labels)
  }

  def contramapLabels[C](f: C => B): Summary[F, A, C] = new Summary[F, A, C] {
    override def observe(n: A, labels: C): F[Unit] = self.observe(n, f(labels))
  }

  final def mapK[G[_]](fk: F ~> G): Summary[G, A, B] =
    new Summary[G, A, B] {
      override def observe(n: A, labels: B): G[Unit] = fk(self.observe(n, labels))
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

  implicit def catsInstances[F[_], C]: Contravariant[Summary[F, *, C]] =
    new Contravariant[Summary[F, *, C]] {
      override def contramap[A, B](fa: Summary[F, A, C])(f: B => A): Summary[F, B, C] = fa.contramap(f)
    }

  implicit def labelsContravariant[F[_], C]: LabelsContravariant[Summary[F, C, *]] =
    new LabelsContravariant[Summary[F, C, *]] {
      override def contramapLabels[A, B](fa: Summary[F, C, A])(f: B => A): Summary[F, C, B] = fa.contramapLabels(f)
    }

  def make[F[_], A, B](_observe: (A, B) => F[Unit]): Summary[F, A, B] =
    new Summary[F, A, B] {
      override def observe(n: A, labels: B): F[Unit] = _observe(n, labels)
    }

  def noop[F[_]: Applicative, A, B]: Summary[F, A, B] =
    new Summary[F, A, B] {
      override def observe(n: A, labels: B): F[Unit] = Applicative[F].unit
    }

}
