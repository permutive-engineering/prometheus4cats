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

package com.permutive

package object metrics extends ShapelessPolyfill {

  /** Starts creating a "gauge" metric.
    *
    * @example
    *   {{{ import com.permutive.metrics._ import eu.timepit.refined.auto._
    *
    * gauge("my_gauge") .help("my gauge help") .label[Int]("first_label") .label[String]("second_label")
    * .label[Boolean]("third_label") .build }}}
    */
//  def gauge[F[_]: Applicative: Console](
//      name: Gauge.Name
//  ): HelpStep[GaugeDsl[F]] =
//    new HelpStep(new GaugeDsl[F](name, _))
}
