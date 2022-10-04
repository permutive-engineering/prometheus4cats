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

package com.permutive.metrics

import scala.compiletime.ops.int.*
import scala.quoted.*

trait ShapelessPolyfill {

  type Represented[R] = R match {
    case IndexedSeq[a] => a
  }

  type Nat = Int

  object Nat {
    type _1 = 1
  }

  type Succ[N <: Nat] = N + 1

  type TupleSized[R, A, N <: Int] <: Tuple = N match {
    case 0 => EmptyTuple
    case S[n] => A *: TupleSized[R, A, n]
  }

  extension [R, A, N <: Int](s: TupleSized[R, A, N]) {
    def unsized: IndexedSeq[A] =
      s.productIterator.toIndexedSeq.asInstanceOf[IndexedSeq[A]]
    def :+(a: A): TupleSized[R, A, N + 1] =
      (s :* a).asInstanceOf[TupleSized[R, A, N + 1]]
  }

  type Sized[Repr, L <: Nat] = TupleSized[Repr, Represented[Repr], L]

  object Sized {
    def apply[A](a1: A): Sized[IndexedSeq[A], 1] = Tuple1(a1)
  }

}
