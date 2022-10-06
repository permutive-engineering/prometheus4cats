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

final class GaugeDsl[F[_]] private[metrics] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    metric: Gauge.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels
) extends BuildStep[F, Gauge[F]](
      registry
        .createAndRegisterGauge(prefix, metric, help, commonLabels)
    )
    with FirstLabelStep[F, LabelledGaugeDsl]
    with UnsafeLabelsStep[F, Gauge.Labelled] {

  /** @inheritdoc
    */
  override def label[A]: FirstLabelApply[F, LabelledGaugeDsl, A] =
    (name, toString) =>
      new LabelledGaugeDsl(
        registry,
        prefix,
        metric,
        help,
        commonLabels,
        Sized(name),
        a => Sized(toString(a))
      )

  def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, Gauge.Labelled[F, Map[Label.Name, String]]] =
    new BuildStep[F, Gauge.Labelled[F, Map[Label.Name, String]]](
      registry.createAndRegisterLabelledGauge(
        prefix,
        metric,
        help,
        commonLabels,
        labelNames
      )(labels => labelNames.flatMap(labels.get))
    )

}
