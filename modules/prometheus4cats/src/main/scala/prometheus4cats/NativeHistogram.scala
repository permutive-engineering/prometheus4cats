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

import scala.concurrent.duration._

/** Tuning parameters for native histograms.
  *
  * Native histograms (also known as sparse or exponential histograms) automatically distribute observations into
  * buckets sized by an exponential schema, removing the need for consumers to pre-declare bucket boundaries. The
  * trade-off is per-metric memory and a small amount of complexity around schema selection and bucket-count limits.
  *
  * The defaults here match the upstream Java client defaults and are appropriate for the common case. Override only
  * when you have a specific reason — e.g., a metric with extremely wide value range that needs lower resolution.
  *
  * @param initialSchema
  *   The schema controls native histogram resolution. Each higher schema halves bucket width (roughly: schema `s`
  *   produces `2^s` buckets per power of two of the observation range). Valid range is `[-4, 8]`. Default `8` matches
  *   upstream and gives the highest available resolution.
  * @param maxNumberOfBuckets
  *   Upper limit on the number of populated native buckets. When exceeded, the histogram automatically reduces its
  *   schema. Default `160`. Set lower to bound per-metric memory; set to `0` to disable the limit.
  * @param resetDuration
  *   How often the histogram resets back to the original schema. Useful when transient observations push the schema
  *   down and you want it to recover. Default `0.seconds` (no reset).
  * @param minZeroThreshold
  *   Smallest tracked zero-bucket threshold. Observations below this are folded into the zero bucket. Default `0.0`.
  * @param maxZeroThreshold
  *   Largest allowed zero-bucket threshold. The histogram may grow the zero bucket up to this value when the
  *   `maxNumberOfBuckets` limit is hit. Default `0.0` (no growth).
  */
final case class NativeHistogram private (
    initialSchema: Int,
    maxNumberOfBuckets: Int,
    resetDuration: FiniteDuration,
    minZeroThreshold: Double,
    maxZeroThreshold: Double
) {

  def withInitialSchema(initialSchema: Int): NativeHistogram = copy(initialSchema = initialSchema)

  def withMaxNumberOfBuckets(maxNumberOfBuckets: Int): NativeHistogram = copy(maxNumberOfBuckets = maxNumberOfBuckets)

  def withResetDuration(resetDuration: FiniteDuration): NativeHistogram = copy(resetDuration = resetDuration)

  def withMinZeroThreshold(minZeroThreshold: Double): NativeHistogram = copy(minZeroThreshold = minZeroThreshold)

  def withMaxZeroThreshold(maxZeroThreshold: Double): NativeHistogram = copy(maxZeroThreshold = maxZeroThreshold)

}

object NativeHistogram {

  /** Default tuning matching the upstream Java client defaults. */
  val Default: NativeHistogram = new NativeHistogram(
    initialSchema = 8, maxNumberOfBuckets = 160, resetDuration = 0.seconds, minZeroThreshold = 0.0,
    maxZeroThreshold = 0.0
  )

}
