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

package prometheus4cats.javasimpleclient.models

import cats.Show
import cats.syntax.foldable._
import cats.syntax.show._

import alleycats.std.set._
import prometheus4cats.Label
import prometheus4cats.Metric

object Exceptions {

  sealed trait PrometheusException[A] extends Throwable {

    def className: String

    def metricName: A

    def labels: Map[Label.Name, String]

  }

  final case class UnhandledPrometheusException[A: Show](
      className: String,
      metricName: A,
      labels: Map[Label.Name, String],
      e: Throwable
  ) extends RuntimeException(
        show"Unable to observe $className `$metricName` due to unhandled exception. Labels: $labels",
        e
      )
      with PrometheusException[A]

  final case class DuplicateMetricsException(duplicates: Set[(Option[Metric.Prefix], String)])
      extends RuntimeException(
        s"Duplicate metrics with the following names were found: '${duplicates.mkString_(",")}'"
      )

}
