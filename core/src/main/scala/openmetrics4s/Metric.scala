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

import cats.syntax.traverse._
import cats.{Eq, Hash, Order, Show}

private[openmetrics4s] trait Metric[A] {
  def contramap[B](f: B => A): Metric[B]
}

object Metric {

  final class CommonLabels private (val value: Map[Label.Name, String]) extends AnyVal {
    override def toString: String = s"Metric.CommonLabels([${value.map { case (name, value) =>
        s"""$name -> "$value""""
      }.mkString(",")}])"
  }

  // There is no macro for this as we believe that these labels will likely come from bits of runtime information
  object CommonLabels {

    val empty: CommonLabels = new CommonLabels(Map.empty)

    def from(labels: Map[Label.Name, String]): Either[String, CommonLabels] =
      Either.cond(
        labels.size <= 10,
        new CommonLabels(labels),
        "Number of common labels must not be more than 10"
      )

    def from(labels: (Label.Name, String)*): Either[String, CommonLabels] =
      from(labels.toMap)

    def fromStrings(labels: Map[String, String]): Either[String, CommonLabels] =
      labels.toList.traverse { case (name, value) =>
        Label.Name.from(name).map(_ -> value)
      }.flatMap(ls => from(ls.toMap))

    def fromStrings(labels: (String, String)*): Either[String, CommonLabels] =
      fromStrings(labels.toMap)

    implicit val catsInstances: Hash[CommonLabels] = Hash.by(_.value)
  }

  /** Refined value class for a help message that has been parsed from a string
    */
  final class Help private (val value: String) extends AnyVal {

    override def toString: String = s"""Metric.Help("$value")"""

  }

  object Help extends MetricHelpFromStringLiteral {

    /** Parse a [[Help]] from the given string
      *
      * @param string
      *   value from which to parse a help string
      * @return
      *   a parsed [[Help]] or failure message, represented by an [[scala.Either]]
      */
    def from(string: String): Either[String, Help] =
      Either.cond(string.nonEmpty, new Help(string), s"must not be empty blank")

    implicit val catsInstances: Hash[Help] with Order[Help] with Show[Help] = new Hash[Help]
      with Order[Help]
      with Show[Help] {
      override def hash(x: Help): Int = Hash[String].hash(x.value)

      override def compare(x: Help, y: Help): Int = Order[String].compare(x.value, y.value)

      override def show(t: Help): String = t.value

      override def eqv(x: Help, y: Help): Boolean = Eq[String].eqv(x.value, y.value)
    }

  }

  /** Refined value class that can be used with [[MetricsFactory]] to prefix every metric name with a certain string
    * value
    */
  final class Prefix private (val value: String) extends AnyVal {
    override def toString: String = s"""Metric.Prefix("$value")"""
  }

  object Prefix extends MetricPrefixFromStringLiteral {
    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern

    /** Parse a [[Prefix]] from the given string
      *
      * @param string
      *   value from which to parse a prefix value
      * @return
      *   a parsed [[Prefix]] or failure message, represented by an [[scala.Either]]
      */
    def from(string: String): Either[String, Prefix] =
      Either.cond(
        regex.matcher(string).matches(),
        new Prefix(string),
        s"$string must match `$regex`"
      )

    implicit val catsInstances: Hash[Prefix] with Order[Prefix] with Show[Prefix] = new Hash[Prefix]
      with Order[Prefix]
      with Show[Prefix] {
      override def hash(x: Prefix): Int = Hash[String].hash(x.value)

      override def compare(x: Prefix, y: Prefix): Int = Order[String].compare(x.value, y.value)

      override def show(t: Prefix): String = t.value

      override def eqv(x: Prefix, y: Prefix): Boolean = Eq[String].eqv(x.value, y.value)
    }
  }

  private[openmetrics4s] trait Labelled[A] {
    def contramapLabels[B](f: B => A): Labelled[B]
  }
}
