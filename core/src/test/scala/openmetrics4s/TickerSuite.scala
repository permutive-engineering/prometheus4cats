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
