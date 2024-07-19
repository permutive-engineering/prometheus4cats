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

import cats.Applicative
import cats.Contravariant
import cats.~>

import prometheus4cats.internal.Neq
import prometheus4cats.internal.Refined
import prometheus4cats.internal.Refined.Regex

sealed abstract class Summary[F[_], -A, B] extends Metric[A] with Metric.Labelled[B] {
  self =>

  protected def observeImpl(n: A, labels: B): F[Unit]

  def contramap[C](f: C => A): Summary[F, C, B] = new Summary[F, C, B] {

    override def observeImpl(n: C, labels: B): F[Unit] = self.observeImpl(f(n), labels)

  }

  def contramapLabels[C](f: C => B): Summary[F, A, C] = new Summary[F, A, C] {

    override def observeImpl(n: A, labels: C): F[Unit] = self.observeImpl(n, f(labels))

  }

  final def mapK[G[_]](fk: F ~> G): Summary[G, A, B] =
    new Summary[G, A, B] {

      override def observeImpl(n: A, labels: B): G[Unit] = fk(self.observeImpl(n, labels))

    }

}

object Summary {

  implicit class SummarySyntax[F[_], -A](summary: Summary[F, A, Unit]) {

    def observe(n: A): F[Unit] = summary.observeImpl(n, ())

  }

  implicit class LabelledSummarySyntax[F[_], -A, B](summary: Summary[F, A, B])(implicit ev: Unit Neq B) {

    def observe(n: A, labels: B): F[Unit] = summary.observeImpl(n, labels)

  }

  final class AgeBuckets(val value: Int) extends AnyVal with Refined.Value[Int]

  object AgeBuckets
      extends Refined[Int, AgeBuckets](
        make = new AgeBuckets(_),
        test = _ > 0,
        nonMatchMessage = a => s"AgeBuckets value $a must be greater than 0"
      )
      with internal.SummaryAgeBucketsFromIntLiteral {

    val Default: AgeBuckets = new AgeBuckets(5)

  }

  final class Quantile(val value: Double) extends AnyVal with Refined.Value[Double]

  object Quantile
      extends Refined[Double, Quantile](
        make = new Quantile(_),
        test = a => a >= 0.0 && a <= 1.0,
        nonMatchMessage = a => s"Quantile value $a must be between 0.0 and 1.0"
      )
      with internal.SummaryQuantileFromDoubleLiteral

  final class AllowedError(val value: Double) extends AnyVal with Refined.Value[Double]

  object AllowedError
      extends Refined[Double, AllowedError](
        make = new AllowedError(_),
        test = a => a >= 0.0 && a <= 1.0,
        nonMatchMessage = a => s"AllowedError value $a must be between 0.0 and 1.0"
      )
      with internal.SummaryAllowedErrorFromDoubleLiteral

  final case class QuantileDefinition(value: Quantile, error: AllowedError)

  case class Value[A](count: A, sum: A, quantiles: Map[Double, A] = Map.empty) {

    def map[B](f: A => B): Value[B] = Value(f(count), f(sum), quantiles.map { case (q, v) => q -> f(v) })

  }

  /** Refined value class for a gauge name that has been parsed from a string */
  final class Name private (val value: String) extends AnyVal with Refined.Value[String]

  object Name
      extends Regex[Name]("^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern, new Name(_))
      with internal.SummaryNameFromStringLiteral

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

      override def observeImpl(n: A, labels: B): F[Unit] = _observe(n, labels)

    }

  def noop[F[_]: Applicative, A, B]: Summary[F, A, B] =
    new Summary[F, A, B] {

      override def observeImpl(n: A, labels: B): F[Unit] = Applicative[F].unit

    }

}
