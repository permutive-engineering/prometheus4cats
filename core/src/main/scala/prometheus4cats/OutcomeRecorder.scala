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

import cats.effect.kernel.syntax.monadCancel._
import cats.effect.kernel.{MonadCancelThrow, Outcome}
import cats.syntax.flatMap._
import cats.{FlatMap, Show, ~>}

/** A derived metric type that records the outcome of an operation. See [[OutcomeRecorder.fromCounter]] and
  * [[OutcomeRecorder.fromGauge]] for more information.
  */
sealed abstract class OutcomeRecorder[F[_], -A] extends Metric.Labelled[A] { self =>
  type Metric

  protected def onCanceled(labels: A): F[Unit]

  protected def onErrored(labels: A): F[Unit]

  protected def onSucceeded(labels: A): F[Unit]

  def contramapLabels[B](f: B => A): OutcomeRecorder[F, B] = new OutcomeRecorder[F, B] {
    override type Metric = self.Metric

    override protected def onCanceled(labels: B): F[Unit] = self.onCanceled(f(labels))

    override protected def onErrored(labels: B): F[Unit] = self.onErrored(f(labels))

    override protected def onSucceeded(labels: B): F[Unit] = self.onSucceeded(f(labels))
  }

  def mapK[G[_]](fk: F ~> G): OutcomeRecorder[G, A] = new OutcomeRecorder[G, A] {
    override type Metric = self.Metric

    override protected def onCanceled(labels: A): G[Unit] = fk(self.onCanceled(labels))

    override protected def onErrored(labels: A): G[Unit] = fk(self.onErrored(labels))

    override protected def onSucceeded(labels: A): G[Unit] = fk(self.onSucceeded(labels))
  }
}

object OutcomeRecorder {

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

  type Aux[F[_], A, B, M[_[_], _, _]] = OutcomeRecorder[F, B] {
    type Metric = M[F, A, (B, Status)]
  }

  implicit class OutcomeRecorderSyntax[F[_]](recorder: OutcomeRecorder[F, Unit]) {

    /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param fb
      *   operation to be evaluated
      */
    final def surround[B](fb: F[B])(implicit F: MonadCancelThrow[F]): F[B] = fb.guaranteeCase(recordOutcome)

    /** Record the result of provided [[cats.effect.kernel.Outcome]]
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param outcome
      *   the [[cats.effect.kernel.Outcome]] to be recorded
      */
    final def recordOutcome[B, E](outcome: Outcome[F, E, B]): F[Unit] =
      outcome match {
        case Outcome.Succeeded(_) => recorder.onSucceeded(())
        case Outcome.Errored(_) => recorder.onErrored(())
        case Outcome.Canceled() => recorder.onCanceled(())
      }
  }

