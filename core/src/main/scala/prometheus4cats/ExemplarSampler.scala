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
