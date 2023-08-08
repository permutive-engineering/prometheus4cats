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
import cats.{Show, ~>}

/** A derived metric type that records the outcome of an operation. See [[OutcomeRecorder.fromCounter]] and
  * [[OutcomeRecorder.fromGauge]] for more information.
  */
sealed abstract class OutcomeRecorder[F[_]: MonadCancelThrow] { self =>
  type Metric

  /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
    *
    * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
    * [[OutcomeRecorder.fromGauge]] for more details.
    *
    * @param fa
    *   operation to be evaluated
    */
  final def surround[A](fa: F[A]): F[A] = fa.guaranteeCase(recordOutcome[A, Throwable])

  /** Record the result of provided [[cats.effect.kernel.Outcome]]
    *
    * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
    * [[OutcomeRecorder.fromGauge]] for more details.
    *
    * @param outcome
    *   the [[cats.effect.kernel.Outcome]] to be recorded
    */
  final def recordOutcome[A, E](outcome: Outcome[F, E, A]): F[Unit] = outcome match {
    case Outcome.Succeeded(_) => onSucceeded
    case Outcome.Errored(_) => onErrored
    case Outcome.Canceled() => onCanceled
  }

  protected def onCanceled: F[Unit]

  protected def onErrored: F[Unit]

  protected def onSucceeded: F[Unit]

  def mapK[G[_]: MonadCancelThrow](fk: F ~> G): OutcomeRecorder[G] = new OutcomeRecorder[G] {
    override type Metric = self.Metric

    override protected def onCanceled: G[Unit] = fk(self.onCanceled)

    override protected def onErrored: G[Unit] = fk(self.onErrored)

    override protected def onSucceeded: G[Unit] = fk(self.onSucceeded)
  }
}

object OutcomeRecorder {

  type Aux[F[_], A, M[_[_], _, _]] = OutcomeRecorder[F] {
    type Metric = M[F, A, Status]
  }

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

  /** Create an [[OutcomeRecorder]] from a [[Counter.Labelled]] instance, where its only label type is [[Status]].
    *
    * This works by incrementing a counter with a label value that corresponds to the value [[Status]] on each
    * invocation.
    *
    * The best way to construct a counter based [[OutcomeRecorder]] is to use the `.asOutcomeRecorder` on the counter
    * DSL provided by [[MetricFactory]].
    *
    * @return
    *   an [[OutcomeRecorder.Aux]] that is annotated with the type of underlying metric, in this case
    *   [[Counter.Labelled]]
    */
  def fromCounter[F[_]: MonadCancelThrow, A](
      counter: Counter.Labelled[F, A, Status]
  ): OutcomeRecorder.Aux[F, A, Counter.Labelled] = new OutcomeRecorder[F] {
    override type Metric = Counter.Labelled[F, A, Status]

    override protected val onCanceled: F[Unit] = counter.inc(Status.Canceled)

    override protected val onErrored: F[Unit] = counter.inc(Status.Errored)

    override protected val onSucceeded: F[Unit] = counter.inc(Status.Succeeded)
  }

  /** Create an [[OutcomeRecorder]] from a [[Gauge.Labelled]] instance, where its only label type is [[Status]].
    *
    * This works by setting gauge with a label value that corresponds to the value of [[Status]] to `1` on each
    * invocation, while the other statuses are set to `0`.
    *
    * The best way to construct a gauge based [[OutcomeRecorder]] is to use the `.asOutcomeRecorder` on the gauge DSL
    * provided by [[MetricFactory]].
    *
    * @return
    *   an [[OutcomeRecorder.Aux]] that is annotated with the type of underlying metric, in this case [[Gauge.Labelled]]
    */
  def fromGauge[F[_]: MonadCancelThrow, A](
      gauge: Gauge.Labelled[F, A, Status]
  ): OutcomeRecorder.Aux[F, A, Gauge.Labelled] =
    new OutcomeRecorder[F] {
      override type Metric = Gauge.Labelled[F, A, Status]

      private def setOutcome(status: Status): F[Unit] =
        (
          gauge.reset(Status.Succeeded) >> gauge.reset(Status.Canceled) >> gauge.reset(Status.Errored) >>
            gauge.inc(status)
        ).uncancelable

      override protected val onCanceled: F[Unit] = setOutcome(Status.Canceled)

      override protected val onErrored: F[Unit] = setOutcome(Status.Errored)

      override protected val onSucceeded: F[Unit] = setOutcome(Status.Succeeded)
    }

