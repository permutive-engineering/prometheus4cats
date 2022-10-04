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

package com.permutive.metrics

import cats.{Eq, Hash, Order, Show}
import cats.syntax.traverse._

object Metric {

  final class CommonLabels private (val value: Map[Label.Name, String]) extends AnyVal {}

  // There is no macro for this as we believe that these labels will likely come from bits of runtime information
  object CommonLabels {

    val empty: CommonLabels = new CommonLabels(Map.empty)

    def from(labels: (Label.Name, String)*): Either[String, CommonLabels] =
      Either.cond(
        labels.size > 10,
        new CommonLabels(labels.toMap),
        "Number of common labels must not be more than 10"
      )

    def fromStrings(labels: (String, String)*): Either[String, CommonLabels] =
      labels.toList.traverse { case (name, value) =>
        Label.Name.from(name).map(_ -> value)
      }
        .flatMap(ls => from(ls: _*))
  }

  /** Refined value class for a help message that has been parsed from a string
    */
  final class Help private (val value: String) extends AnyVal {

    override def toString: String = value

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
      Either.cond(!string.isBlank, new Help(string), s"must not be empty blank")

    implicit val MetricHelpHash: Hash[Help] = Hash.by(_.value)

    implicit val MetricHelpEq: Eq[Help] = Eq.by(_.value)

    implicit val MetricHelpShow: Show[Help] = Show.show(_.value)

    implicit val MetricHelpOrder: Order[Help] = Order.by(_.value)

  }

  /** Refined value class that can be used with [[MetricsFactory]] to prefix every metric name with a certain string
    * value
    */
  final class Prefix private (val value: String) extends AnyVal

  object Prefix extends MetricPrefixFromStringLiteral {
    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r

    /** Parse a [[Prefix]] from the given string
      *
      * @param string
      *   value from which to parse a prefix value
      * @return
      *   a parsed [[Prefix]] or failure message, represented by an [[scala.Either]]
      */
    def from(string: String): Either[String, Prefix] =
      Either.cond(
        regex.matches(string),
        new Prefix(string),
        s"$string must match `$regex`"
      )

    implicit val MetricPrefixHash: Hash[Prefix] = Hash.by(_.value)

    implicit val MetricPrefixEq: Eq[Prefix] = Eq.by(_.value)

    implicit val MetricPrefixShow: Show[Prefix] = Show.show(_.value)

    implicit val MetricPrefixOrder: Order[Prefix] = Order.by(_.value)
  }
}
