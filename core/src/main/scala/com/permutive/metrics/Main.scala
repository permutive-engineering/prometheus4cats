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

import cats.effect.{IO, IOApp}
import com.permutive.metrics.internal.InitLast
import com.permutive.metrics.internal.InitLast.default

object Main extends IOApp.Simple {

  InitLast[(String, String), Int, (String, String, Int)]

  InitLast.default[(String, String, Int), (String, String), Int]

//  def run: IO[Unit] =
//    gauge[IO]("dsfsdf")
//      .help("dsfsdf")
//      .label[String]("sfsd")
//      .label[String]("sdf32rw")
//      .label[Int]("sdfsdfs")
//      .build

  //      .flatMap(x => x.inc(0.1, ("sdfsdf", "d23r232", 1)))

  def run = IO.println("test")

}
