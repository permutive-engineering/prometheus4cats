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

trait Neq[A, B] extends Serializable

object Neq {

  def unexpected: Nothing = sys.error("Unexpected invocation")

  implicit def neq[A, B]: A Neq B = new Neq[A, B] {}

  implicit def neqAmbig1[A]: A Neq A = unexpected

  implicit def neqAmbig2[A]: A Neq A = unexpected

}
