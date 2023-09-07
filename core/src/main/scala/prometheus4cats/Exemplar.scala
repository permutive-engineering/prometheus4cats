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

import cats.Applicative
import prometheus4cats.internal.ExemplarLabelNameFromStringLiteral
import prometheus4cats.internal.Refined
import prometheus4cats.internal.Refined.Regex

import java.time.Instant
import scala.collection.immutable.SortedMap

/** A typeclass to provide exemplars to counters and histograms, which may be used by [[MetricRegistry]]
  * implementations.
  */
trait Exemplar[F[_]] {
  def get: F[Option[Exemplar.Labels]]
}

object Exemplar {
  def apply[F[_]: Exemplar]: Exemplar[F] = implicitly

  object Implicits {
    implicit def noop[F[_]: Applicative]: Exemplar[F] = new Exemplar[F] {
      override def get: F[Option[Labels]] = Applicative[F].pure(None)
    }
  }

  sealed abstract class Data(val labels: Exemplar.Labels, val timestamp: Instant) extends Serializable

  object Data {
    def apply(labels: Labels, timestamp: Instant): Data = new Data(labels, timestamp) {}
  }

  /** Refined value class for an exemplar label name that has been parsed from a string
    */
  final class LabelName private (val value: String) extends AnyVal with Refined.Value[String]

  object LabelName
      extends Regex[LabelName]("^[a-zA-Z_][a-zA-Z_0-9]*$".r.pattern, new LabelName(_))
      with ExemplarLabelNameFromStringLiteral

  /** Refined value class for a set of exemplar labels.
    *
    * Validation will fail if the labels are empty or if the length of all the label names and values does not exceed
    * 128 UTF-8 characters
    */
  final class Labels private (val value: SortedMap[LabelName, String])
      extends AnyVal
      with Refined.Value[SortedMap[LabelName, String]]

  object Labels
      extends Refined[SortedMap[LabelName, String], Labels](
        make = new Labels(_),
        test = a => a.nonEmpty && a.map { case (k, v) => s"${k.value}$v".length }.sum <= 128,
        nonMatchMessage = _ =>
          "exemplar labels must not be empty and the combined length of the label names and values must not exceed 128 UTF-8 characters"
      ) {
    def of(first: (LabelName, String), rest: (LabelName, String)*): Either[String, Labels] =
      from(rest.foldLeft(SortedMap.empty[LabelName, String].updated(first._1, first._2)) { case (acc, (k, v)) =>
        acc.updated(k, v)
      })

    def fromMap(a: Map[LabelName, String]): Either[String, Labels] = from(
      a.foldLeft(SortedMap.empty[LabelName, String]) { case (acc, (k, v)) => acc.updated(k, v) }
    )

  }
}
