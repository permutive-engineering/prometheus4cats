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

package openmetrics4s.internal.histogram

import cats.data.NonEmptySeq
import openmetrics4s._
import openmetrics4s.internal.{BuildStep, FirstLabelApply, FirstLabelStep, UnsafeLabelsStep}

final class HistogramDsl[F[_]] private[openmetrics4s] (
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    metric: Histogram.Name,
    help: Metric.Help,
    commonLabels: Metric.CommonLabels,
    buckets: NonEmptySeq[Double]
) extends BuildStep[F, Histogram[F]](
      registry
        .createAndRegisterHistogram(
          prefix,
          metric,
          help,
          commonLabels,
          buckets
        )
    )
    with FirstLabelStep[F, LabelledHistogramDsl]
    with UnsafeLabelsStep[F, Histogram.Labelled] {

  /** @inheritdoc
    */
  override def label[A]: FirstLabelApply[F, LabelledHistogramDsl, A] =
    (name, toString) =>
      new LabelledHistogramDsl(
        registry,
        prefix,
        metric,
        help,
        commonLabels,
        Sized(name),
        buckets,
        a => Sized(toString(a))
      )

  override def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, Histogram.Labelled[F, Map[Label.Name, String]]] =
    new BuildStep[F, Histogram.Labelled[F, Map[Label.Name, String]]](
      registry.createAndRegisterLabelledHistogram(
        prefix,
        metric,
        help,
        commonLabels,
        labelNames,
        buckets
      )(labels => labelNames.flatMap(labels.get))
    )

}
