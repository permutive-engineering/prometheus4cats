/*
 * Copyright 2022-2026 Permutive Ltd. <https://permutive.com>
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

import cats.Show
import cats.effect.kernel.Ref

import io.prometheus.metrics.core.metrics.MetricWithFixedMetadata
import prometheus4cats.javaclient.models.MetricType
import prometheus4cats.util.NameUtils

package object javaclient {

  private[javaclient] type StateKey = (Option[Metric.Prefix], String)

  private[javaclient] type MetricID = (IndexedSeq[Label.Name], MetricType)

  /** State entry bound by the most specific common parent of every upstream metric type we register —
    * `MetricWithFixedMetadata`. Counter, Gauge, Histogram, Summary all extend `StatefulMetric` which extends this; Info
    * extends this directly without going through `StatefulMetric`.
    */
  private[javaclient] type StateValue[F[_]] =
    (MetricID, (MetricWithFixedMetadata, Ref[F, Option[Exemplar.Data]], Int))

  private[javaclient] type State[F[_]] = Map[StateKey, StateValue[F]]

  private[javaclient] val duplicateShow: Show[(Option[Metric.Prefix], String)] = Show.show { case (prefix, name) =>
    NameUtils.makeName(prefix, name)
  }

}
