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

import scala.concurrent.duration._

import cats.Id
import cats.data.WriterT
import cats.effect.IO
import cats.effect.kernel.Outcome.Succeeded
import cats.effect.testkit.TestControl

import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF._

class CurrentTimeRecorderSuite extends CatsEffectSuite with ScalaCheckEffectSuite {

  def write[A]: (A, Unit) => WriterT[IO, List[A], Unit] = (d, _: Unit) => WriterT.tell(List(d))

  val longGauge =
    CurrentTimeRecorder.fromLongGauge(
      Gauge.make(
        write,
        write,
        write
      )
    )(_)

  val doubleGauge = CurrentTimeRecorder.fromDoubleGauge(
    Gauge.make(
      write,
      write,
      write
    )
  )(_)

  def writeLabels[A, B]: (A, B) => WriterT[IO, List[(A, B)], Unit] = (a, b) => WriterT.tell(List(a -> b))

  val labelledLongGauge = CurrentTimeRecorder.fromLongGauge(
    Gauge.make(
      writeLabels[Long, String],
      writeLabels[Long, String],
      writeLabels[Long, String]
    )
  )(_)

  val labelledDoubleGauge = CurrentTimeRecorder.fromDoubleGauge(
    Gauge.make(
      writeLabels[Double, String],
      writeLabels[Double, String],
      writeLabels[Double, String]
    )
  )(_)

  implicit val durArb: Arbitrary[FiniteDuration] = Arbitrary(Gen.posNum[Long].map(_.nanos))

  def test[A](fa: IO[List[A]], tickBy: FiniteDuration, expected: A): IO[Unit] =
    TestControl.execute(fa).flatMap { control =>
      for {
        _ <- control.tick
        _ <- control.advanceAndTick(tickBy)
        _ <- control.results.assertEquals(
               Some(Succeeded[Id, Throwable, List[A]](List(expected)))
             )
      } yield ()

    }

  test("sets to current time in seconds") {
    forAllF { (dur: FiniteDuration) =>
      test(longGauge(_.toSeconds).mark.written, dur, dur.toSeconds) >> test(
        doubleGauge(_.toSeconds.toDouble).mark.written,
        dur,
        dur.toSeconds.toDouble
      )
    }
  }

  test("sets to current time in millis") {
    forAllF { (dur: FiniteDuration) =>
      test(longGauge(_.toMillis).mark.written, dur, dur.toMillis) >> test(
        doubleGauge(_.toMillis.toDouble).mark.written,
        dur,
        dur.toMillis.toDouble
      )
    }
  }

  test("sets to current time in seconds with labels") {
    forAllF { (dur: FiniteDuration, s: String) =>
      test(labelledLongGauge(_.toSeconds).mark(s).written, dur, (dur.toSeconds, s)) >> test(
        labelledLongGauge(_.toSeconds).mark(s).written,
        dur,
        (dur.toSeconds.toDouble, s)
      )
    }
  }

  test("sets to current time in millis with labels") {
    forAllF { (dur: FiniteDuration, s: String) =>
      test(labelledLongGauge(_.toMillis).mark(s).written, dur, (dur.toMillis, s)) >> test(
        labelledLongGauge(_.toMillis).mark(s).written,
        dur,
        (dur.toMillis.toDouble, s)
      )
    }
  }

}
