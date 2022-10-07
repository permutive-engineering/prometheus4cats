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

package openmetrics4s.internal

import openmetrics4s._

final class LabelledMetricDsl[F[_], A, T, N <: Nat, L[_[_], _, _]] private[internal] (
    makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L],
    labelNames: Sized[IndexedSeq[Label.Name], N],
    f: T => Sized[IndexedSeq[String], N]
) extends BuildStep[F, L[F, A, T]](
      makeLabelledMetric(labelNames.unsized)(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t).unsized
      )
    )
    with NextLabelsStep[F, A, T, N, L] {

  /** @inheritdoc
    */
  override def label[B]: LabelApply[F, A, T, N, B, L] =
    new LabelApply[F, A, T, N, B, L] {

      override def apply[C: InitLast.Aux[T, B, *]](
          name: Label.Name,
          toString: B => String
      ): LabelledMetricDsl[F, A, C, Succ[N], L] = new LabelledMetricDsl(
        makeLabelledMetric,
        labelNames :+ name,
        c => f(InitLast[T, B, C].init(c)) :+ toString(InitLast[T, B, C].last(c))
      )

    }

}
