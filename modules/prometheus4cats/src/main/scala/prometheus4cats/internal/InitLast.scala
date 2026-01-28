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

package prometheus4cats.internal

/** Type class supporting accessing both the init (all but the last element) and the last element of a tuple. */
sealed private[prometheus4cats] trait InitLast[A, B] extends Serializable {

  type C <: Product

  def init(c: C): A

  def last(c: C): B

}

private[internal] trait LowPriorityInitLast {

  implicit def base[A, B]: InitLast.Aux[A, B, (A, B)] =
    InitLast.make(_._1, _._2)

}

private[prometheus4cats] object InitLast extends PlatformSpecificInitLast {

  private[internal] def apply[A, B, C](implicit
      ev: InitLast.Aux[A, B, C]
  ): InitLast.Aux[A, B, C] = ev

  type Aux[A, B, Out] = InitLast[A, B] { type C = Out }

  private[internal] def make[A, B, Out <: Product](
      _init: Out => A,
      _last: Out => B
  ): InitLast.Aux[A, B, Out] =
    new InitLast[A, B] {

      override type C = Out

      override def init(c: C): A = _init(c)

      override def last(c: C): B = _last(c)

    }

}
