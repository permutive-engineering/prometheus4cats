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

final class MetricDsl[F[_], A, M[_[_], _], L[_[_], _, _]] private[openmetrics4s] (
    makeMetric: F[M[F, A]],
    makeLabelledMetric: LabelledMetricPartiallyApplied[F, A, L]
) extends BuildStep[F, M[F, A]](makeMetric)
    with FirstLabelStep[F, A, L]
    with UnsafeLabelsStep[F, A, L] {

  /** @inheritdoc
    */
  override def label[B]: FirstLabelApply[F, A, B, L] =
    (name, toString) =>
      new LabelledMetricDsl(
        makeLabelledMetric,
        Sized(name),
        a => Sized(toString(a))
      )

  override def unsafeLabels(
      labelNames: IndexedSeq[Label.Name]
  ): BuildStep[F, L[F, A, Map[Label.Name, String]]] =
    new BuildStep[F, L[F, A, Map[Label.Name, String]]](
      makeLabelledMetric(
        labelNames
      )(labels => labelNames.flatMap(labels.get))
    )
}
