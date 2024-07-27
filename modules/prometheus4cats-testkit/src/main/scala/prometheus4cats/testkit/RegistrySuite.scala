/*
 * Copyright 2022 Permutive
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package prometheus4cats.testkit

import scala.concurrent.duration._

import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.kernel.Resource

import munit.CatsEffectSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import prometheus4cats.Metric.CommonLabels
import prometheus4cats._

trait RegistrySuite[State] extends ScalaCheckEffectSuite {
  self: CatsEffectSuite =>

  def stateResource: Resource[IO, State]

  private def niceStringGen(initialChar: Option[Gen[Char]] = None): Gen[String] = for {
    c1 <- initialChar.getOrElse(Gen.alphaChar)
    c2 <- Gen.alphaChar
    s  <- Gen.alphaNumStr
  } yield s"$c1$c2$s"

  protected def niceStringArb[A](f: String => Either[String, A], initialChar: Option[Gen[Char]] = None): Arbitrary[A] =
    Arbitrary(
      niceStringGen(initialChar).flatMap(s => f(s).toOption).suchThat(_.nonEmpty).map(_.get)
    )

  implicit val prefixArb: Arbitrary[Metric.Prefix] = niceStringArb(Metric.Prefix.from)

  implicit val counterNameArb: Arbitrary[Counter.Name] = niceStringArb(s => Counter.Name.from(s"${s}_total"))

  implicit val gaugeNameArb: Arbitrary[Gauge.Name] = niceStringArb(s => Gauge.Name.from(s))

  implicit val histogramNameArb: Arbitrary[Histogram.Name] = niceStringArb(s => Histogram.Name.from(s))

  implicit val summaryNameArb: Arbitrary[Summary.Name] = niceStringArb(s => Summary.Name.from(s))

  implicit val maxAgeArb: Arbitrary[Summary.AgeBuckets] = Arbitrary(
    Gen.posNum[Int].flatMap(i => Summary.AgeBuckets.from(i).toOption).suchThat(_.nonEmpty).map(_.get)
  )

  implicit val quantileArb: Arbitrary[Summary.Quantile] = Arbitrary(
    Gen.choose(0.0, 1.0).map(d => Summary.Quantile.from(d).toOption).suchThat(_.nonEmpty).map(_.get)
  )

  implicit val errorArb: Arbitrary[Summary.AllowedError] = Arbitrary(
    Gen.choose(0.0, 1.0).map(d => Summary.AllowedError.from(d).toOption).suchThat(_.nonEmpty).map(_.get)
  )

  implicit val quantileDefinitionArb: Arbitrary[Summary.QuantileDefinition] = Arbitrary(for {
    q <- quantileArb.arbitrary
    e <- errorArb.arbitrary
  } yield Summary.QuantileDefinition(q, e))

  implicit val helpArb: Arbitrary[Metric.Help] = niceStringArb(Metric.Help.from)

  implicit val labelArb: Arbitrary[Label.Name] = niceStringArb(Label.Name.from)

  def prefixedLabelMapArb(initialChar: Gen[Char]): Arbitrary[Map[Label.Name, String]] = Arbitrary(for {
    size <- Gen.choose(0, 10)
    map  <- Gen.mapOfN(size, Gen.zip(niceStringArb(Label.Name.from, Some(initialChar)).arbitrary, niceStringGen()))
  } yield map)

  implicit val labelMapArb: Arbitrary[Map[Label.Name, String]] = prefixedLabelMapArb(Gen.oneOf('a', 'b', 'c'))

  implicit val commonLabelsArb: Arbitrary[Metric.CommonLabels] =
    Arbitrary(
      prefixedLabelMapArb(Gen.oneOf('n', 'o', 'p')).arbitrary.flatMap(map => Gen.oneOf(CommonLabels.from(map).toOption))
    )

  implicit val posFiniteDurationArb: Arbitrary[FiniteDuration] = Arbitrary(Gen.posNum[Long].map(_.nanos))

  def getCounterValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String] = Map.empty
  ): IO[Option[Double]]

  def getExemplarCounterValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String] = Map.empty
  ): IO[Option[(Double, Option[Map[String, String]])]]

  def getGaugeValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String] = Map.empty
  ): IO[Option[Double]]

  def getHistogramValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double],
      extraLabels: Map[Label.Name, String] = Map.empty
  ): IO[Option[Map[String, Double]]]

  def getExemplarHistogramValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double],
      extraLabels: Map[Label.Name, String] = Map.empty
  ): IO[Option[Map[String, (Double, Option[Map[String, String]])]]]

  def getSummaryValue(
      state: State,
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[(Option[Map[String, Double]], Option[Double], Option[Double])]

}
