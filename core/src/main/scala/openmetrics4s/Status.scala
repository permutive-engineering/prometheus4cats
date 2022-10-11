package openmetrics4s

import cats.Show

sealed trait Status

object Status {
  case object Succeeded extends Status

  case object Errored extends Status

  case object Canceled extends Status

  implicit val catsInstances: Show[Status] = Show.show {
    case Status.Succeeded => "succeeded"
    case Status.Errored => "errored"
    case Status.Canceled => "canceled"
  }
}
