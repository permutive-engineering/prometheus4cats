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

import cats.effect.IO
import cats.effect.testkit.TestInstances
import munit.Suite

import scala.concurrent.duration._

trait TickerSuite extends TestInstances { self: Suite =>
  val time: FiniteDuration = 0.nanos

  def exec[A](fa: IO[A], tickBy: FiniteDuration = 1.second): A = {
    implicit val ticker: Ticker = Ticker()

    val res = fa.unsafeToFuture()
    ticker.ctx.tickAll()
    ticker.ctx.advanceAndTick(tickBy)
    res.value.get.get
  }
}
