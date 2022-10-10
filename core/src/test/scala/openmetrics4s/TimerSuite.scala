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

import java.util.concurrent.TimeUnit

import cats.data.{NonEmptyList, WriterT}
import cats.effect.testkit.TestInstances
import cats.effect.{Clock, IO, Ref}
import munit.ScalaCheckSuite
import org.scalacheck.Prop._

import scala.concurrent.duration._

class TimerSuite extends ScalaCheckSuite with TestInstances {
  val write: Double => WriterT[IO, List[Double], Unit] = d => WriterT.tell[IO, List[Double]](List(d))

  val hist =
    Timer.fromHistogram(Histogram.make(write))

  val gauge =
    Timer.fromGauge(
      Gauge.make(
        1.0,
        write,
        write,
        write,
        Clock[WriterT[IO, List[Double], *]].realTime.flatMap(dur => write(dur.toSeconds.toDouble))
      )
    )

  def writeLabels[A]: (Double, A) => WriterT[IO, List[(Double, A)], Unit] = (d, a) =>
    WriterT.tell[IO, List[(Double, A)]](List(d -> a))

  val labelledHistogram = Timer.Labelled.fromHistogram(Histogram.Labelled.make(writeLabels[String]))

  val labelledGauge = Timer.Labelled.fromGauge(
    Gauge.Labelled.make(
      1.0,
      writeLabels[String],
      writeLabels[String],
      writeLabels[String],
      (s: String) =>
        Clock[WriterT[IO, List[(Double, String)], *]].realTime.flatMap(dur => writeLabels(dur.toSeconds.toDouble, s))
    )
  )

  val time: FiniteDuration = 0.nanos

  def exec[A](fa: IO[A], tickBy: FiniteDuration = 1.second): A = {
    implicit val ticker: Ticker = Ticker()

    val res = fa.unsafeToFuture()
    ticker.ctx.tickAll()
    ticker.ctx.advanceAndTick(tickBy)
    res.value.get.get
  }

  property("observeDuration records times in seconds") {
    forAll { (dur: FiniteDuration, durs: List[FiniteDuration]) =>
      def test(f: FiniteDuration => WriterT[IO, List[Double], Unit]): IO[Unit] = NonEmptyList(dur, durs)
        .traverse(f)
        .written
        .map(res => assertEquals(res, (dur :: durs).map(_.toUnit(TimeUnit.SECONDS))))

      exec(test(hist.recordTime) >> test(gauge.recordTime))
    }
  }

  property("observeDuration records times in seconds with labels") {
    forAll { (dur: (FiniteDuration, String), durs: List[(FiniteDuration, String)]) =>
      def test(f: (FiniteDuration, String) => WriterT[IO, List[(Double, String)], Unit]): IO[Unit] =
        NonEmptyList(dur, durs)
          .traverse(f.tupled)
          .written
          .map(res => assertEquals(res, (dur :: durs).map { case (dur, l) => dur.toUnit(TimeUnit.SECONDS) -> l }))

      exec(test(labelledHistogram.recordTime) >> test(labelledGauge.recordTime))
    }
  }

  property("timing an operation delegates to observeDuration") {
    forAll { (dur: FiniteDuration, durs: List[FiniteDuration]) =>
      def test(f: WriterT[IO, List[Double], Unit] => WriterT[IO, List[Double], Unit]): IO[Unit] =
        NonEmptyList(dur, durs)
          .traverse(d => f(WriterT.liftF(IO.sleep(d))))
          .written
          .map(res => assertEquals(res, (dur :: durs).map(_.toUnit(TimeUnit.SECONDS))))

      exec(test(hist.time) >> test(gauge.time))
    }
  }

  property("timing an operation with labels delegates to observeDuration") {
    forAll { (dur: (FiniteDuration, String), durs: List[(FiniteDuration, String)]) =>
      def test(
          f: (WriterT[IO, List[(Double, String)], Unit], String) => WriterT[IO, List[(Double, String)], Unit]
      ): IO[Unit] =
        NonEmptyList(dur, durs).traverse { case (d, s) => f(WriterT.liftF(IO.sleep(d)), s) }.written
          .map(res => assertEquals(res, (dur :: durs).map { case (dur, l) => dur.toUnit(TimeUnit.SECONDS) -> l }))

      exec(test(labelledHistogram.time) >> test(labelledGauge.time))
    }
  }

  property("attempting a successful operation delegates to observeDuration") {
    forAll { (dur: (FiniteDuration, String), durs: List[(FiniteDuration, String)]) =>
      def test(
          f: WriterT[IO, List[(Double, String)], String] => WriterT[IO, List[(Double, String)], String]
      ): IO[Unit] =
        NonEmptyList(dur, durs).traverse { case (d, s) => f(WriterT.liftF(IO.sleep(d).as(s))) }.written
          .map(res => assertEquals(res, (dur :: durs).map { case (dur, l) => dur.toUnit(TimeUnit.SECONDS) -> l }))

      exec(
        test(labelledHistogram.timeAttempt[String](_, identity, { case th => th.getMessage })) >> test(
          labelledGauge.timeAttempt[String](_, identity, { case th => th.getMessage })
        )
      )
    }
  }

  // This _must_ be done with a Ref as a Writer will not record anything if an operation fails
  property("attempting a failed operation delegates to observeDuration") {
    forAll { (dur: (FiniteDuration, String), durs: List[(FiniteDuration, String)]) =>
      def test(
          f: (Ref[IO, List[(Double, String)]], IO[String]) => IO[String]
      ): IO[Unit] =
        for {
          ref <- Ref.of[IO, List[(Double, String)]](List.empty)
          _ <- NonEmptyList(dur, durs).traverse { case (d, s) =>
            f(ref, IO.sleep(d) >> IO.raiseError(new RuntimeException(s))).attempt
          }
          res <- ref.get
        } yield assertEquals(res, (dur :: durs).map { case (dur, l) => dur.toUnit(TimeUnit.SECONDS) -> l })

      def gaugeSet(ref: Ref[IO, List[(Double, String)]]): (Double, String) => IO[Unit] =
        (d, s) => ref.update(_ :+ (d -> s))

      exec(
        test((ref, s) =>
          Timer.Labelled
            .fromHistogram(Histogram.Labelled.make[IO, Double, String]((d, s) => ref.update(_ :+ (d -> s))))
            .timeAttempt[String](s, identity, { case th => th.getMessage })
        ) >> test((ref, s) =>
          Timer.Labelled
            .fromGauge(
              Gauge.Labelled.make[IO, Double, String](
                1.0,
                gaugeSet(ref),
                gaugeSet(ref),
                gaugeSet(ref),
                s => Clock[IO].realTime.flatMap(dur => gaugeSet(ref)(dur.toSeconds.toDouble, s))
              )
            )
            .timeAttempt[String](s, identity, { case th => th.getMessage })
        )
      )
    }
  }

}
