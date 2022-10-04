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

package com.permutive.metrics

import cats.{Applicative, Eq, Hash, Order, Show, ~>}
sealed abstract class Counter[F[_]] { self =>

  def inc(n: Double = 1.0): F[Unit]

  final def mapK[G[_]](fk: F ~> G): Counter[G] = new Counter[G] {
    override def inc(n: Double): G[Unit] = fk(self.inc(n))
  }
}

/** Escape hatch for writing testing implementations in `metrics-testing` module
  */
abstract private[metrics] class Counter_[F[_]] extends Counter[F]

object Counter {

  /** Refined value class for a counter name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal {
    override def toString: String = value
  }

  object Name extends CounterNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*_total$".r

    /** Parse a [[Name]] from the given string
      *
      * @param string
      *   value from which to parse a counter name
      * @return
      *   a parsed [[Name]] or failure message, represented by an [[scala.Either]]
      */
    def from(string: String): Either[String, Name] =
      Either.cond(
        regex.matches(string),
        new Name(string),
        s"$string must match `$regex`"
      )

    implicit val CounterNameHash: Hash[Name] = Hash.by(_.value)

    implicit val CounterNameEq: Eq[Name] = Eq.by(_.value)

    implicit val CounterNameShow: Show[Name] = Show.show(_.value)

    implicit val CounterNameOrder: Order[Name] = Order.by(_.value)

  }

  def make[F[_]](_inc: Double => F[Unit]): Counter[F] = new Counter[F] {
    override def inc(n: Double): F[Unit] = _inc(n)
  }

  def noop[F[_]: Applicative]: Counter[F] = new Counter[F] {
    override def inc(n: Double): F[Unit] = Applicative[F].unit
  }

  sealed abstract class Labelled[F[_], A] {
    self =>

    def inc(n: Double = 1.0, labels: A): F[Unit]

    final def mapK[G[_]](fk: F ~> G): Counter.Labelled[G, A] =
      new Labelled[G, A] {
        override def inc(n: Double, labels: A): G[Unit] = fk(
          self.inc(n, labels)
        )
      }
  }

  /** Escape hatch for writing testing implementations in `metrics-testing` module
    */
  abstract private[metrics] class Labelled_[F[_], A] extends Labelled[F, A]

  object Labelled {
    def make[F[_], A](_inc: (Double, A) => F[Unit]): Labelled[F, A] =
      new Labelled[F, A] {
        override def inc(n: Double, labels: A): F[Unit] = _inc(n, labels)
      }

    def noop[F[_]: Applicative, A]: Labelled[F, A] = new Labelled[F, A] {
      override def inc(n: Double, labels: A): F[Unit] = Applicative[F].unit
    }
  }

}