  sealed abstract class Exemplar[F[_]: MonadCancelThrow] {
    self =>
    type Metric

    /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Exemplar.fromCounter]] and
      *
      * @param fa
      *   operation to be evaluated
      */
    final def surround[A](
        fa: F[A],
        recordExemplarOnSucceeded: Boolean = false,
        recordExemplarOnErrored: Boolean = false,
        recordExemplarOnCanceled: Boolean = false
    )(implicit exemplar: prometheus4cats.Exemplar[F]): F[A] =
      fa.guaranteeCase(
        recordOutcome[A, Throwable](_, recordExemplarOnSucceeded, recordExemplarOnErrored, recordExemplarOnCanceled)
      )

    final def surroundWithExemplar[A](
        fa: F[A],
        exemplarOnSucceeded: Option[prometheus4cats.Exemplar.Labels] = None,
        exemplarOnErrored: Option[prometheus4cats.Exemplar.Labels] = None,
        exemplarOnCanceled: Option[prometheus4cats.Exemplar.Labels] = None
    ): F[A] =
      fa.guaranteeCase(
        recordOutcomeWithExemplar[A, Throwable](_, exemplarOnSucceeded, exemplarOnErrored, exemplarOnCanceled)
      )

    /** Record the result of provided [[cats.effect.kernel.Outcome]]
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.fromCounter]] and
      * [[OutcomeRecorder.fromGauge]] for more details.
      *
      * @param outcome
      *   the [[cats.effect.kernel.Outcome]] to be recorded
      */
    final def recordOutcome[A, E](
        outcome: Outcome[F, E, A],
        recordExemplarOnSucceeded: Boolean = false,
        recordExemplarOnErrored: Boolean = false,
        recordExemplarOnCanceled: Boolean = false
    )(implicit exemplar: prometheus4cats.Exemplar[F]): F[Unit] = outcome match {
      case Outcome.Succeeded(_) =>
        if (recordExemplarOnSucceeded) exemplar.get.flatMap(onSucceeded) else onSucceeded(None)
      case Outcome.Errored(_) =>
        if (recordExemplarOnErrored) exemplar.get.flatMap(onErrored) else onErrored(None)
      case Outcome.Canceled() =>
        if (recordExemplarOnCanceled) exemplar.get.flatMap(onCanceled) else onCanceled(None)
    }

    final def recordOutcomeWithExemplar[A, E](
        outcome: Outcome[F, E, A],
        exemplarOnSucceeded: Option[prometheus4cats.Exemplar.Labels] = None,
        exemplarOnErrored: Option[prometheus4cats.Exemplar.Labels] = None,
        exemplarOnCanceled: Option[prometheus4cats.Exemplar.Labels] = None
    ): F[Unit] = outcome match {
      case Outcome.Succeeded(_) =>
        onSucceeded(exemplarOnSucceeded)
      case Outcome.Errored(_) =>
        onErrored(exemplarOnErrored)
      case Outcome.Canceled() =>
        onCanceled(exemplarOnCanceled)
    }

    protected def onCanceled(exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

    protected def onErrored(exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

    protected def onSucceeded(exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

    def mapK[G[_]: MonadCancelThrow](fk: F ~> G): OutcomeRecorder.Exemplar[G] = new OutcomeRecorder.Exemplar[G] {
      override type Metric = self.Metric

      override protected def onCanceled(exemplar: Option[prometheus4cats.Exemplar.Labels]): G[Unit] = fk(
        self.onCanceled(exemplar)
      )

      override protected def onErrored(exemplar: Option[prometheus4cats.Exemplar.Labels]): G[Unit] = fk(
        self.onErrored(exemplar)
      )

      override protected def onSucceeded(exemplar: Option[prometheus4cats.Exemplar.Labels]): G[Unit] = fk(
        self.onSucceeded(exemplar)
      )
    }
  }
  object Exemplar {
    type Aux[F[_], A, M[_[_], _, _]] = OutcomeRecorder.Exemplar[F] {
      type Metric = M[F, A, Status]
    }

    /** Create an [[OutcomeRecorder]] from a [[Counter.Labelled]] instance, where its only label type is [[Status]].
      *
      * This works by incrementing a counter with a label value that corresponds to the value [[Status]] on each
      * invocation.
      *
      * The best way to construct a counter based [[OutcomeRecorder]] is to use the `.asOutcomeRecorder` on the counter
      * DSL provided by [[MetricFactory]].
      *
      * @return
      *   an [[OutcomeRecorder.Aux]] that is annotated with the type of underlying metric, in this case
      *   [[Counter.Labelled]]
      */
    def fromCounter[F[_]: MonadCancelThrow, A](
        counter: Counter.Labelled[F, A, Status]
    ): OutcomeRecorder.Exemplar.Aux[F, A, Counter.Labelled] = new OutcomeRecorder.Exemplar[F] {
      override type Metric = Counter.Labelled[F, A, Status]

      override protected def onCanceled(exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incWithExemplar(Status.Canceled, exemplar)

      override protected def onErrored(exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incWithExemplar(Status.Errored, exemplar)

      override protected def onSucceeded(exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
        counter.incWithExemplar(Status.Succeeded, exemplar)
    }
  }

  /** A derived metric type that records the outcome of an operation. See [[OutcomeRecorder.Labelled.fromCounter]] and
    * [[OutcomeRecorder.Labelled.fromGauge]] for more information.
    */
  sealed abstract class Labelled[F[_]: MonadCancelThrow, -A] extends Metric.Labelled[A] { self =>
    type Metric

    /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Labelled.fromCounter]] and
      * [[OutcomeRecorder.Labelled.fromGauge]] for more details.
      *
      * @param fb
      *   operation to be evaluated
      * @param labels
      *   labels to add to the underlying metric
      */
    final def surround[B](fb: F[B], labels: A): F[B] = fb.guaranteeCase(recordOutcome(_, labels))

    /** Record the result of provided [[cats.effect.kernel.Outcome]]
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Labelled.fromCounter]] and
      * [[OutcomeRecorder.Labelled.fromGauge]] for more details.
      *
      * @param outcome
      *   the [[cats.effect.kernel.Outcome]] to be recorded
      * @param labels
      *   labels to add to the underlying metric
      */
    final def recordOutcome[B, E](outcome: Outcome[F, E, B], labels: A): F[Unit] =
      outcome match {
        case Outcome.Succeeded(_) => onSucceeded(labels)
        case Outcome.Errored(_) => onErrored(labels)
        case Outcome.Canceled() => onCanceled(labels)
      }

    /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]],
      * computing additional labels from the result.
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Labelled.fromCounter]] and
      * [[OutcomeRecorder.Labelled.fromGauge]] for more details.
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
    )(labelsSuccess: B => A, labelsError: Throwable => A): F[B] =
      fb.guaranteeCase(recordOutcomeWithComputedLabels(_, labelsCanceled)(labelsSuccess, labelsError))

    /** Record the result of provided [[cats.effect.kernel.Outcome]] computing additional labels from the result.
      *
      * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Labelled.fromCounter]] and
      * [[OutcomeRecorder.Labelled.fromGauge]] for more details.
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
    )(labelsSuccess: B => A, labelsError: E => A): F[Unit] =
      outcome match {
        case Outcome.Succeeded(fb) => fb.flatMap(b => onSucceeded(labelsSuccess(b)))
        case Outcome.Errored(th) => onErrored(labelsError(th))
        case Outcome.Canceled() => onCanceled(labelsCanceled)
      }

    protected def onCanceled(labels: A): F[Unit]

    protected def onErrored(labels: A): F[Unit]

    protected def onSucceeded(labels: A): F[Unit]

    def contramapLabels[B](f: B => A): Labelled[F, B] = new Labelled[F, B] {
      override type Metric = self.Metric

      override protected def onCanceled(labels: B): F[Unit] = self.onCanceled(f(labels))

      override protected def onErrored(labels: B): F[Unit] = self.onErrored(f(labels))

      override protected def onSucceeded(labels: B): F[Unit] = self.onSucceeded(f(labels))
    }

    def mapK[G[_]: MonadCancelThrow](fk: F ~> G): Labelled[G, A] = new Labelled[G, A] {
      override type Metric = self.Metric

      override protected def onCanceled(labels: A): G[Unit] = fk(self.onCanceled(labels))

      override protected def onErrored(labels: A): G[Unit] = fk(self.onErrored(labels))

      override protected def onSucceeded(labels: A): G[Unit] = fk(self.onSucceeded(labels))
    }
  }

  object Labelled {
    type Aux[F[_], A, B, M[_[_], _, _]] = OutcomeRecorder.Labelled[F, B] {
      type Metric = M[F, A, (B, Status)]
    }

    implicit def labelsContravariant[F[_]]: LabelsContravariant[Labelled[F, *]] =
      new LabelsContravariant[Labelled[F, *]] {
        override def contramapLabels[A, B](fa: Labelled[F, A])(f: B => A): Labelled[F, B] = fa.contramapLabels(f)
      }

    /** Create an [[OutcomeRecorder]] from a [[Counter.Labelled]] instance, where its labels type is a tuple of the
      * original labels of the counter and [[Status]].
      *
      * This works by incrementing a counter with a label value that corresponds to the value [[Status]] on each
      * invocation.
      *
      * The best way to construct a counter based [[OutcomeRecorder]] is to use the `.asOutcomeRecorder` on the counter
      * DSL provided by [[MetricFactory]].
      *
      * @return
      *   an [[OutcomeRecorder.Labelled.Aux]] that is annotated with the type of underlying metric, in this case
      *   [[Counter.Labelled]]
      */
    def fromCounter[F[_]: MonadCancelThrow, A, B](
        counter: Counter.Labelled[F, A, (B, Status)]
    ): OutcomeRecorder.Labelled.Aux[F, A, B, Counter.Labelled] = new OutcomeRecorder.Labelled[F, B] {
      override type Metric = Counter.Labelled[F, A, (B, Status)]

      override protected def onCanceled(labels: B): F[Unit] = counter.inc((labels, Status.Canceled))

      override protected def onErrored(labels: B): F[Unit] = counter.inc((labels, Status.Errored))

      override protected def onSucceeded(labels: B): F[Unit] = counter.inc((labels, Status.Succeeded))
    }

    /** Create an [[OutcomeRecorder]] from a [[Gauge.Labelled]] instance, where its only label type is a tuple of the
      * original labels of the counter and [[Status]].
      *
      * This works by setting gauge with a label value that corresponds to the value of [[Status]] to `1` on each
      * invocation, while the other statuses are set to `0`.
      *
      * The best way to construct a gauge based [[OutcomeRecorder]] is to use the `.asOutcomeRecorder` on the gauge DSL
      * provided by [[MetricFactory]].
      *
      * @return
      *   an [[OutcomeRecorder.Labelled.Aux]] that is annotated with the type of underlying metric, in this case
      *   [[Gauge.Labelled]]
      */
    def fromGauge[F[_]: MonadCancelThrow, A, B](
        gauge: Gauge.Labelled[F, A, (B, Status)]
    ): OutcomeRecorder.Labelled.Aux[F, A, B, Gauge.Labelled] =
      new OutcomeRecorder.Labelled[F, B] {
        override type Metric = Gauge.Labelled[F, A, (B, Status)]

        private def setOutcome(labels: B, status: Status): F[Unit] =
          (
            gauge.reset(labels -> Status.Succeeded) >> gauge.reset(labels -> Status.Canceled) >>
              gauge.reset(labels -> Status.Errored) >> gauge.inc(labels -> status)
          ).uncancelable

        override protected def onCanceled(labels: B): F[Unit] = setOutcome(labels, Status.Canceled)

        override protected def onErrored(labels: B): F[Unit] = setOutcome(labels, Status.Errored)

        override protected def onSucceeded(labels: B): F[Unit] = setOutcome(labels, Status.Succeeded)
      }

    /** A derived metric type that records the outcome of an operation. See [[OutcomeRecorder.Labelled.fromCounter]] and
      * [[OutcomeRecorder.Labelled.fromGauge]] for more information.
      */
    sealed abstract class Exemplar[F[_]: MonadCancelThrow, -A] extends Metric.Labelled[A] {
      self =>
      type Metric

      /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]].
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Labelled.fromCounter]]
        * and [[OutcomeRecorder.Labelled.fromGauge]] for more details.
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
      )(implicit exemplar: prometheus4cats.Exemplar[F]): F[B] = fb.guaranteeCase(
        recordOutcome(_, labels, recordExemplarOnSucceeded, recordExemplarOnErrored, recordExemplarOnCanceled)
      )

      /** Record the result of provided [[cats.effect.kernel.Outcome]]
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Labelled.fromCounter]]
        * and [[OutcomeRecorder.Labelled.fromGauge]] for more details.
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
      )(implicit exemplar: prometheus4cats.Exemplar[F]): F[Unit] =
        outcome match {
          case Outcome.Succeeded(_) =>
            if (recordExemplarOnSucceeded) exemplar.get.flatMap(onSucceeded(labels, _))
            else onSucceeded(labels, None)
          case Outcome.Errored(_) =>
            if (recordExemplarOnErrored) exemplar.get.flatMap(onErrored(labels, _))
            else onErrored(labels, None)
          case Outcome.Canceled() =>
            if (recordExemplarOnCanceled) exemplar.get.flatMap(onCanceled(labels, _))
            else onCanceled(labels, None)
        }

      /** Surround an operation and evaluate its outcome using an instance of [[cats.effect.kernel.MonadCancel]],
        * computing additional labels from the result.
        *
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Labelled.fromCounter]]
        * and [[OutcomeRecorder.Labelled.fromGauge]] for more details.
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
      )(labelsSuccess: B => A, labelsError: Throwable => A)(implicit exemplar: prometheus4cats.Exemplar[F]): F[B] =
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
        * The resulting metrics depend on the underlying implementation. See [[OutcomeRecorder.Labelled.fromCounter]]
        * and [[OutcomeRecorder.Labelled.fromGauge]] for more details.
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
      )(labelsSuccess: B => A, labelsError: E => A)(implicit exemplar: prometheus4cats.Exemplar[F]): F[Unit] =
        outcome match {
          case Outcome.Succeeded(fb) =>
            fb.flatMap(b =>
              if (recordExemplarOnSucceeded) exemplar.get.flatMap(onSucceeded(labelsSuccess(b), _))
              else onSucceeded(labelsSuccess(b), None)
            )
          case Outcome.Errored(th) =>
            if (recordExemplarOnErrored) exemplar.get.flatMap(onErrored(labelsError(th), _))
            else onErrored(labelsError(th), None)
          case Outcome.Canceled() =>
            if (recordExemplarOnCanceled) exemplar.get.flatMap(onCanceled(labelsCanceled, _))
            else onCanceled(labelsCanceled, None)
        }

      protected def onCanceled(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

      protected def onErrored(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

      protected def onSucceeded(labels: A, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit]

      def contramapLabels[B](f: B => A): Labelled.Exemplar[F, B] = new Labelled.Exemplar[F, B] {
        override type Metric = self.Metric

        override protected def onCanceled(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
          self.onCanceled(f(labels), exemplar)

        override protected def onErrored(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
          self.onErrored(f(labels), exemplar)

        override protected def onSucceeded(labels: B, exemplar: Option[prometheus4cats.Exemplar.Labels]): F[Unit] =
          self.onSucceeded(f(labels), exemplar)
      }

      def mapK[G[_]: MonadCancelThrow](fk: F ~> G): Labelled.Exemplar[G, A] = new Labelled.Exemplar[G, A] {
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
  }
}
