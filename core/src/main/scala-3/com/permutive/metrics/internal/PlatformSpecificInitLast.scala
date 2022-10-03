package com.permutive.metrics.internal

trait PlatformSpecificInitLast extends LowPriorityInitLast {


  inline given default[C <: NonEmptyTuple, A <: Tuple.Init[C], B <: Tuple.Last[C]]: InitLast.Aux[Tuple.Init[C], Tuple.Last[C], C] = InitLast.make(_.init, _.last)
}
