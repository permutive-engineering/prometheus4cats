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

package openmetrics4s

import cats.effect.kernel.syntax.monadCancel._
import cats.effect.kernel.{MonadCancelThrow, Outcome}
import cats.syntax.flatMap._

sealed abstract class OpStatus[F[_]: MonadCancelThrow] {
  type Metric

  final def surround[A](fa: F[A]): F[A] = fa.guaranteeCase {
    case Outcome.Succeeded(_) => onSucceeded
    case Outcome.Errored(_) => onErrored
    case Outcome.Canceled() => onCanceled
  }

  protected def onCanceled: F[Unit]

  protected def onErrored: F[Unit]

  protected def onSucceeded: F[Unit]
}

object OpStatus {

  type Aux[F[_], A, M[_[_], _, _]] = OpStatus[F] {
    type Metric = M[F, A, Status]
  }

  def fromCounter[F[_]: MonadCancelThrow, A](
      counter: Counter.Labelled[F, A, Status]
  ): OpStatus.Aux[F, A, Counter.Labelled] = new OpStatus[F] {
    override type Metric = Counter.Labelled[F, A, Status]

    override protected val onCanceled: F[Unit] = counter.inc(Status.Canceled)

    override protected val onErrored: F[Unit] = counter.inc(Status.Errored)

    override protected val onSucceeded: F[Unit] = counter.inc(Status.Succeeded)
  }

  def fromGauge[F[_]: MonadCancelThrow, A](gauge: Gauge.Labelled[F, A, Status]): OpStatus.Aux[F, A, Gauge.Labelled] =
    new OpStatus[F] {
      override type Metric = Gauge.Labelled[F, A, Status]

      override protected val onCanceled: F[Unit] =
        (gauge.reset(Status.Succeeded) >> gauge.reset(Status.Errored) >> gauge.inc(Status.Canceled)).uncancelable

      override protected val onErrored: F[Unit] =
        (gauge.reset(Status.Succeeded) >> gauge.reset(Status.Canceled) >> gauge.inc(Status.Errored)).uncancelable

      override protected val onSucceeded: F[Unit] =
        (gauge.reset(Status.Canceled) >> gauge.reset(Status.Errored) >> gauge.inc(Status.Succeeded)).uncancelable
    }

  sealed abstract class Labelled[F[_]: MonadCancelThrow, A] {
    type Metric

    final def surround[B](fa: F[B], labels: A): F[B] = fa.guaranteeCase {
      case Outcome.Succeeded(_) => onSucceeded(labels)
      case Outcome.Errored(_) => onErrored(labels)
      case Outcome.Canceled() => onCanceled(labels)
    }

    final def surroundWithComputedLabels[B](
        fb: F[B],
        labelsCanceled: A
    )(labelsSuccess: B => A, labelsError: Throwable => A): F[B] = fb.guaranteeCase {
      case Outcome.Succeeded(fb) => fb.flatMap(b => onSucceeded(labelsSuccess(b)))
      case Outcome.Errored(th) => onErrored(labelsError(th))
      case Outcome.Canceled() => onCanceled(labelsCanceled)
    }

    protected def onCanceled(labels: A): F[Unit]

    protected def onErrored(labels: A): F[Unit]

    protected def onSucceeded(labels: A): F[Unit]
  }

  object Labelled {
    type Aux[F[_], A, B, M[_[_], _, _]] = OpStatus.Labelled[F, B] {
      type Metric = M[F, A, (B, Status)]
    }

    def fromCounter[F[_]: MonadCancelThrow, A, B](
        counter: Counter.Labelled[F, A, (B, Status)]
    ): OpStatus.Labelled.Aux[F, A, B, Counter.Labelled] = new OpStatus.Labelled[F, B] {
      override type Metric = Counter.Labelled[F, A, (B, Status)]

      override protected def onCanceled(labels: B): F[Unit] = counter.inc((labels, Status.Canceled))

      override protected def onErrored(labels: B): F[Unit] = counter.inc((labels, Status.Errored))

      override protected def onSucceeded(labels: B): F[Unit] = counter.inc((labels, Status.Succeeded))
    }

    def fromGauge[F[_]: MonadCancelThrow, A, B](
        gauge: Gauge.Labelled[F, A, (B, Status)]
    ): OpStatus.Labelled.Aux[F, A, B, Gauge.Labelled] =
      new OpStatus.Labelled[F, B] {
        override type Metric = Gauge.Labelled[F, A, (B, Status)]

        override protected def onCanceled(labels: B): F[Unit] =
          (gauge.reset(labels -> Status.Succeeded) >> gauge.reset(labels -> Status.Errored) >> gauge.inc(
            labels -> Status.Canceled
          )).uncancelable

        override protected def onErrored(labels: B): F[Unit] =
          (gauge.reset(labels -> Status.Succeeded) >> gauge.reset(labels -> Status.Canceled) >> gauge.inc(
            labels -> Status.Errored
          )).uncancelable

        override protected def onSucceeded(labels: B): F[Unit] =
          (gauge.reset(labels -> Status.Canceled) >> gauge.reset(labels -> Status.Errored) >> gauge.inc(
            labels -> Status.Succeeded
          )).uncancelable
      }
  }
}
