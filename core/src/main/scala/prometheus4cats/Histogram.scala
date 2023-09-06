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

import cats.data.NonEmptySeq
import cats.effect.kernel.Ref
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.{Applicative, Contravariant, FlatMap, Monad, ~>}
import prometheus4cats.internal.Refined.Regex
import prometheus4cats.internal.{Neq, Refined}

sealed abstract class Histogram[F[_], A, B](
    protected[prometheus4cats] val exemplarState: Histogram.ExemplarState[F]
) extends Metric[A]
    with Metric.Labelled[B] {
  self =>

  protected def observeProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit]

  def contramap[C](f: C => A): Histogram[F, C, B] =
    new Histogram[F, C, B](exemplarState) {
      override def observeProvidedExemplarImpl(n: C, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        self.observeProvidedExemplarImpl(f(n), labels, exemplar)
    }

  def contramapLabels[C](f: C => B): Histogram[F, A, C] =
    new Histogram[F, A, C](exemplarState) {
      override def observeProvidedExemplarImpl(n: A, labels: C, exemplar: Option[Exemplar.Labels]): F[Unit] =
        self.observeProvidedExemplarImpl(n, f(labels), exemplar)
    }

  final def mapK[G[_]](fk: F ~> G): Histogram[G, A, B] =
    new Histogram[G, A, B](exemplarState.mapK(fk)) {
      override def observeProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): G[Unit] =
        fk(self.observeProvidedExemplarImpl(n, labels, exemplar))
    }

}

object Histogram {

  sealed trait ExemplarState[F[_]] {
    self =>
    def surround[A](n: A)(
        fa: Option[Exemplar.Labels] => F[Unit]
    )(implicit F: Monad[F], exemplarSampler: ExemplarSampler.Histogram[F, A]): F[Unit]

    def mapK[G[_]](fk: F ~> G): ExemplarState[G]
  }

  object ExemplarState {
    def getSet[F[_]](
        buckets: NonEmptySeq[Double],
        get: F[Option[Exemplar.Data]],
        set: Option[Exemplar.Data] => F[Unit]
    ): ExemplarState[F] =
      new ExemplarState[F] {
        override def surround[A](n: A)(
            fa: Option[Exemplar.Labels] => F[Unit]
        )(implicit F: Monad[F], exemplarSampler: ExemplarSampler.Histogram[F, A]): F[Unit] =
          for {
            previous <- get
            next <- exemplarSampler.sample(n, buckets, previous)
            _ <- fa(next.map(_.labels))
            _ <- next.traverse_(data => set(Some(data)))
          } yield ()

        def mapK[G[_]](fk: F ~> G): ExemplarState[G] = getSet(buckets, fk(get), ex => fk(set(ex)))
      }

    def fromRef[F[_]](buckets: NonEmptySeq[Double], ref: Ref[F, Option[Exemplar.Data]]): ExemplarState[F] =
      getSet(buckets, ref.get, ref.set)

    def noop[F[_]]: ExemplarState[F] = new ExemplarState[F] {
      self =>
      override def surround[A](n: A)(
          fa: Option[Exemplar.Labels] => F[Unit]
      )(implicit F: Monad[F], exemplarSampler: ExemplarSampler.Histogram[F, A]): F[Unit] =
        Applicative[F].unit

      override def mapK[G[_]](fk: F ~> G): ExemplarState[G] = noop[G]

    }
  }

  implicit class HistogramSyntax[F[_], A](histogram: Histogram[F, A, Unit]) {
    final def observe(n: A): F[Unit] = observeProvidedExemplar(n, None)

    final def observeWithSampledExemplar(
        n: A
    )(implicit F: Monad[F], exemplarSampler: ExemplarSampler.Histogram[F, A]): F[Unit] =
      histogram.exemplarState.surround(n)(observeProvidedExemplar(n, _))

    final def observeWithExemplar(n: A)(implicit F: FlatMap[F], exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(observeProvidedExemplar(n, _))

    final def observeProvidedExemplar(n: A, exemplar: Option[Exemplar.Labels]): F[Unit] =
      histogram.observeProvidedExemplarImpl(n = n, labels = (), exemplar = exemplar)
  }

  implicit class LabelledCounterSyntax[F[_], A, B](histogram: Histogram[F, A, B])(implicit ev: Unit Neq B) {
    final def observe(n: A, labels: B): F[Unit] = observeProvidedExemplar(n, labels, None)

    final def observeWithSampledExemplar(
        n: A,
        labels: B
    )(implicit F: Monad[F], exemplarSampler: ExemplarSampler.Histogram[F, A]): F[Unit] =
      histogram.exemplarState.surround(n)(observeProvidedExemplar(n, labels, _))

    final def observeWithExemplar(n: A, labels: B)(implicit F: FlatMap[F], exemplar: Exemplar[F]): F[Unit] =
      exemplar.get.flatMap(observeProvidedExemplar(n, labels, _))

    final def observeProvidedExemplar(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
      histogram.observeProvidedExemplarImpl(n = n, labels = labels, exemplar = exemplar)
  }

  /** A value that is produced by a histogram
    *
    * @note
    *   the size `bucketValues` '''MUST MATCH''' that of the number of buckets defined when creating the histogram in
    *   [[MetricFactory.WithCallbacks]]. If they do not match, the histogram may not render correctly or at all.
    *
    * @param sum
    *   the histogram sum
    * @param bucketValues
    *   values corresponding to to buckets defined when creating the histogram
    * @tparam A
    *   number type for this histogram value
    */
  case class Value[A](sum: A, bucketValues: NonEmptySeq[A]) {
    def map[B](f: A => B): Value[B] = Value(f(sum), bucketValues.map(f))
  }

  val DefaultHttpBuckets: NonEmptySeq[Double] =
    NonEmptySeq.of(0.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)

  /** Refined value class for a histogram name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal with Refined.Value[String]

  object Name
      extends Regex[Name]("^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern, new Name(_))
      with internal.HistogramNameFromStringLiteral

  implicit def catsInstances[F[_], C]: Contravariant[Histogram[F, *, C]] =
    new Contravariant[Histogram[F, *, C]] {
      override def contramap[A, B](fa: Histogram[F, A, C])(f: B => A): Histogram[F, B, C] = fa.contramap(f)
    }

  implicit def labelsContravariant[F[_], C]: LabelsContravariant[Histogram[F, C, *]] =
    new LabelsContravariant[Histogram[F, C, *]] {
      override def contramapLabels[A, B](fa: Histogram[F, C, A])(f: B => A): Histogram[F, C, B] = fa.contramapLabels(f)
    }

  def make[F[_], A, B](
      exemplarState: ExemplarState[F],
      _observe: (A, B, Option[Exemplar.Labels]) => F[Unit]
  ): Histogram[F, A, B] =
    new Histogram[F, A, B](exemplarState) {
      override def observeProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        _observe(n, labels, exemplar)
    }

  def noop[F[_]: Applicative, A, B]: Histogram[F, A, B] =
    new Histogram[F, A, B](ExemplarState.noop[F]) {
      override def observeProvidedExemplarImpl(n: A, labels: B, exemplar: Option[Exemplar.Labels]): F[Unit] =
        Applicative[F].unit
    }

}
