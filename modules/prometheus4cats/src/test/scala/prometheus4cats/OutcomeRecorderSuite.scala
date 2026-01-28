/*
 * Copyright 2022-2026 Permutive Ltd. <https://permutive.com>
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

import cats.effect.IO
import cats.effect.Ref
import cats.syntax.semigroup._

import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF._
import prometheus4cats.OutcomeRecorder.Status

class OutcomeRecorderSuite extends CatsEffectSuite with ScalaCheckEffectSuite {

  val opCounter: IO[(OutcomeRecorder[IO, Unit], IO[Map[Status, Int]])] =
    Ref.of[IO, Map[Status, Int]](Map.empty).map { ref =>
      OutcomeRecorder.fromCounter(
        Counter.make[IO, Int, (Unit, Status)](
          Counter.ExemplarState.noop[IO],
          (i: Int, labels: (Unit, Status), _: Option[Exemplar.Labels]) => ref.update(_ |+| Map(labels._2 -> i))
        )
      ) -> ref.get
    }

  val opGauge: IO[(OutcomeRecorder[IO, Unit], IO[Map[Status, Int]])] =
    Ref.of[IO, Map[Status, Int]](Map.empty).map { ref =>
      OutcomeRecorder.fromGauge(
        Gauge.make[IO, Int, (Unit, Status)](
          (i: Int, labels: (Unit, Status)) => ref.update(_ |+| Map(labels._2 -> i)),
          (i: Int, labels: (Unit, Status)) => ref.update(_ |+| Map(labels._2 -> -i)),
          (i: Int, labels: (Unit, Status)) => ref.update(_.updated(labels._2, i))
        )
      ) -> ref.get
    }

  val labelledOpCounter: IO[(OutcomeRecorder[IO, String], IO[Map[(String, Status), Int]])] =
    Ref.of[IO, Map[(String, Status), Int]](Map.empty).map { ref =>
      OutcomeRecorder.fromCounter(
        Counter.make[IO, Int, (String, Status)](
          Counter.ExemplarState.noop[IO],
          (i: Int, s: (String, Status), _: Option[Exemplar.Labels]) => ref.update(_ |+| Map(s -> i))
        )
      ) -> ref.get
    }

  val labelledOpGauge: IO[(OutcomeRecorder[IO, String], IO[Map[(String, Status), Int]])] =
    Ref.of[IO, Map[(String, Status), Int]](Map.empty).map { ref =>
      OutcomeRecorder.fromGauge(
        Gauge.make[IO, Int, (String, Status)](
          (i: Int, s: (String, Status)) => ref.update(_ |+| Map(s -> i)),
          (i: Int, s: (String, Status)) => ref.update(_ |+| Map(s -> -i)),
          (i: Int, s: (String, Status)) => ref.update(_.updated(s, i))
        )
      ) -> ref.get
    }

  implicit val posInt: Arbitrary[Int] = Arbitrary(Gen.posNum[Int])

  test("op counter should record success") {
    forAllF { (i: Int) =>
      val nonZero = Math.abs(i) + 1

      opCounter.flatMap { case (counter, res) =>
        counter.surround(IO.unit).replicateA(nonZero) >> res.map(
          assertEquals(_, Map[Status, Int](Status.Succeeded -> nonZero))
        )
      }
    }
  }

  test("op gauge should record success") {
    forAllF { (i: Int) =>
      val nonZero = Math.abs(i) + 1

      opGauge.flatMap { case (gauge, res) =>
        gauge.surround(IO.unit).replicateA(nonZero) >> res.map(
          assertEquals(_, Map[Status, Int](Status.Succeeded -> 1, Status.Errored -> 0, Status.Canceled -> 0))
        )
      }
    }
  }

  test("op counter should record cancelation") {
    forAllF { (i: Int) =>
      val nonZero = Math.abs(i) + 1

      opCounter.flatMap { case (counter, res) =>
        // the deferred in the race here gives time for the finalizers to be registered on the first IO
        IO.deferred[Unit]
          .flatMap(wait => counter.surround(wait.complete(()) >> IO.never).race(wait.get))
          .replicateA(nonZero) >> res
          .map(
            assertEquals(_, Map[Status, Int](Status.Canceled -> nonZero))
          )
      }
    }
  }

  test("op gauge should record cancelation") {
    forAllF { (i: Int) =>
      val nonZero = Math.abs(i) + 1

      opGauge.flatMap { case (gauge, res) =>
        // the deferred in the race here gives time for the finalizers to be registered on the first IO
        IO.deferred[Unit]
          .flatMap(wait => gauge.surround(wait.complete(()) >> IO.never).race(wait.get))
          .replicateA(nonZero) >> res
          .map(
            assertEquals(_, Map[Status, Int](Status.Succeeded -> 0, Status.Errored -> 0, Status.Canceled -> 1))
          )
      }
    }
  }

  test("op counter should record failure") {
    forAllF { (i: Int) =>
      val nonZero = Math.abs(i) + 1

      opCounter.flatMap { case (counter, res) =>
        counter.surround(IO.raiseError(new RuntimeException())).attempt.replicateA(nonZero) >> res.map(
          assertEquals(_, Map[Status, Int](Status.Errored -> nonZero))
        )
      }
    }
  }

  test("op gauge should record failure") {
    forAllF { (i: Int) =>
      val nonZero = Math.abs(i) + 1

      opGauge.flatMap { case (gauge, res) =>
        gauge.surround(IO.raiseError(new RuntimeException())).attempt.replicateA(nonZero) >> res.map(
          assertEquals(_, Map[Status, Int](Status.Succeeded -> 0, Status.Errored -> 1, Status.Canceled -> 0))
        )
      }
    }
  }

  test("op counter should record success with labels") {
    forAllF { (i: Int, s: String) =>
      val nonZero = Math.abs(i) + 1

      labelledOpCounter.flatMap { case (counter, res) =>
        counter.surround(IO.unit, s).replicateA(nonZero) >> res.map(
          assertEquals(_, Map[(String, Status), Int]((s, Status.Succeeded) -> nonZero))
        )
      }
    }
  }

  test("op gauge should record success with labels") {
    forAllF { (i: Int, s: String) =>
      val nonZero = Math.abs(i) + 1

      labelledOpGauge.flatMap { case (gauge, res) =>
        gauge.surround(IO.unit, s).replicateA(nonZero) >> res.map(
          assertEquals(
            _,
            Map[(String, Status), Int](
              (s, Status.Succeeded) -> 1,
              (s, Status.Errored)   -> 0,
              (s, Status.Canceled)  -> 0
            )
          )
        )
      }
    }
  }

  test("op counter should record cancelation with labels") {
    forAllF { (i: Int, s: String) =>
      val nonZero = Math.abs(i) + 1

      labelledOpCounter.flatMap { case (counter, res) =>
        // the deferred in the race here gives time for the finalizers to be registered on the first IO
        IO.deferred[Unit]
          .flatMap(wait => counter.surround(wait.complete(()) >> IO.never, s).race(wait.get))
          .replicateA(nonZero) >> res.map(
          assertEquals(_, Map[(String, Status), Int]((s, Status.Canceled) -> nonZero))
        )
      }
    }
  }

  test("op gauge should record cancelation with labels") {
    forAllF { (i: Int, s: String) =>
      val nonZero = Math.abs(i) + 1

      labelledOpGauge.flatMap { case (gauge, res) =>
        // the deferred in the race here gives time for the finalizers to be registered on the first IO
        IO.deferred[Unit]
          .flatMap(wait => gauge.surround(wait.complete(()) >> IO.never, s).race(wait.get))
          .replicateA(nonZero) >> res.map(
          assertEquals(
            _,
            Map[(String, Status), Int](
              (s, Status.Succeeded) -> 0,
              (s, Status.Errored)   -> 0,
              (s, Status.Canceled)  -> 1
            )
          )
        )
      }
    }
  }

  test("op counter should record failure with labels") {
    forAllF { (i: Int, s: String) =>
      val nonZero = Math.abs(i) + 1

      labelledOpCounter.flatMap { case (counter, res) =>
        counter.surround(IO.raiseError(new RuntimeException()), s).attempt.replicateA(nonZero) >> res.map(
          assertEquals(_, Map[(String, Status), Int]((s, Status.Errored) -> nonZero))
        )
      }
    }
  }

  test("op gauge should record failure with labels") {
    forAllF { (i: Int, s: String) =>
      val nonZero = Math.abs(i) + 1

      labelledOpGauge.flatMap { case (gauge, res) =>
        gauge.surround(IO.raiseError(new RuntimeException()), s).attempt.replicateA(nonZero) >> res.map(
          assertEquals(
            _,
            Map[(String, Status), Int](
              (s, Status.Succeeded) -> 0,
              (s, Status.Errored)   -> 1,
              (s, Status.Canceled)  -> 0
            )
          )
        )
      }
    }
  }

}
