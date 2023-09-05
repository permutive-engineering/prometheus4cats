package prometheus4cats

import cats.Applicative
import cats.data.NonEmptySeq

trait CounterExemplarSampler[F[_], -A] {
  def sample(previous: Option[Exemplar.Data]): F[Option[Exemplar.Labels]]
  def sample(value: A, previous: Option[Exemplar.Data]): F[Option[Exemplar.Labels]]
}

object CounterExemplarSampler extends Common {
  def apply[F[_], A](implicit sampler: CounterExemplarSampler[F, A]): CounterExemplarSampler[F, A] = implicitly
}

trait HistogramExemplarSampler[F[_], -A] {
  def sample(value: A, buckets: NonEmptySeq[Double], previous: Option[Exemplar.Data]): F[Option[Exemplar.Labels]]
}

object HistogramExemplarSampler extends Common {
  def apply[F[_], A](implicit sampler: HistogramExemplarSampler[F, A]): HistogramExemplarSampler[F, A] = implicitly
}

trait ExemplarSampler[F[_], -A] extends CounterExemplarSampler[F, A] with HistogramExemplarSampler[F, A]

object ExemplarSampler extends Common {
  def apply[F[_], A](implicit sampler: ExemplarSampler[F, A]): ExemplarSampler[F, A] = implicitly
}

trait Common {
  object Implicits {
    implicit def noop[F[_]: Applicative, A]: ExemplarSampler[F, A] = new ExemplarSampler[F, A] {
      override def sample(previous: Option[Exemplar.Data]): F[Option[Exemplar.Labels]] = Applicative[F].pure(None)

      override def sample(value: A, previous: Option[Exemplar.Data]): F[Option[Exemplar.Labels]] =
        Applicative[F].pure(None)

      override def sample(
          value: A,
          buckets: NonEmptySeq[Double],
          previous: Option[Exemplar.Data]
      ): F[Option[Exemplar.Labels]] = Applicative[F].pure(None)
    }
  }
}
