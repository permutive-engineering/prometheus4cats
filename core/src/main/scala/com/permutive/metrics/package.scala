package com.permutive

package object metrics extends ShapelessPolyfill {

  /** Starts creating a "gauge" metric.
    *
    * @example
    *   {{{ import com.permutive.metrics._ import eu.timepit.refined.auto._
    *
    * gauge("my_gauge") .help("my gauge help") .label[Int]("first_label")
    * .label[String]("second_label") .label[Boolean]("third_label") .build }}}
    */
//  def gauge[F[_]: Applicative: Console](
//      name: Gauge.Name
//  ): HelpStep[GaugeDsl[F]] =
//    new HelpStep(new GaugeDsl[F](name, _))
}
