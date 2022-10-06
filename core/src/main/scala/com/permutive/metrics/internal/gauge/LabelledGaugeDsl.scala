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

package com.permutive.metrics.internal.gauge

import com.permutive.metrics._
import com.permutive.metrics.internal._

final class LabelledGaugeDsl[F[_], T, N <: Nat] private[gauge] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    metric: Gauge.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels,
    labelNames: Sized[IndexedSeq[Label.Name], N],
    f: T => Sized[IndexedSeq[String], N]
) extends BuildStep[F, Gauge.Labelled[F, T]](
      registry.createAndRegisterLabelledGauge(
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
    with NextLabelsStep[F, T, N, LabelledGaugeDsl] {

  /** @inheritdoc
    */
  override def label[B]: LabelApply[F, T, N, LabelledGaugeDsl, B] =
    new LabelApply[F, T, N, LabelledGaugeDsl, B] {

      override def apply[C: InitLast.Aux[T, B, *]](
          name: Label.Name,
          toString: B => String
      ): LabelledGaugeDsl[F, C, Succ[N]] = new LabelledGaugeDsl(
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
