package com.permutive.metrics

trait ShapelessPolyfill {

  type Nat = shapeless.Nat
  object Nat {
    type _1 = shapeless.Nat._1
  }

  type Succ[N <: Nat] = shapeless.Succ[N]

  val Succ = shapeless.Succ

  type Sized[+Repr, L <: Nat] = shapeless.Sized[Repr, L]

  val Sized = shapeless.Sized

}
