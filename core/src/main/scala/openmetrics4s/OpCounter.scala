package openmetrics4s

import cats.Show
import cats.effect.kernel.syntax.monadCancel._
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
