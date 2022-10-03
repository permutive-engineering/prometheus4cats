package com.permutive.metrics.internal

/** Type class supporting accessing both the init (all but the last element) and
  * the last element of a tuple.
  */
sealed trait InitLast[A, B] extends Serializable {

  type C <: Product

  def init(c: C): A

  def last(c: C): B

}

private[metrics] trait LowPriorityInitLast {

  implicit def base[A, B]: InitLast.Aux[A, B, (A, B)] =
    InitLast.make(_._1, _._2)

}

object InitLast extends PlatformSpecificInitLast {

  def apply[A, B, C](implicit
      ev: InitLast.Aux[A, B, C]
  ): InitLast.Aux[A, B, C] = ev

  type Aux[A, B, Out] = InitLast[A, B] { type C = Out }

//  object Aux extends PlatformSpecificInitLast

  private[internal] def make[A, B, Out <: Product](
      _init: Out => A,
      _last: Out => B
  ): InitLast.Aux[A, B, Out] =
    new InitLast[A, B] {
      override type C = Out
      override def init(c: C): A = _init(c)
      override def last(c: C): B = _last(c)
    }

//  implicit def default1[C0 <: Product, A <: Product, B](implicit
//      @nowarn prepend: Prepend.Aux[A, Tuple1[B], C0],
//      initEv: Init.Aux[C0, A],
//      lastEv: Last.Aux[C0, B]
//  ): InitLast.Aux[A, B, C0] = InitLast.make(initEv.apply, lastEv.apply)
}
