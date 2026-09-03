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

import io.prometheus.metrics.model.registry.Collector
import prometheus4cats.javaclient.models.MetricType
import prometheus4cats.util.NameUtils

package object javaclient {

  private[javaclient] type StateKey = (Option[Metric.Prefix], String)

  private[javaclient] type MetricID = (IndexedSeq[Label.Name], MetricType)

  /** State entry holding the object that was registered with the `PrometheusRegistry` — the metric itself, or the
    * `EvictingCollector` wrapping it when stale-series eviction is enabled — so release can unregister the same object.
    */
  private[javaclient] type StateValue[F[_]] =
    (MetricID, (Collector, Ref[F, Option[Exemplar.Data]], Int))

  private[javaclient] type State[F[_]] = Map[StateKey, StateValue[F]]

  private[javaclient] val duplicateShow: Show[(Option[Metric.Prefix], String)] = Show.show { case (prefix, name) =>
    NameUtils.makeName(prefix, name)
  }

}
