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

import cats.Show
import cats.effect.kernel.syntax.monadCancel._
import cats.syntax.flatMap._
import cats.effect.kernel.{MonadCancelThrow, Outcome}

sealed abstract class OpCounter[F[_]: MonadCancelThrow] {
  final def surround[A](fa: F[A]): F[A] = fa.guaranteeCase {
    case Outcome.Succeeded(_) => onSucceeded
    case Outcome.Errored(_) => onErrored
    case Outcome.Canceled() => onCanceled
  }

  protected def onCanceled: F[Unit]

  protected def onErrored: F[Unit]

  protected def onSucceeded: F[Unit]
}

object OpCounter {
  sealed trait Status
  object Status {
    case object Succeeded extends Status

    case object Errored extends Status

    case object Canceled extends Status

    implicit val catsInstances: Show[Status] = Show.show {
      case Status.Succeeded => "succeeded"
      case Status.Errored => "errored"
      case Status.Canceled => "canceled"
    }
  }

  def fromCounter[F[_]: MonadCancelThrow, A](counter: Counter.Labelled[F, A, Status]): OpCounter[F] = new OpCounter[F] {
    override protected val onCanceled: F[Unit] = counter.inc(Status.Canceled)

    override protected val onErrored: F[Unit] = counter.inc(Status.Errored)

    override protected val onSucceeded: F[Unit] = counter.inc(Status.Succeeded)
  }

  sealed abstract class Labelled[F[_]: MonadCancelThrow, A] {
    final def surround[B](fa: F[B], labels: A): F[B] = fa.guaranteeCase {
      case Outcome.Succeeded(_) => onSucceeded(labels)
      case Outcome.Errored(_) => onErrored(labels)
      case Outcome.Canceled() => onCanceled(labels)
    }

    final def surroundWithComputedLabels[B](
        fb: F[B]
    )(labelsSuccess: B => A, labelsError: Throwable => A, labelsCanceled: A): F[B] = fb.guaranteeCase {
      case Outcome.Succeeded(fb) => fb.flatMap(b => onSucceeded(labelsSuccess(b)))
      case Outcome.Errored(th) => onErrored(labelsError(th))
      case Outcome.Canceled() => onCanceled(labelsCanceled)
    }

    protected def onCanceled(labels: A): F[Unit]

    protected def onErrored(labels: A): F[Unit]

    protected def onSucceeded(labels: A): F[Unit]
  }

  object Labelled {
    def fromCounter[F[_]: MonadCancelThrow, A, B](
        counter: Counter.Labelled[F, A, (B, Status)]
    ): OpCounter.Labelled[F, B] = new OpCounter.Labelled[F, B] {
      override protected def onCanceled(labels: B): F[Unit] = counter.inc((labels, Status.Canceled))

      override protected def onErrored(labels: B): F[Unit] = counter.inc((labels, Status.Errored))

      override protected def onSucceeded(labels: B): F[Unit] = counter.inc((labels, Status.Succeeded))
    }
  }
}
