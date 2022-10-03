package com.permutive.metrics

import cats.Eq
import cats.Hash
import cats.Order
import cats.Show
import cats.~>

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

  final class Name private (val value: String) extends AnyVal {

    override def toString: String = value

  }

  object Name extends GaugeNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r

    def from(string: String): Either[String, Name] =
      Either.cond(
        regex.matches(string),
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
}
