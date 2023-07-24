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
import cats.syntax.show._
import prometheus4cats.internal.ExemplarLabelNameFromStringLiteral

import java.util.regex.Pattern
import scala.collection.immutable.SortedMap

/** A typeclass to provide exemplars to counters and histograms, which may be used by [[MetricRegistry]]
  * implementations.
  */
trait Exemplar[F[_]] {
  def get: F[Option[Exemplar.Labels]]
}

object Exemplar {
  def apply[F[_]: Exemplar]: Exemplar[F] = implicitly

  implicit def noop[F[_]: Applicative]: Exemplar[F] = new Exemplar[F] {
    override def get: F[Option[Labels]] = Applicative[F].pure(None)
  }

  /** Refined value class for an exemplar label name that has been parsed from a string
    */
  final class LabelName private (val value: String) extends AnyVal with internal.Refined.Value[String] {
    override def toString: String = s"""Exemplar.LabelName("$value")"""
  }

  object LabelName extends internal.Refined.StringRegexRefinement[LabelName] with ExemplarLabelNameFromStringLiteral {
    protected val regex: Pattern = "^[a-zA-Z_][a-zA-Z_0-9]*$".r.pattern

    implicit val ordering: Ordering[LabelName] = catsInstances.toOrdering

    override protected def make(a: String): LabelName = new LabelName(a)
  }

  /** Refined value class for a set of exemplar labels.
    *
    * Validation will fail if the labels are empty or if the length of all the label names and values does not exceed
    * 128 UTF-8 characters
    */
  final class Labels private (val value: SortedMap[LabelName, String])
      extends AnyVal
      with internal.Refined.Value[SortedMap[LabelName, String]] {
    override def toString: String = s"""Exemplar.Labels("${value.show}")"""
  }

  object Labels extends internal.Refined[SortedMap[LabelName, String], Labels] {
    def fromMap(a: Map[LabelName, String]): Either[String, Labels] = from(
      a.foldLeft(SortedMap.empty[LabelName, String]) { case (acc, (k, v)) => acc.updated(k, v) }
    )

    override protected def make(a: SortedMap[LabelName, String]): Labels = new Labels(a)

    override protected def test(a: SortedMap[LabelName, String]): Boolean =
      a.nonEmpty && a.map { case (k, v) => s"${k.value}$v".length }.sum <= 128

    override protected def nonMatchMessage(a: SortedMap[LabelName, String]): String =
      "exemplar labels must not be empty and the combined length of the label names and values must not exceed 128 UTF-8 characters"
  }
}
