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
import cats.data.NonEmptyList
import cats.effect.kernel.Ref
import cats.effect.kernel.Unique

import io.prometheus.metrics.core.metrics.MetricWithFixedMetadata
import io.prometheus.metrics.model.registry.Collector
import io.prometheus.metrics.model.snapshots.DataPointSnapshot
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

  /** Per-metric-name callback registry state. Each registered metric name has exactly one upstream `Collector`;
    * multiple consumer registrations attach to that single Collector via a `Unique.Token`-keyed inner Ref. The
    * Collector at scrape time runs all stored callbacks through the dispatcher, merges their per-registration results
    * into a single `MetricSnapshot`, and returns it.
    *
    * The inner callback type is uniformly `F[NEL[DataPointSnapshot]]` — pre-built upstream data-point snapshots. Each
    * kind-specific `register*Callback` method maps the user's typed callback (e.g., `F[NEL[(Double, A)]]` for Counter
    * or `F[NEL[(Histogram.Value[Double], A)]]` for Histogram) into the right concrete `DataPointSnapshot` subtype
    * inline. The kind-specific Collector then casts the data points to its concrete subtype to build the right
    * `MetricSnapshot`. Casts are safe because the public `register*Callback` signatures enforce type-correctness at the
    * boundary; internal storage only needs the parent type.
    */
  private[javaclient] type CallbackPayload[F[_]] =
    Map[Unique.Token, F[NonEmptyList[DataPointSnapshot]]]

  private[javaclient] type CallbackState[F[_]] =
    Map[StateKey, (MetricType, Ref[F, CallbackPayload[F]], Collector)]

  private[javaclient] val duplicateShow: Show[(Option[Metric.Prefix], String)] = Show.show { case (prefix, name) =>
    NameUtils.makeName(prefix, name)
  }

}
