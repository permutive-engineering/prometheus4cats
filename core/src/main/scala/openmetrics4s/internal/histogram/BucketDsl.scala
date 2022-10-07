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
import openmetrics4s.Histogram

final class BucketDsl[A, N] private[openmetrics4s] (
    f: NonEmptySeq[N] => A
) {

  /** Provides the list of buckets for the histogram as a non-empty sequence.
    */
  def buckets(list: NonEmptySeq[N]): A =
    f(list)

  /** Provides the list of buckets for the histogram as parameters.
    */
  def buckets(head: N, rest: N*): A = buckets(
    NonEmptySeq.of(head, rest: _*)
  )
}

object BucketDsl {
  implicit class DoubleSyntax[A](dsl: BucketDsl[A, Double]) {

    /** Initialise this histogram with the default HTTP buckets list.
      *
      * @see
      *   [[Histogram.DefaultHttpBuckets]]
      */
    def defaultHttpBuckets: A = dsl.buckets(
      Histogram.DefaultHttpBuckets
    )
  }
}
