package com.permutive.metrics

import cats.effect.kernel.Clock
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, FlatMap, MonadThrow}

import scala.concurrent.duration._

/** Interface that represents the ability to record the duration taken to run an
  * effect
  */
trait Record[F[_]] {
  def record[A](fa: F[A])(observeDuration: (FiniteDuration, A) => F[Unit]): F[A]
}

object Record {
  def apply[F[_]](implicit M: Record[F]): Record[F] = M

  implicit def recordForClock[F[_]: Clock: FlatMap]: Record[F] =
    new ClockRecord[F](Clock[F])

  def noOpRecord[F[_]]: Record[F] = new Record[F] {
    override def record[A](fa: F[A])(
        observeDuration: (FiniteDuration, A) => F[Unit]
    ): F[A] = fa
  }
}

private[metrics] class ClockRecord[F[_]: FlatMap](clock: Clock[F])
    extends Record[F] {
  override def record[A](
      fa: F[A]
  )(observeDuration: (FiniteDuration, A) => F[Unit]): F[A] =
    clock.timed(fa).flatMap { case (t, a) =>
      observeDuration(t, a).as(a)
    }
}

/** Interface that represents the ability to record the duration taken to run an
  * effect with error handling
  */
trait RecordAttempt[F[_]] extends Record[F] {
  def recordAttemptFold[A, B](
      fa: F[A],
      observeDuration: (FiniteDuration, B) => F[Unit],
      transformSuccess: A => B,
      transformError: PartialFunction[Throwable, B]
  ): F[A]

  def recordAttempt[A](
      fa: F[A],
      observeDuration: (FiniteDuration, A) => F[Unit],
      transformError: PartialFunction[Throwable, A]
  ): F[A] =
    recordAttemptFold[A, A](fa, observeDuration, identity, transformError)
}

object RecordAttempt {
  def apply[F[_]](implicit M: RecordAttempt[F]): RecordAttempt[F] = M

  implicit def recordAttemptForClockMonadThrow[F[_]: Clock: MonadThrow]
      : ClockRecordAttempt[F] =
    new ClockRecordAttempt[F](Clock[F])

  def noOpRecordAttempt[F[_]]: RecordAttempt[F] = new RecordAttempt[F] {
    override def recordAttemptFold[A, B](
        fa: F[A],
        observeDuration: (FiniteDuration, B) => F[Unit],
        transformSuccess: A => B,
        transformError: PartialFunction[Throwable, B]
    ): F[A] = fa

    override def record[A](fa: F[A])(
        observeDuration: (FiniteDuration, A) => F[Unit]
    ): F[A] = fa
  }
}

private[metrics] class ClockRecordAttempt[F[_]: MonadThrow](clock: Clock[F])
    extends ClockRecord[F](clock)
    with RecordAttempt[F] {
  override def recordAttemptFold[A, B](
      fa: F[A],
      observeDuration: (FiniteDuration, B) => F[Unit],
      transformSuccess: A => B,
      transformError: PartialFunction[Throwable, B]
  ): F[A] = for {
    x <- clock.timed(fa.attempt)
    _ <- x._2.fold(
      e =>
        transformError
          .lift(e)
          .fold(Applicative[F].unit)(b => observeDuration(x._1, b)),
      a => observeDuration(x._1, transformSuccess(a))
    )
    res <- x._2.liftTo[F]
  } yield res
}
