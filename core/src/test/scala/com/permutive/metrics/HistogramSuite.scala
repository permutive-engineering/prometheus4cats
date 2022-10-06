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

import java.util.concurrent.TimeUnit

import cats.data.{NonEmptyList, WriterT}
import cats.effect.IO
import cats.effect.testkit.TestInstances
import munit.ScalaCheckSuite
import org.scalacheck.Prop._

import scala.concurrent.duration._

class HistogramSuite extends ScalaCheckSuite with TestInstances {
  def hist: Histogram[WriterT[IO, List[Double], *]] =
    Histogram.make(d => WriterT.tell[IO, List[Double]](List(d)))

  val time: FiniteDuration = 0.nanos

  def exec[A](fa: IO[A], tickBy: FiniteDuration = 1.second): A = {
    implicit val ticker: Ticker = Ticker()

    val res = fa.unsafeToFuture()
    ticker.ctx.tickAll()
    ticker.ctx.advanceAndTick(tickBy)
    res.value.get.get
  }

  property("observe records doubles") {
    forAll { (d: Double, ds: List[Double]) =>
      val histogram = hist

      val test = NonEmptyList(d, ds)
        .traverse(histogram.observe)
        .written
        .map(res => assertEquals(res, d :: ds))

      exec(test)
    }
  }

  property("observeDuration records times in seconds") {
    forAll { (dur: FiniteDuration, durs: List[FiniteDuration]) =>
      val histogram = hist

      val test = NonEmptyList(dur, durs)
        .traverse(histogram.observeDuration)
        .written
        .map(res => assertEquals(res, (dur :: durs).map(_.toUnit(TimeUnit.SECONDS))))

      exec(test)
    }
  }

  property("timing an operation delegates to observeDuration") {
    forAll { (dur: FiniteDuration, durs: List[FiniteDuration]) =>
      val histogram = hist

      val test = NonEmptyList(dur, durs)
        .traverse(d => histogram.time(WriterT.liftF(IO.sleep(d))))
        .written
        .map(res => assertEquals(res, (dur :: durs).map(_.toUnit(TimeUnit.SECONDS))))

      exec(test)
    }
  }

}
