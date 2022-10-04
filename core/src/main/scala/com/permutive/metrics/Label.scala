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

import cats.Eq
import cats.Hash
import cats.Order
import cats.Show

object Label {

  final class Name private (val value: String) extends AnyVal {

    override def toString: String = value

  }

  object Name extends LabelNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r

    def from(string: String): Either[String, Name] =
      Either.cond(
        regex.matches(string),
        new Name(string),
        s"$string must match `$regex`"
      )

    implicit val LabelNameHash: Hash[Name] = Hash.by(_.value)

    implicit val LabelNameEq: Eq[Name] = Eq.by(_.value)

    implicit val LabelNameShow: Show[Name] = Show.show(_.value)

    implicit val LabelNameOrder: Order[Name] = Order.by(_.value)

  }

}
