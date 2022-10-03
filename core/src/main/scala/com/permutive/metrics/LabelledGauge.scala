package com.permutive.metrics

import cats.~>

abstract class LabelledGauge[F[_], A] { self =>

  def inc(n: Double = 1.0, labels: A): F[Unit]
  def dec(n: Double = 1.0, labels: A): F[Unit]
  def set(n: Double, labels: A): F[Unit]

  def setToCurrentTime(labels: A): F[Unit]

  final def mapK[G[_]](fk: F ~> G): LabelledGauge[G, A] =
    new LabelledGauge[G, A] {
      override def inc(n: Double, labels: A): G[Unit] = fk(self.inc(n, labels))

      override def dec(n: Double, labels: A): G[Unit] = fk(self.dec(n, labels))

      override def set(n: Double, labels: A): G[Unit] = fk(self.set(n, labels))

      override def setToCurrentTime(labels: A): G[Unit] = fk(
        self.setToCurrentTime(labels)
      )
    }

}

/** Escape hatch for writing testing implementations in `metrics-testing` module
  */
abstract private[metrics] class LabelledGauge_[F[_], A]
    extends LabelledGauge[F, A]

object LabelledGauge {
  def make[F[_], A](
      _inc: (Double, A) => F[Unit],
      _dec: (Double, A) => F[Unit],
      _set: (Double, A) => F[Unit],
      _setToCurrentTime: A => F[Unit]
  ): LabelledGauge[F, A] = new LabelledGauge[F, A] {
    override def inc(n: Double, labels: A): F[Unit] = _inc(n, labels)

    override def dec(n: Double, labels: A): F[Unit] = _dec(n, labels)

    override def set(n: Double, labels: A): F[Unit] = _set(n, labels)

    override def setToCurrentTime(labels: A): F[Unit] = _setToCurrentTime(
      labels
    )
  }
}
