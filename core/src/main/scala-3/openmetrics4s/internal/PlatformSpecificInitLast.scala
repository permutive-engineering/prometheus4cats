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

package openmetrics4s.internal

trait PlatformSpecificInitLast extends LowPriorityInitLast {
  type NonEmptyAppend[X <: Tuple, Y] <: NonEmptyTuple = X match {
    case EmptyTuple => Y *: EmptyTuple
    case x *: xs => x *: NonEmptyAppend[xs, Y]
  }

  implicit def default[A <: NonEmptyTuple, B]: InitLast.Aux[A, B, NonEmptyAppend[A, B]] =
    InitLast.make(_.init.asInstanceOf[A], _.last.asInstanceOf[B])
}
