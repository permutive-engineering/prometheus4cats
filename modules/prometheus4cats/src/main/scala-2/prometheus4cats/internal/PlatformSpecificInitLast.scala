/*
 * Copyright 2022-2024 Permutive Ltd. <https://permutive.com>
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

import scala.annotation.nowarn

import shapeless.ops.tuple._

private[prometheus4cats] trait PlatformSpecificInitLast extends LowPriorityInitLast {

  implicit def default[C0 <: Product, A <: Product, B](implicit
      @nowarn prepend: Prepend.Aux[A, Tuple1[B], C0],
      initEv: Init.Aux[C0, A],
      lastEv: Last.Aux[C0, B]
  ): InitLast.Aux[A, B, C0] = InitLast.make(initEv.apply, lastEv.apply)

}
