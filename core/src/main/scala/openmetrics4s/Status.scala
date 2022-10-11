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

package openmetrics4s

import cats.Show

sealed trait Status

object Status {
  case object Succeeded extends Status

  case object Errored extends Status

  case object Canceled extends Status

  implicit val catsInstances: Show[Status] = Show.show {
    case Status.Succeeded => "succeeded"
    case Status.Errored => "errored"
    case Status.Canceled => "canceled"
  }
}
