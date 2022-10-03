package com.permutive.metrics.internal.gauge

import cats.Applicative
import cats.effect.std.Console
import com.permutive.metrics._
import com.permutive.metrics.internal._

final class LabelledGaugeDsl[F[_], T, N <: Nat] private[gauge] (
    metric: Gauge.Name,
    help: Metric.Help,
    labelNames: Sized[IndexedSeq[Label.Name], N],
    f: T => Sized[IndexedSeq[String], N]
)(implicit F: Applicative[F], C: Console[F])
    extends BuildStep[F, LabelledGauge[F, T]](
      F.pure(
        LabelledGauge.make[F, T](
          (d, t) => C.println((d, labelNames.unsized.zip(f(t).unsized))),
          (d, t) => C.println((d, t)),
          (d, t) => C.println((d, t)),
          C.println
        )
      )
    )
    with NextLabelsStep[F, T, N, LabelledGaugeDsl] {

  /** @inheritdoc
    */
  override def label[B]: LabelApply[F, T, N, LabelledGaugeDsl, B] =
    new LabelApply[F, T, N, LabelledGaugeDsl, B] {

      override def apply[C: InitLast.Aux[T, B, *]](
          name: Label.Name,
          toString: B => String
      ): LabelledGaugeDsl[F, C, Succ[N]] = new LabelledGaugeDsl(
        metric,
        help,
        labelNames :+ name,
        c => f(InitLast[T, B, C].init(c)) :+ toString(InitLast[T, B, C].last(c))
      )

    }

}
