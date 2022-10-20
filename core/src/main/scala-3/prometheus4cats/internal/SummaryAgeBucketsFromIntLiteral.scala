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

package prometheus4cats.internal

import prometheus4cats._

import scala.quoted.*

trait SummaryAgeBucketsFromIntLiteral {

  inline def apply(inline t: Int): Summary.AgeBuckets = ${
    SummaryAgeBucketsFromIntLiteral.ageBucketsLiteral('t)
  }

  implicit inline def fromStringLiteral(inline t: Int): Summary.AgeBuckets = ${
    SummaryAgeBucketsFromIntLiteral.ageBucketsLiteral('t)
  }

}

object SummaryAgeBucketsFromIntLiteral extends MacroUtils {
  def ageBucketsLiteral(i: Expr[Int])(using q: Quotes): Expr[Summary.AgeBuckets] =
    i.value match {
      case Some(int) =>
        Summary.AgeBuckets
          .from(int)
          .fold(
            error,
            _ =>
              '{
                Summary.AgeBuckets.from(${ Expr(int) }).toOption.get
              }
          )
      case None =>
        abort("Summary.AgeBuckets.from")
        '{ ??? }
    }
}
