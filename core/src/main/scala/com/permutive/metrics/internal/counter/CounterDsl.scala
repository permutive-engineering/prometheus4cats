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

final class CounterDsl[F[_]] private[metrics] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    metric: Counter.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels
) extends BuildStep[F, Counter[F]](
      registry
        .createAndRegisterCounter(prefix, metric, help, commonLabels)
    )
    with FirstLabelStep[F, LabelledCounterDsl]
    with UnsafeLabelsStep[F, Counter.Labelled] {

  /** @inheritdoc
    */
  override def label[A]: FirstLabelApply[F, LabelledCounterDsl, A] =
    (name, toString) =>
      new LabelledCounterDsl(
        registry,
        prefix,
        metric,
        help,
        commonLabels,
        Sized(name),
        a => Sized(toString(a))
      )

  override def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, Counter.Labelled[F, Map[Label.Name, String]]] =
    new BuildStep[F, Counter.Labelled[F, Map[Label.Name, String]]](
      registry.createAndRegisterLabelledCounter(
        prefix,
        metric,
        help,
        commonLabels,
        labelNames
      )(labels => labelNames.flatMap(labels.get))
    )
}
