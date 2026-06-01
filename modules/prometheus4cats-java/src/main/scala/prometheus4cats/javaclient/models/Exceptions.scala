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

package prometheus4cats.javaclient.models

import prometheus4cats.Label

private[javaclient] object Exceptions {

  sealed abstract class PrometheusException[A] extends RuntimeException

  final case class UnhandledPrometheusException[A](
      className: String,
      metricName: A,
      labels: Map[Label.Name, String],
      cause: Throwable
  ) extends PrometheusException[A] {

    override def getMessage: String =
      s"Unhandled exception while operating on metric '$metricName' (impl class '$className', labels: $labels)"

    override def getCause: Throwable = cause

  }

}
