package com.permutive.metrics

object Fixtures {
  val alphaChars: Set[Char] = (('a' to 'z') ++ ('A' to 'Z')).toSet
  val ordinaryChars: Set[Char] = alphaChars ++ ('0' to '9')
}
