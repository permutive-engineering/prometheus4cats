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

import scala.quoted.*
import scala.util.control.NoStackTrace

case class RefinementError private[internal] (msg: String) extends RuntimeException(msg) with NoStackTrace

private[internal] trait MacroUtils {
  def error(msg: String): Nothing = throw RefinementError(msg)

  def abort(constructorName: String)(using q: Quotes): Unit =
    q.reflect.report.error(
      s"This method uses a macro to verify that a literal is valid. Use $constructorName if you have a dynamic value you want to parse."
    )
}
