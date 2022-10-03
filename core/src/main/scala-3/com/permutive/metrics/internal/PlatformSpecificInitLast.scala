package com.permutive.metrics.internal

trait PlatformSpecificInitLast extends LowPriorityInitLast {
  type NonEmptyAppend[X <: Tuple, Y] <: NonEmptyTuple = X match {
    case EmptyTuple => Y *: EmptyTuple
    case x *: xs    => x *: NonEmptyAppend[xs, Y]
  }

  implicit def default[A <: NonEmptyTuple, B, Out <: NonEmptyAppend[A, B]]
      : InitLast.Aux[A, B, Out] =
    InitLast.make(_.init.asInstanceOf[A], _.last.asInstanceOf[B])
}
