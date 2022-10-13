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

package prometheus4cats.internal.histogram

import cats.data.NonEmptySeq
import prometheus4cats._

import scala.annotation.nowarn

final class BucketDsl[A, N] private[prometheus4cats] (
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

  def linearBuckets[Count <: Nat: ToInt](start: N, width: N)(implicit @nowarn gt: GT[Count, Nat._0]): A = {
    val count = Nat.toInt[Count] - 1

    def f(i: Int) = N.plus(start, N.times(N.fromInt(i), width))

    val seq = if (count > 0) NonEmptySeq(f(0), 1.to(count).map(f)) else NonEmptySeq.one(f(0))

    buckets(seq)
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

    def exponentialBuckets[Count <: Nat: ToInt](start: Double, factor: Double)(implicit
        @nowarn gt: GT[Count, Nat._0]
    ): A = {
      val count = Nat.toInt[Count] - 1

      def f(i: Int) = start * Math.pow(factor, i.toDouble)

      val seq = if (count > 0) NonEmptySeq(f(0), 1.to(count).map(f)) else NonEmptySeq.one(f(0))

      dsl.buckets(seq)
    }
  }
}
