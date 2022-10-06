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

package com.permutive.metrics.util

import cats.Show
import cats.syntax.show._
import com.permutive.metrics.Metric

object NameUtils {
  def makeName[A: Show](prefix: Option[Metric.Prefix], name: A): String =
    prefix.fold(name.show)(p => show"${p}_$name")
}
