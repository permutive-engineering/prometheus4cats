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

package com.permutive.metrics.internal.counter

import com.permutive.metrics._
import com.permutive.metrics.internal._

final class LabelledCounterDsl[F[_], T, N <: Nat] private[counter] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    metric: Counter.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels,
    labelNames: Sized[IndexedSeq[Label.Name], N],
    f: T => Sized[IndexedSeq[String], N]
) extends BuildStep[F, Counter.Labelled[F, T]](
      registry.createAndRegisterLabelledCounter(
        prefix,
        metric,
        help,
        commonLabels,
        labelNames.unsized
      )(
        // avoid using andThen because it can be slow and this gets called repeatedly during runtime
        t => f(t).unsized
      )
    )
    with NextLabelsStep[F, T, N, LabelledCounterDsl] {

  /** @inheritdoc
    */
  override def label[B]: LabelApply[F, T, N, LabelledCounterDsl, B] =
    new LabelApply[F, T, N, LabelledCounterDsl, B] {

      override def apply[C: InitLast.Aux[T, B, *]](
          name: Label.Name,
          toString: B => String
      ): LabelledCounterDsl[F, C, Succ[N]] = new LabelledCounterDsl(
        registry,
        prefix,
        metric,
        help,
        commonLabels,
        labelNames :+ name,
        c => f(InitLast[T, B, C].init(c)) :+ toString(InitLast[T, B, C].last(c))
      )

    }

}
