package com.permutive.metrics.internal

import shapeless.ops.tuple._

import scala.annotation.nowarn

trait PlatformSpecificInitLast extends LowPriorityInitLast {
  implicit def default[C0 <: Product, A <: Product, B](implicit
      @nowarn prepend: Prepend.Aux[A, Tuple1[B], C0],
      initEv: Init.Aux[C0, A],
      lastEv: Last.Aux[C0, B]
  ): InitLast.Aux[A, B, C0] = InitLast.make(initEv.apply, lastEv.apply)
}
