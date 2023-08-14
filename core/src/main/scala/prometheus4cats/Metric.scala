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

import cats.Hash
import cats.syntax.traverse._
import prometheus4cats.internal.{MetricHelpFromStringLiteral, MetricPrefixFromStringLiteral}
import prometheus4cats.internal.Refined
import prometheus4cats.internal.Refined.Regex

private[prometheus4cats] trait Metric[-A] {
  def contramap[B](f: B => A): Metric[B]
}

object Metric {

  final class CommonLabels private (val value: Map[Label.Name, String])
      extends AnyVal
      with Refined.Value[Map[Label.Name, String]]

  // There is no macro for this as we believe that these labels will likely come from bits of runtime information
  object CommonLabels {

    val empty: CommonLabels = new CommonLabels(Map.empty)

    def from(labels: Map[Label.Name, String]): Either[String, CommonLabels] =
      Either.cond(
        labels.size <= 10,
        new CommonLabels(labels),
        "Number of common labels must not be more than 10"
      )

    def of(labels: (Label.Name, String)*): Either[String, CommonLabels] =
      from(labels.toMap)

    def fromStrings(labels: Map[String, String]): Either[String, CommonLabels] =
      labels.toList.traverse { case (name, value) =>
        Label.Name.from(name).map(_ -> value)
      }.flatMap(ls => from(ls.toMap))

    def ofStrings(labels: (String, String)*): Either[String, CommonLabels] =
      fromStrings(labels.toMap)

    implicit val catsInstances: Hash[CommonLabels] = Hash.by(_.value)

  }

  /** Refined value class for a help message that has been parsed from a string
    */
  final class Help private (val value: String) extends AnyVal with Refined.Value[String]

  object Help extends Regex[Help]("^(?!\\s*$).+".r.pattern, new Help(_)) with MetricHelpFromStringLiteral

  /** Refined value class that can be used with [[MetricFactory]] to prefix every metric name with a certain string
    * value
    */
  final class Prefix private (val value: String) extends AnyVal with Refined.Value[String]

  object Prefix
      extends Regex[Prefix]("^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern, new Prefix(_))
      with MetricPrefixFromStringLiteral

  private[prometheus4cats] trait Labelled[-A] {
    def contramapLabels[B](f: B => A): Labelled[B]
  }
}