  implicit class LabelledOutcomeRecorder[F[_], -A](recorder: OutcomeRecorder[F, A]) {

    /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param fb
      *   operation to be evaluated
      * @param labels
      *   labels to add to the underlying metric
      */
    final def surround[B](fb: F[B], labels: A)(implicit F: MonadCancelThrow[F]): F[B] =
      fb.guaranteeCase(recordOutcome(_, labels))

    /** Record the result of provided [[cats.effect.kernel.Outcome]]
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param outcome
      *   the [[cats.effect.kernel.Outcome]] to be recorded
      * @param labels
      *   labels to add to the underlying metric
      */
    final def recordOutcome[B, E](outcome: Outcome[F, E, B], labels: A): F[Unit] =
      outcome match {
        case Outcome.Succeeded(_) => recorder.onSucceeded(labels)
        case Outcome.Errored(_) => recorder.onErrored(labels)
        case Outcome.Canceled() => recorder.onCanceled(labels)
      }

    /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]],
      * computing additional labels from the result.
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param fb
      *   operation to be evaluated
      * @param labelsCanceled
      *   labels to add when the operation is canceled
      * @param labelsSuccess
      *   function to compute labels from the result of `fb` when the operation is successful
      * @param labelsError
      *   function to compute labels from the exception that was raised if the operation is unsuccessful
      */
    final def surroundWithComputedLabels[B](
        fb: F[B],
        labelsCanceled: A
    )(labelsSuccess: B => A, labelsError: Throwable => A)(implicit F: MonadCancelThrow[F]): F[B] =
      fb.guaranteeCase(recordOutcomeWithComputedLabels(_, labelsCanceled)(labelsSuccess, labelsError))

    /** Record the result of provided [[cats.effect.kernel.Outcome]] computing additional labels from the result.
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param outcome
      *   the [[cats.effect.kernel.Outcome]] to be recorded
      * @param labelsCanceled
      *   labels to add when the operation is canceled
      * @param labelsSuccess
      *   function to compute labels from the result of `fb` when the operation is successful
      * @param labelsError
      *   function to compute labels from the exception that was raised if the operation is unsuccessful
      */
    final def recordOutcomeWithComputedLabels[B, E](
        outcome: Outcome[F, E, B],
        labelsCanceled: A
    )(labelsSuccess: B => A, labelsError: E => A)(implicit F: FlatMap[F]): F[Unit] =
      outcome match {
        case Outcome.Succeeded(fb) => fb.flatMap(b => recorder.onSucceeded(labelsSuccess(b)))
        case Outcome.Errored(th) => recorder.onErrored(labelsError(th))
        case Outcome.Canceled() => recorder.onCanceled(labelsCanceled)
      }

  }

  implicit def labelsContravariant[F[_]]: LabelsContravariant[OutcomeRecorder[F, *]] =
    new LabelsContravariant[OutcomeRecorder[F, *]] {
      override def contramapLabels[A, B](fa: OutcomeRecorder[F, A])(f: B => A): OutcomeRecorder[F, B] =
        fa.contramapLabels(f)
    }

  /** Create an [[OutcomeRecorder]] from a [[Counter]] instance, where its labels type is a tuple of the original labels
    * of the counter and [[Status]].
    *
    * This works by incrementing a counter with a label value that corresponds to the value [[Status]] on each
    * invocation.
    *
    * The best way to construct a counter based [[OutcomeRecorder]] is to use the `.asOutcomeRecorder` on the counter
    * DSL provided by [[MetricFactory]].
    *
    * @return
    *   an [[OutcomeRecorder.Aux]] that is annotated with the type of underlying metric, in this case [[Counter]]
    */
  def fromCounter[F[_], A, B](
      counter: Counter[F, A, (B, Status)]
  ): OutcomeRecorder.Aux[F, A, B, Counter] = new OutcomeRecorder[F, B] {
    override type Metric = Counter[F, A, (B, Status)]

    override protected def onCanceled(labels: B): F[Unit] = counter.inc((labels, Status.Canceled))

    override protected def onErrored(labels: B): F[Unit] = counter.inc((labels, Status.Errored))

    override protected def onSucceeded(labels: B): F[Unit] = counter.inc((labels, Status.Succeeded))
  }

  /** Create an [[OutcomeRecorder]] from a [[Gauge]] instance, where its only label type is a tuple of the original
    * labels of the counter and [[Status]].
    *
    * This works by setting gauge with a label value that corresponds to the value of [[Status]] to `1` on each
    * invocation, while the other statuses are set to `0`.
    *
    * The best way to construct a gauge based [[OutcomeRecorder]] is to use the `.asOutcomeRecorder` on the gauge DSL
    * provided by [[MetricFactory]].
    *
    * @return
    *   an [[OutcomeRecorder.Aux]] that is annotated with the type of underlying metric, in this case [[Gauge]]
    */
  def fromGauge[F[_]: MonadCancelThrow, A, B](
      gauge: Gauge[F, A, (B, Status)]
  ): OutcomeRecorder.Aux[F, A, B, Gauge] =
    new OutcomeRecorder[F, B] {
      override type Metric = Gauge[F, A, (B, Status)]

      private def setOutcome(labels: B, status: Status): F[Unit] =
        (
          gauge.reset(labels -> Status.Succeeded) >> gauge.reset(labels -> Status.Canceled) >>
            gauge.reset(labels -> Status.Errored) >> gauge.inc(labels -> status)
        ).uncancelable

      override protected def onCanceled(labels: B): F[Unit] = setOutcome(labels, Status.Canceled)

      override protected def onErrored(labels: B): F[Unit] = setOutcome(labels, Status.Errored)

      override protected def onSucceeded(labels: B): F[Unit] = setOutcome(labels, Status.Succeeded)
    }

  /** A derived metric type that records the outcome of an operation. See [[OutcomeRecorder.fromCounter]] and
    * [[OutcomeRecorder.fromGauge]] for more information.
    */
  sealed abstract class Exemplar[F[_], -A] extends Metric.Labelled[A] {
    self =>
    type Metric

    protected def onCanceled(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

    protected def onErrored(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

    protected def onSucceeded(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

    def contramapLabels[B](f: B => A): Exemplar[F, B] = new Exemplar[F, B] {
      override type Metric = self.Metric

      override protected def onCanceled(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        self.onCanceled(f(labels), exemplar)

      override protected def onErrored(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        self.onErrored(f(labels), exemplar)

      override protected def onSucceeded(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        self.onSucceeded(f(labels), exemplar)
    }

    def mapK[G[_]](fk: F ~> G): Exemplar[G, A] = new Exemplar[G, A] {
      override type Metric = self.Metric

      override protected def onCanceled(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): G[Unit] = fk(
        self.onCanceled(labels, exemplar)
      )

      override protected def onErrored(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): G[Unit] = fk(
        self.onErrored(labels, exemplar)
      )

      override protected def onSucceeded(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): G[Unit] = fk(
        self.onSucceeded(labels, exemplar)
      )
    }
  }

  object Exemplar {
    type Aux[F[_], A, B, M[_[_], _, _]] = OutcomeRecorder.Exemplar[F, B] {
      type Metric = M[F, A, (B, Status)]
    }

    implicit class OutcomeRecorderExemplarSyntax[F[_]](recorder: OutcomeRecorder.Exemplar[F, Unit]) {

      /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
        * [[OutcomeRecorder.fromGauge]] for more details.
        *
        * @param fb
        *   operation to be evaluated
        */
      final def surround[B](
          fb: F[B],
          recordExemplarOnSucceeded: Boolean = false,
          recordExemplarOnErrored: Boolean = false,
          recordExemplarOnCanceled: Boolean = false
      )(implicit F: MonadCancelThrow[F], exemplar: prometheus4cats.Exemplar[F]): F[B] = fb.guaranteeCase(
        recordOutcome(_, recordExemplarOnSucceeded, recordExemplarOnErrored, recordExemplarOnCanceled)
      )

      /** Record the result of provided [[cats.effect.kernel.Outcome]]
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
        * [[OutcomeRecorder.fromGauge]] for more details.
        *
        * @param outcome
        *   the [[cats.effect.kernel.Outcome]] to be recorded
        */
      final def recordOutcome[B, E](
          outcome: Outcome[F, E, B],
          recordExemplarOnSucceeded: Boolean = false,
          recordExemplarOnErrored: Boolean = false,
          recordExemplarOnCanceled: Boolean = false
      )(implicit F: FlatMap[F], exemplar: prometheus4cats.Exemplar[F]): F[Unit] =
        outcome match {
          case Outcome.Succeeded(_) =>
            if (recordExemplarOnSucceeded) exemplar.get.flatMap(recorder.onSucceeded((), _))
            else recorder.onSucceeded((), None)
          case Outcome.Errored(_) =>
            if (recordExemplarOnErrored) exemplar.get.flatMap(recorder.onErrored((), _))
            else recorder.onErrored((), None)
          case Outcome.Canceled() =>
            if (recordExemplarOnCanceled) exemplar.get.flatMap(recorder.onCanceled((), _))
            else recorder.onCanceled((), None)
        }
    }

    implicit class LabelledOutcomeRecorderExemplarSyntax[F[_], A](recorder: OutcomeRecorder.Exemplar[F, A])(implicit
        ev: Unit =:!= A
    ) {

      /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
        * [[OutcomeRecorder.fromGauge]] for more details.
        *
        * @param fb
        *   operation to be evaluated
        * @param labels
        *   labels to add to the underlying metric
        */
      final def surround[B](
          fb: F[B],
          labels: A,
          recordExemplarOnSucceeded: Boolean = false,
          recordExemplarOnErrored: Boolean = false,
          recordExemplarOnCanceled: Boolean = false
      )(implicit F: MonadCancelThrow[F], exemplar: prometheus4cats.Exemplar[F]): F[B] = fb.guaranteeCase(
        recordOutcome(_, labels, recordExemplarOnSucceeded, recordExemplarOnErrored, recordExemplarOnCanceled)
      )

      /** Record the result of provided [[cats.effect.kernel.Outcome]]
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
        * [[OutcomeRecorder.fromGauge]] for more details.
        *
        * @param outcome
        *   the [[cats.effect.kernel.Outcome]] to be recorded
        * @param labels
        *   labels to add to the underlying metric
        */
      final def recordOutcome[B, E](
          outcome: Outcome[F, E, B],
          labels: A,
          recordExemplarOnSucceeded: Boolean = false,
          recordExemplarOnErrored: Boolean = false,
          recordExemplarOnCanceled: Boolean = false
      )(implicit F: FlatMap[F], exemplar: prometheus4cats.Exemplar[F]): F[Unit] =
        outcome match {
          case Outcome.Succeeded(_) =>
            if (recordExemplarOnSucceeded) exemplar.get.flatMap(recorder.onSucceeded(labels, _))
            else recorder.onSucceeded(labels, None)
          case Outcome.Errored(_) =>
            if (recordExemplarOnErrored) exemplar.get.flatMap(recorder.onErrored(labels, _))
            else recorder.onErrored(labels, None)
          case Outcome.Canceled() =>
            if (recordExemplarOnCanceled) exemplar.get.flatMap(recorder.onCanceled(labels, _))
            else recorder.onCanceled(labels, None)
        }

      /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]],
        * computing additional labels from the result.
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
        * [[OutcomeRecorder.fromGauge]] for more details.
        *
        * @param fb
        *   operation to be evaluated
        * @param labelsCanceled
        *   labels to add when the operation is canceled
        * @param labelsSuccess
        *   function to compute labels from the result of `fb` when the operation is successful
        * @param labelsError
        *   function to compute labels from the exception that was raised if the operation is unsuccessful
        */
      final def surroundWithComputedLabels[B](
          fb: F[B],
          labelsCanceled: A,
          recordExemplarOnSucceeded: Boolean = false,
          recordExemplarOnErrored: Boolean = false,
          recordExemplarOnCanceled: Boolean = false
      )(labelsSuccess: B => A, labelsError: Throwable => A)(implicit
          F: MonadCancelThrow[F],
          exemplar: prometheus4cats.Exemplar[F]
      ): F[B] =
        fb.guaranteeCase(
          recordOutcomeWithComputedLabels(
            _,
            labelsCanceled,
            recordExemplarOnSucceeded,
            recordExemplarOnErrored,
            recordExemplarOnCanceled
          )(labelsSuccess, labelsError)
        )

      /** Record the result of provided [[cats.effect.kernel.Outcome]] computing additional labels from the result.
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
        * [[OutcomeRecorder.fromGauge]] for more details.
        *
        * @param outcome
        *   the [[cats.effect.kernel.Outcome]] to be recorded
        * @param labelsCanceled
        *   labels to add when the operation is canceled
        * @param labelsSuccess
        *   function to compute labels from the result of `fb` when the operation is successful
        * @param labelsError
        *   function to compute labels from the exception that was raised if the operation is unsuccessful
        */
      final def recordOutcomeWithComputedLabels[B, E](
          outcome: Outcome[F, E, B],
          labelsCanceled: A,
          recordExemplarOnSucceeded: Boolean = false,
          recordExemplarOnErrored: Boolean = false,
          recordExemplarOnCanceled: Boolean = false
      )(labelsSuccess: B => A, labelsError: E => A)(implicit
          F: FlatMap[F],
          exemplar: prometheus4cats.Exemplar[F]
      ): F[Unit] =
        outcome match {
          case Outcome.Succeeded(fb) =>
            fb.flatMap(b =>
              if (recordExemplarOnSucceeded) exemplar.get.flatMap(recorder.onSucceeded(labelsSuccess(b), _))
              else recorder.onSucceeded(labelsSuccess(b), None)
            )
          case Outcome.Errored(th) =>
            if (recordExemplarOnErrored) exemplar.get.flatMap(recorder.onErrored(labelsError(th), _))
            else recorder.onErrored(labelsError(th), None)
          case Outcome.Canceled() =>
            if (recordExemplarOnCanceled) exemplar.get.flatMap(recorder.onCanceled(labelsCanceled, _))
            else recorder.onCanceled(labelsCanceled, None)
        }

    }

    implicit def labelsContravariant[F[_]]: LabelsContravariant[OutcomeRecorder.Exemplar[F, *]] =
      new LabelsContravariant[OutcomeRecorder.Exemplar[F, *]] {
        override def contramapLabels[A, B](fa: OutcomeRecorder.Exemplar[F, A])(
            f: B => A
        ): OutcomeRecorder.Exemplar[F, B] =
          fa.contramapLabels(f)
      }

    /** Create an [[OutcomeRecorder]] from a [[Counter]] instance, where its labels type is a tuple of the original
      * labels of the counter and [[Status]].
      *
      * This works by incrementing a counter with a label value that corresponds to the value [[Status]] on each
      * invocation.
      *
      * The best way to construct a counter based [[OutcomeRecorder]] is to use the `.asOutcomeRecorder` on the counter
      * DSL provided by [[MetricFactory]].
      *
      * @return
      *   an [[OutcomeRecorder.Aux]] that is annotated with the type of underlying metric, in this case [[Counter]]
      */
    def fromCounter[F[_], A, B](
        counter: Counter[F, A, (B, Status)]
    ): OutcomeRecorder.Exemplar.Aux[F, A, B, Counter] = new OutcomeRecorder.Exemplar[F, B] {
      override type Metric = Counter[F, A, (B, Status)]

      override protected def onCanceled(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incWithExemplar((labels, Status.Canceled), exemplar)

      override protected def onErrored(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incWithExemplar((labels, Status.Errored), exemplar)

      override protected def onSucceeded(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incWithExemplar((labels, Status.Succeeded), exemplar)
    }
  }

}
