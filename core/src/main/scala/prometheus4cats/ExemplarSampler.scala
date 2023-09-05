package prometheus4cats

import cats.{Applicative, ~>}
import cats.data.NonEmptySeq

trait CounterExemplarSampler[F[_], -A] { self =>
  def sample(previous: Option[Exemplar.Data]): F[Option[Exemplar.Labels]]
  def sample(value: A, previous: Option[Exemplar.Data]): F[Option[Exemplar.Labels]]

  def mapK[G[_]](fk: F ~> G): CounterExemplarSampler[G, A] = new CounterExemplarSampler[G, A] {
    override def sample(previous: Option[Exemplar.Data]): G[Option[Exemplar.Labels]] = fk(self.sample(previous))

    override def sample(value: A, previous: Option[Exemplar.Data]): G[Option[Exemplar.Labels]] = fk(
      self.sample(value, previous)
    )
  }
}

object CounterExemplarSampler extends Common {
  def apply[F[_], A](implicit sampler: CounterExemplarSampler[F, A]): CounterExemplarSampler[F, A] = implicitly
}

trait HistogramExemplarSampler[F[_], -A] { self =>
  def sample(value: A, buckets: NonEmptySeq[Double], previous: Option[Exemplar.Data]): F[Option[Exemplar.Labels]]

  def mapK[G[_]](fk: F ~> G): HistogramExemplarSampler[G, A] = new HistogramExemplarSampler[G, A] {
    override def sample(
        value: A,
        buckets: NonEmptySeq[Double],
        previous: Option[Exemplar.Data]
    ): G[Option[Exemplar.Labels]] =
      fk(self.sample(value, buckets, previous))
  }
}

object HistogramExemplarSampler extends Common {
  def apply[F[_], A](implicit sampler: HistogramExemplarSampler[F, A]): HistogramExemplarSampler[F, A] = implicitly
}

trait ExemplarSampler[F[_], -A] extends CounterExemplarSampler[F, A] with HistogramExemplarSampler[F, A] { self =>
  override def mapK[G[_]](fk: F ~> G): ExemplarSampler[G, A] = new ExemplarSampler[G, A] {
    override def sample(previous: Option[Exemplar.Data]): G[Option[Exemplar.Labels]] = fk(self.sample(previous))

    override def sample(value: A, previous: Option[Exemplar.Data]): G[Option[Exemplar.Labels]] = fk(
      self.sample(value, previous)
    )

    override def sample(
        value: A,
        buckets: NonEmptySeq[Double],
        previous: Option[Exemplar.Data]
    ): G[Option[Exemplar.Labels]] = fk(self.sample(value, buckets, previous))
  }
}

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
