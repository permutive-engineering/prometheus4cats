package com.permutive.metrics.internal.gauge

import cats.Applicative
import cats.effect.std.Console
import com.permutive.metrics._
import com.permutive.metrics.internal._

final class GaugeDsl[F[_]] private[metrics] (
    metric: Gauge.Name,
    help: Metric.Help
)(implicit F: Applicative[F], C: Console[F])
    extends BuildStep[F, Gauge[F]](
      F.pure(
        Gauge.make[F](C.println, C.println, C.println, C.println("curr time"))
      )
    )
    with FirstLabelStep[F, LabelledGaugeDsl] {

  /** @inheritdoc
    */
  override def label[A]: FirstLabelApply[F, LabelledGaugeDsl, A] =
    (name, toString) =>
      new LabelledGaugeDsl(metric, help, Sized(name), a => Sized(toString(a)))

}
