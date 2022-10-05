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

sealed abstract class Gauge[F[_]] { self =>

  def inc(n: Double = 1.0): F[Unit]
  def dec(n: Double = 1.0): F[Unit]
  def set(n: Double): F[Unit]

  def setToCurrentTime(): F[Unit]

  final def mapK[G[_]](fk: F ~> G): Gauge[G] = new Gauge[G] {
    override def inc(n: Double): G[Unit] = fk(self.inc(n))

    override def dec(n: Double): G[Unit] = fk(self.dec(n))

    override def set(n: Double): G[Unit] = fk(self.set(n))

    override def setToCurrentTime(): G[Unit] = fk(self.setToCurrentTime())
  }

}

/** Escape hatch for writing testing implementations in `metrics-testing` module
  */
abstract private[metrics] class Gauge_[F[_]] extends Gauge[F]

object Gauge {

  /** Refined value class for a gauge name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal {
    override def toString: String = s"""Gauge.Name("$value")"""
  }

  object Name extends GaugeNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern

    /** Parse a [[Name]] from the given string
      * @param string
      *   value from which to parse a gauge name
      * @return
      *   a parsed [[Name]] or failure message, represented by an [[scala.Either]]
      */
    def from(string: String): Either[String, Name] =
      Either.cond(
        regex.matcher(string).matches(),
        new Name(string),
        s"$string must match `$regex`"
      )

    implicit val GaugeNameHash: Hash[Name] = Hash.by(_.value)

    implicit val GaugeNameEq: Eq[Name] = Eq.by(_.value)

    implicit val GaugeNameShow: Show[Name] = Show.show(_.value)

    implicit val GaugeNameOrder: Order[Name] = Order.by(_.value)

  }

  def make[F[_]](
      _inc: Double => F[Unit],
      _dec: Double => F[Unit],
      _set: Double => F[Unit],
      _setToCurrentTime: F[Unit]
  ): Gauge[F] = new Gauge[F] {
    override def inc(n: Double): F[Unit] = _inc(n)

    override def dec(n: Double): F[Unit] = _dec(n)

    override def set(n: Double): F[Unit] = _set(n)

    override def setToCurrentTime(): F[Unit] = _setToCurrentTime
  }

  def noop[F[_]: Applicative]: Gauge[F] = new Gauge[F] {
    override def inc(n: Double): F[Unit] = Applicative[F].unit

    override def dec(n: Double): F[Unit] = Applicative[F].unit

    override def set(n: Double): F[Unit] = Applicative[F].unit

    override def setToCurrentTime(): F[Unit] = Applicative[F].unit
  }

  abstract class Labelled[F[_], A] {
    self =>

    def inc(n: Double = 1.0, labels: A): F[Unit]

    def dec(n: Double = 1.0, labels: A): F[Unit]

    def set(n: Double, labels: A): F[Unit]

    def setToCurrentTime(labels: A): F[Unit]

    final def mapK[G[_]](fk: F ~> G): Labelled[G, A] =
      new Labelled[G, A] {
        override def inc(n: Double, labels: A): G[Unit] = fk(
          self.inc(n, labels)
        )

        override def dec(n: Double, labels: A): G[Unit] = fk(
          self.dec(n, labels)
        )

        override def set(n: Double, labels: A): G[Unit] = fk(
          self.set(n, labels)
        )

        override def setToCurrentTime(labels: A): G[Unit] = fk(
          self.setToCurrentTime(labels)
        )
      }

  }

  /** Escape hatch for writing testing implementations in `metrics-testing` module
    */
  abstract private[metrics] class Labelled_[F[_], A] extends Labelled[F, A]

  object Labelled {
    def make[F[_], A](
        _inc: (Double, A) => F[Unit],
        _dec: (Double, A) => F[Unit],
        _set: (Double, A) => F[Unit],
        _setToCurrentTime: A => F[Unit]
    ): Labelled[F, A] = new Labelled[F, A] {
      override def inc(n: Double, labels: A): F[Unit] = _inc(n, labels)

      override def dec(n: Double, labels: A): F[Unit] = _dec(n, labels)

      override def set(n: Double, labels: A): F[Unit] = _set(n, labels)

      override def setToCurrentTime(labels: A): F[Unit] = _setToCurrentTime(
        labels
      )
    }

    def noop[F[_]: Applicative, A]: Labelled[F, A] = new Labelled[F, A] {
      override def inc(n: Double, labels: A): F[Unit] = Applicative[F].unit

      override def dec(n: Double, labels: A): F[Unit] = Applicative[F].unit

      override def set(n: Double, labels: A): F[Unit] = Applicative[F].unit

      override def setToCurrentTime(labels: A): F[Unit] = Applicative[F].unit
    }
  }
}
