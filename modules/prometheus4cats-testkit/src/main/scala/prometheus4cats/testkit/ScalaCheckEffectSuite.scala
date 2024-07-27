/*
 * Copyright 2021 Typelevel
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

import cats.implicits._

import munit.Exceptions
import munit.Location
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Test.PropException
import org.scalacheck.effect.PropF
import org.scalacheck.rng.Seed
import org.scalacheck.util.Pretty
import org.scalacheck.{Test => ScalaCheckTest}

/** COPIED FROM https://github.com/typelevel/scalacheck-effect until MUnit 1.0 compatible version is released.
  *
  * Extends `ScalaCheckSuite`, adding support for evaluation of effectful properties (`PropF[F]` values).
  *
  * This trait transforms tests which return `PropF[F]` values in to `F[Unit]` values. The `F[Unit]` values are
  * transformed to a `Future[Unit]` via `munitValueTransform`. Hence, an appropriate value transform must be registered
  * for the effect type in use. This is typically done by mixing in an MUnit compatibility trait for the desired effect
  * type.
  */
@SuppressWarnings(Array("scalafix:DisableSyntax.valInAbstract"))
private[testkit] trait ScalaCheckEffectSuite extends ScalaCheckSuite {

  private val initialSeed: Seed =
    scalaCheckTestParameters.initialSeed.getOrElse(
      Seed.fromBase64(scalaCheckInitialSeed).get
    )

  private val genParameters: Gen.Parameters =
    Gen.Parameters.default
      .withLegacyShrinking(scalaCheckTestParameters.useLegacyShrinking)
      .withInitialSeed(initialSeed)

  override def munitValueTransforms: List[ValueTransform] = {
    val testResultTransform =
      new ValueTransform(
        "ScalaCheck TestResult",
        { case p: ScalaCheckTest.Result =>
          super.munitValueTransform(parseTestResult(p))
        }
      )

    super.munitValueTransforms :+ scalaCheckPropFValueTransform :+ testResultTransform
  }

  private val scalaCheckPropFValueTransform: ValueTransform =
    new ValueTransform(
      "ScalaCheck PropF",
      { case p: PropF[f] =>
        super.munitValueTransform(checkPropF[f](p))
      }
    )

  private def checkPropF[F[_]](prop: PropF[F])(implicit loc: Location): F[Unit] = {
    import prop.F
    prop.check(scalaCheckTestParameters, genParameters).map(fixResultException).map(parseTestResult)
  }

  private def parseTestResult(result: ScalaCheckTest.Result)(implicit loc: Location): Unit = {
    if (!result.passed) {
      val seed = genParameters.initialSeed.get
      val seedMessage =
        s"""|Failing seed: ${seed.toBase64}
            |You can reproduce this failure by adding the following override to your suite:
            |
            |  override def scalaCheckInitialSeed = "${seed.toBase64}"
            |""".stripMargin
      fail(seedMessage + "\n" + Pretty.pretty(result, scalaCheckPrettyParameters))
    }
  }

  private def fixResultException(result: ScalaCheckTest.Result): ScalaCheckTest.Result =
    result.copy(
      status = result.status match {
        case p @ PropException(_, e, _) => p.copy(e = Exceptions.rootCause(e))
        case default                    => default
      }
    )

}

object ScalaCheckEffectSuite {}
