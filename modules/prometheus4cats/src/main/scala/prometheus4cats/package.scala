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

import prometheus4cats.internal.ShapelessPolyfill

package object prometheus4cats extends ShapelessPolyfill {

  /** Type alias that lets `Info` reuse the existing labelled-metric DSL machinery (`MetricDsl`, `LabelledMetricDsl`)
    * which is parameterised over `L[F[_], A, B]` of kind `(* -> *) -> * -> * -> *`.
    *
    * `Info[F, A]` only has two type parameters (effect + labels); the value type `A` in the L-shape is unused for Info
    * because info metrics don't have a separate observation value (they're emitted as `name{labels...} 1`).
    * Conventionally we instantiate Info-shaped DSLs at `MetricDsl[F, Unit, InfoL]`.
    */
  type InfoL[F[_], A, B] = Info[F, B]

  /** Compound type exposing both `MetricDsl[F, A, Histogram]` (for `.label`, `.build`, etc.) and
    * `HistogramWithNativeOps[F, A]` (for `.withNative`) on a single value. Both `HistogramMetricDsl.Plain` and
    * `HistogramMetricDsl.WithCallbacksImpl` satisfy this — the former via `MetricDsl`, the latter via
    * `MetricDsl.WithCallbacks` (which extends `MetricDsl`).
    */
  type HistogramMetricDsl[F[_], A] =
    internal.MetricDsl[F, A, Histogram] with internal.HistogramWithNativeOps[F, A]

}
