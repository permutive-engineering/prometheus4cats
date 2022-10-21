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

import prometheus4cats.Summary

import scala.reflect.macros.blackbox

trait SummaryAgeBucketsFromIntLiteral {

  def apply(t: Int): Summary.AgeBuckets =
    macro SummaryAgeBucketsMacros.fromIntLiteral

  implicit def fromDoubleLiteral(t: Int): Summary.AgeBuckets =
    macro SummaryAgeBucketsMacros.fromIntLiteral

}

private[prometheus4cats] class SummaryAgeBucketsMacros(val c: blackbox.Context) extends MacroUtils {

  def fromIntLiteral(t: c.Expr[Int]): c.Expr[Summary.AgeBuckets] = {
    val int: Int = literal(t, or = "Summary.AgeBuckets.from({int})")

    Summary.AgeBuckets
      .from(int)
      .fold(
        abort,
        _ => c.universe.reify(Summary.AgeBuckets.from(t.splice).toOption.get)
      )
  }

}
