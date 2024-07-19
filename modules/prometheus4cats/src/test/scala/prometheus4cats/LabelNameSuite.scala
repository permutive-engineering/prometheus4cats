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

package prometheus4cats

import munit.ScalaCheckSuite

class LabelNameSuite extends ScalaCheckSuite with NameSuite[Label.Name] {
  override def make(str: String): Either[String, Label.Name] = Label.Name.from(str)

  override def stringValue(result: Label.Name): String = result.value
}
