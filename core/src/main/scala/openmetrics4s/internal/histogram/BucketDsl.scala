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

import scala.annotation.nowarn

final class BucketDsl[A, N] private[openmetrics4s] (
    f: NonEmptySeq[N] => A
)(implicit N: Numeric[N]) {

  /** Provides the list of buckets for the histogram as a non-empty sequence.
    */
  def buckets(list: NonEmptySeq[N]): A =
    f(list)

  /** Provides the list of buckets for the histogram as parameters.
    */
  def buckets(head: N, rest: N*): A = buckets(
    NonEmptySeq.of(head, rest: _*)
  )

  def linearBuckets[NN <: Nat: ToInt](start: N, width: N)(implicit @nowarn gt: GT[NN, Nat._0]): NonEmptySeq[N] = {
    val count = Nat.toInt[NN] - 1

    def f(i: Int) = N.plus(start, N.times(N.fromInt(i), width))

    NonEmptySeq(f(0), 1.to(count).map(f))
  }
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

    def exponentialBuckets[NN <: Nat: ToInt](start: Double, factor: Double)(implicit
        @nowarn gt: GT[NN, Nat._0]
    ): NonEmptySeq[Double] = {
      val count = Nat.toInt[NN] - 1

      def f(i: Int) = start * Math.pow(factor, i.toDouble)

      NonEmptySeq(f(0), 1.to(count).map(f))
    }
  }
}