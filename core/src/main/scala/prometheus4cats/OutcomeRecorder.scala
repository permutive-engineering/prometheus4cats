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
import cats.effect.kernel.{Clock, MonadCancelThrow, Outcome}
import cats.syntax.flatMap._
import cats.{FlatMap, Monad, Show, ~>}
import prometheus4cats.internal.Neq

/** A derived metric type that records the outcome of an operation. See [[OutcomeRecorder.fromCounter]] and
  * [[OutcomeRecorder.fromGauge]] for more information.
  */
sealed abstract class OutcomeRecorder[F[_], -A](
    protected val exemplarState: Counter.ExemplarState[F]
) extends Metric.Labelled[A] { self =>
  type Metric

  protected def onCanceled(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

  protected def onErrored(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

  protected def onSucceeded(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

  def contramapLabels[B](f: B => A): OutcomeRecorder[F, B] =
    new OutcomeRecorder[F, B](exemplarState) {
      override protected def onCanceled(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        self.onCanceled(f(labels), exemplar)

      override protected def onErrored(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        self.onErrored(f(labels), exemplar)

      override protected def onSucceeded(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        self.onSucceeded(f(labels), exemplar)
    }

  def mapK[G[_]](fk: F ~> G): OutcomeRecorder[G, A] =
    new OutcomeRecorder[G, A](exemplarState.mapK(fk)) {
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
        case Outcome.Succeeded(_) => recorder.onSucceeded((), None)
        case Outcome.Errored(_) => recorder.onErrored((), None)
        case Outcome.Canceled() => recorder.onCanceled((), None)
      }
  }

  implicit class ExemplarOutcomeRecorderSyntax[F[_], A](recorder: OutcomeRecorder.Aux[F, A, Unit, Counter]) {

    /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param fb
      *   operation to be evaluated
      */
    final def surroundWithExemplar[B](
        fb: F[B],
        recordExemplarOnSucceeded: Boolean = false,
        recordExemplarOnErrored: Boolean = false,
        recordExemplarOnCanceled: Boolean = false
    )(implicit F: MonadCancelThrow[F], exemplar: prometheus4cats.Exemplar[F]): F[B] = fb.guaranteeCase(
      recordWithExemplarOutcome(_, recordExemplarOnSucceeded, recordExemplarOnErrored, recordExemplarOnCanceled)
    )

    final def surroundWithSampledExemplar[B](
        fb: F[B]
    )(implicit F: MonadCancelThrow[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[B] =
      fb.guaranteeCase(recordWithSampledExemplar)

    final def surroundProvidedExemplar[C](
        fb: F[C],
        exemplarOnSucceeded: Option[Exemplar.Labels] = None,
        exemplarOnErrored: Option[Exemplar.Labels] = None,
        exemplarOnCanceled: Option[Exemplar.Labels] = None
    )(implicit F: MonadCancelThrow[F]): F[C] = fb.guaranteeCase(
      recordOutcomeProvidedExemplar(_, exemplarOnSucceeded, exemplarOnErrored, exemplarOnCanceled)
    )

    /** Record the result of provided [[cats.effect.kernel.Outcome]]
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param outcome
      *   the [[cats.effect.kernel.Outcome]] to be recorded
      */
    final def recordWithExemplarOutcome[B, E](
        outcome: Outcome[F, E, B],
        recordExemplarOnSucceeded: Boolean = false,
        recordExemplarOnErrored: Boolean = false,
        recordExemplarOnCanceled: Boolean = false
    )(implicit F: FlatMap[F], exemplar: prometheus4cats.Exemplar[F]): F[Unit] = exemplar.get.flatMap(ex =>
      recordOutcomeProvidedExemplar(
        outcome,
        ex.filter(_ => recordExemplarOnSucceeded),
        ex.filter(_ => recordExemplarOnErrored),
        ex.filter(_ => recordExemplarOnCanceled)
      )
    )

    final def recordWithSampledExemplar[B, E](
        outcome: Outcome[F, E, B]
    )(implicit F: Monad[F], clock: Clock[F], exemplarSampler: ExemplarSampler.Counter[F, A]): F[Unit] =
      recorder.exemplarState.surround(exemplar => recordOutcomeProvidedExemplar(outcome, exemplar, exemplar, exemplar))

    final def recordOutcomeProvidedExemplar[C, E](
        outcome: Outcome[F, E, C],
        exemplarOnSucceeded: Option[Exemplar.Labels] = None,
        exemplarOnErrored: Option[Exemplar.Labels] = None,
        exemplarOnCanceled: Option[Exemplar.Labels] = None
    ): F[Unit] =
      outcome match {
        case Outcome.Succeeded(_) =>
          recorder.onSucceeded((), exemplarOnSucceeded)
        case Outcome.Errored(_) =>
          recorder.onErrored((), exemplarOnErrored)
        case Outcome.Canceled() =>
          recorder.onCanceled((), exemplarOnCanceled)
      }
  }

  implicit class LabelledOutcomeRecorder[F[_], -A](recorder: OutcomeRecorder[F, A])(implicit ev: Unit Neq A) {

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
        case Outcome.Succeeded(_) => recorder.onSucceeded(labels, None)
        case Outcome.Errored(_) => recorder.onErrored(labels, None)
        case Outcome.Canceled() => recorder.onCanceled(labels, None)
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
        case Outcome.Succeeded(fb) => fb.flatMap(b => recorder.onSucceeded(labelsSuccess(b), None))
        case Outcome.Errored(th) => recorder.onErrored(labelsError(th), None)
        case Outcome.Canceled() => recorder.onCanceled(labelsCanceled, None)
      }

  }

  implicit class ExemplarLabelledOutcomeRecorderSyntax[F[_], -A, B](recorder: OutcomeRecorder.Aux[F, B, A, Counter])(
      implicit ev: Unit Neq A
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
    final def surroundWithExemplar[C](
        fb: F[C],
        labels: A,
        recordExemplarOnSucceeded: Boolean = false,
        recordExemplarOnErrored: Boolean = false,
        recordExemplarOnCanceled: Boolean = false
    )(implicit F: MonadCancelThrow[F], exemplar: prometheus4cats.Exemplar[F]): F[C] = fb.guaranteeCase(
      recordOutcomeWithExemplar(_, labels, recordExemplarOnSucceeded, recordExemplarOnErrored, recordExemplarOnCanceled)
    )

    final def surroundWithSampledExemplar[C](fb: F[C], labels: A)(implicit
        F: MonadCancelThrow[F],
        clock: Clock[F],
        exemplarSampler: ExemplarSampler.Counter[F, B]
    ): F[C] = fb.guaranteeCase(recordOutcomeWithSampledExemplar(_, labels))

    final def surroundProvidedExemplar[C](
        fb: F[C],
        labels: A,
        exemplarOnSucceeded: Option[Exemplar.Labels] = None,
        exemplarOnErrored: Option[Exemplar.Labels] = None,
        exemplarOnCanceled: Option[Exemplar.Labels] = None
    )(implicit F: MonadCancelThrow[F]): F[C] = fb.guaranteeCase(
      recordOutcomeProvidedExemplar(_, labels, exemplarOnSucceeded, exemplarOnErrored, exemplarOnCanceled)
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
    final def recordOutcomeWithExemplar[C, E](
        outcome: Outcome[F, E, C],
        labels: A,
        recordExemplarOnSucceeded: Boolean = false,
        recordExemplarOnErrored: Boolean = false,
        recordExemplarOnCanceled: Boolean = false
    )(implicit F: FlatMap[F], exemplar: prometheus4cats.Exemplar[F]): F[Unit] = exemplar.get.flatMap { ex =>
      recordOutcomeProvidedExemplar(
        outcome,
        labels,
        ex.filter(_ => recordExemplarOnSucceeded),
        ex.filter(_ => recordExemplarOnErrored),
        ex.filter(_ => recordExemplarOnCanceled)
      )
    }

    final def recordOutcomeWithSampledExemplar[C, E](
        outcome: Outcome[F, E, C],
        labels: A
    )(implicit
        F: Monad[F],
        clock: Clock[F],
        exemplarSampler: ExemplarSampler.Counter[F, B]
    ): F[Unit] =
      recorder.exemplarState.surround(next => recordOutcomeProvidedExemplar(outcome, labels, next, next, next))

    final def recordOutcomeProvidedExemplar[C, E](
        outcome: Outcome[F, E, C],
        labels: A,
        exemplarOnSucceeded: Option[Exemplar.Labels] = None,
        exemplarOnErrored: Option[Exemplar.Labels] = None,
        exemplarOnCanceled: Option[Exemplar.Labels] = None
    ): F[Unit] =
      outcome match {
        case Outcome.Succeeded(_) =>
          recorder.onSucceeded(labels, exemplarOnSucceeded)
        case Outcome.Errored(_) =>
          recorder.onErrored(labels, exemplarOnErrored)
        case Outcome.Canceled() =>
          recorder.onCanceled(labels, exemplarOnCanceled)
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
    final def surroundWithComputedLabelsWithExemplar[C](
        fb: F[C],
        labelsCanceled: A,
        recordExemplarOnSucceeded: Boolean = false,
        recordExemplarOnErrored: Boolean = false,
        recordExemplarOnCanceled: Boolean = false
    )(labelsSuccess: C => A, labelsError: Throwable => A)(implicit
        F: MonadCancelThrow[F],
        exemplar: prometheus4cats.Exemplar[F]
    ): F[C] =
      fb.guaranteeCase(
        recordOutcomeWithComputedLabelsWithExemplar(
          _,
          labelsCanceled,
          recordExemplarOnSucceeded,
          recordExemplarOnErrored,
          recordExemplarOnCanceled
        )(labelsSuccess, labelsError)
      )

    final def surroundWithComputedLabelsWithSampledExemplar[C](
        fb: F[C],
        labelsCanceled: A
    )(labelsSuccess: C => A, labelsError: Throwable => A)(implicit
        F: MonadCancelThrow[F],
        clock: Clock[F],
        exemplarSampler: ExemplarSampler.Counter[F, B]
    ): F[C] =
      fb.guaranteeCase(
        recordOutcomeWithComputedLabelsWithExemplar(_, labelsCanceled)(labelsSuccess, labelsError)
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
    final def recordOutcomeWithComputedLabelsWithExemplar[C, E](
        outcome: Outcome[F, E, C],
        labelsCanceled: A,
        recordExemplarOnSucceeded: Boolean = false,
        recordExemplarOnErrored: Boolean = false,
        recordExemplarOnCanceled: Boolean = false
    )(labelsSuccess: C => A, labelsError: E => A)(implicit
        F: FlatMap[F],
        exemplar: prometheus4cats.Exemplar[F]
    ): F[Unit] =
      exemplar.get.flatMap(ex =>
        recordOutcomeWithComputedLabelsProvidedExemplar(
          outcome,
          labelsCanceled,
          ex.filter(_ => recordExemplarOnSucceeded),
          ex.filter(_ => recordExemplarOnErrored),
          ex.filter(_ => recordExemplarOnCanceled)
        )(labelsSuccess, labelsError)
      )

    final def recordOutcomeWithComputedLabelsWithExemplar[C, E](
        outcome: Outcome[F, E, C],
        labelsCanceled: A
    )(labelsSuccess: C => A, labelsError: E => A)(implicit
        F: Monad[F],
        clock: Clock[F],
        exemplarSampler: ExemplarSampler.Counter[F, B]
    ): F[Unit] =
      recorder.exemplarState
        .surround(exemplar =>
          recordOutcomeWithComputedLabelsProvidedExemplar(outcome, labelsCanceled, exemplar, exemplar, exemplar)(
            labelsSuccess,
            labelsError
          )
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
    final def recordOutcomeWithComputedLabelsProvidedExemplar[C, E](
        outcome: Outcome[F, E, C],
        labelsCanceled: A,
        exemplarOnSucceeded: Option[Exemplar.Labels] = None,
        exemplarOnErrored: Option[Exemplar.Labels] = None,
        exemplarOnCanceled: Option[Exemplar.Labels] = None
    )(labelsSuccess: C => A, labelsError: E => A)(implicit F: FlatMap[F]): F[Unit] =
      outcome match {
        case Outcome.Succeeded(fb) =>
          fb.flatMap(b => recorder.onSucceeded(labelsSuccess(b), exemplarOnSucceeded))
        case Outcome.Errored(th) =>
          recorder.onErrored(labelsError(th), exemplarOnErrored)
        case Outcome.Canceled() =>
          recorder.onCanceled(labelsCanceled, exemplarOnCanceled)
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
  ): OutcomeRecorder.Aux[F, A, B, Counter] =
    new OutcomeRecorder[F, B](counter.exemplarState) {
      override type Metric = Counter[F, A, (B, Status)]

      override protected def onCanceled(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incProvidedExemplar((labels, Status.Canceled), exemplar)

      override protected def onErrored(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incProvidedExemplar((labels, Status.Errored), exemplar)

      override protected def onSucceeded(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incProvidedExemplar((labels, Status.Succeeded), exemplar)
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
    new OutcomeRecorder[F, B](Counter.ExemplarState.noop) {
      override type Metric = Gauge[F, A, (B, Status)]

      private def setOutcome(labels: B, status: Status): F[Unit] =
        (
          gauge.reset(labels -> Status.Succeeded) >> gauge.reset(labels -> Status.Canceled) >>
            gauge.reset(labels -> Status.Errored) >> gauge.inc(labels -> status)
        ).uncancelable

      override protected def onCanceled(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        setOutcome(labels, Status.Canceled)

      override protected def onErrored(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        setOutcome(labels, Status.Errored)

      override protected def onSucceeded(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        setOutcome(labels, Status.Succeeded)
    }

}
