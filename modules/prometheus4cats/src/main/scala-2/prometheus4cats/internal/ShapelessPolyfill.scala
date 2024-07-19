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

package prometheus4cats.internal

private[prometheus4cats] trait ShapelessPolyfill {

  type Nat = shapeless.Nat

  object Nat {

    type _0 = shapeless.Nat._0

    type _1 = shapeless.Nat._1

    def toInt[N <: Nat](implicit toIntN: ToInt[N]): Int = shapeless.Nat.toInt[N]

  }

  type ToInt[N <: Nat] = shapeless.ops.nat.ToInt[N]

  val ToInt = shapeless.ops.nat.ToInt

  type GT[A <: Nat, B <: Nat] = shapeless.ops.nat.GT[A, B]

  val GT = shapeless.ops.nat.GT

  type Succ[N <: Nat] = shapeless.Succ[N]

  val Succ = shapeless.Succ

}
