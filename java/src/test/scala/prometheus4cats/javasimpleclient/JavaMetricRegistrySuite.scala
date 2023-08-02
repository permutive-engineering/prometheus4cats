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

package prometheus4cats.javasimpleclient

import cats.Show
import cats.data.{NonEmptyList, NonEmptySeq}
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.show._
import io.prometheus.client.CollectorRegistry
import munit.CatsEffectSuite
import org.scalacheck.effect.PropF._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import prometheus4cats.Metric.CommonLabels
import prometheus4cats._
import prometheus4cats.testkit.{CallbackRegistrySuite, MetricRegistrySuite}
import prometheus4cats.util.NameUtils

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._

class JavaMetricRegistrySuite
    extends CatsEffectSuite
    with MetricRegistrySuite[CollectorRegistry]
    with CallbackRegistrySuite[CollectorRegistry] {

  implicit val logger: Logger[IO] = NoOpLogger.impl

  implicit val exemplar: Exemplar[IO] = new Exemplar[IO] {
    override def get: IO[Option[Exemplar.Labels]] = IO(
      Exemplar.Labels.of(Exemplar.LabelName("test") -> "test").toOption
    )
  }

  override val stateResource: Resource[IO, CollectorRegistry] = Resource.eval(IO.delay(new CollectorRegistry()))

  override def metricRegistryResource(state: CollectorRegistry): Resource[IO, MetricRegistry[IO]] =
    JavaMetricRegistry.Builder().withRegistry(state).build[IO]

  override def callbackRegistryResource(state: CollectorRegistry): Resource[IO, CallbackRegistry[IO]] =
    JavaMetricRegistry.Builder().withRegistry(state).withCallbackTimeout(100.millis).build[IO]

  def getMetricValue[A: Show](
      state: CollectorRegistry,
      prefix: Option[Metric.Prefix],
      name: A,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): Option[(Double, Option[Map[String, String]])] = {
    val n = NameUtils.makeName(prefix, name)

    val allLabels = (extraLabels ++ commonLabels.value).map { case (n, v) => n.value -> v }

    while (state.metricFamilySamples().asScala.isEmpty)
      Thread.sleep(10)

    // the prometheus collector registry returns 0.0 when calling `getSampleValue` even if the metric is missing,
    // despite what their Javadoc says
    state
      .metricFamilySamples()
      .asScala
      .toList
      .flatMap(_.samples.asScala.toList)
      .find { sample =>
        val labels = sample.labelNames.asScala.zip(sample.labelValues.asScala).toMap

        sample.name == n && labels == allLabels
      }
      .map { sample =>
        (
          sample.value,
          Option(sample.exemplar).map { exemplar =>
            0.until(exemplar.getNumberOfLabels).foldLeft(Map.empty[String, String]) { case (acc, i) =>
              acc.updated(exemplar.getLabelName(i), exemplar.getLabelValue(i))
            }
          }
        )
      }
  }

  override def getCounterValue(
      state: CollectorRegistry,
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[Option[Double]] = IO(getMetricValue(state, prefix, name, commonLabels, extraLabels).map(_._1))

  override def getExemplarCounterValue(
      state: CollectorRegistry,
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[Option[(Double, Option[Map[String, String]])]] =
    IO(getMetricValue(state, prefix, name, commonLabels, extraLabels))

  override def getGaugeValue(
      state: CollectorRegistry,
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[Option[Double]] = IO(getMetricValue(state, prefix, name, commonLabels, extraLabels).map(_._1))

  override def getHistogramValue(
      state: CollectorRegistry,
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: CommonLabels,
      buckets: NonEmptySeq[Double],
      extraLabels: Map[Label.Name, String]
  ): IO[Option[Map[String, Double]]] =
    getExemplarHistogramValue(state, prefix, name, help, commonLabels, buckets, extraLabels).map(
      _.map(_.view.mapValues(_._1).toMap)
    )

  override def getExemplarHistogramValue(
      state: CollectorRegistry,
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double],
      extraLabels: Map[Label.Name, String]
  ): IO[Option[Map[String, (Double, Option[Map[String, String]])]]] =
    IO {
      val n = NameUtils.makeName(prefix, name)

      val allLabels = (commonLabels.value ++ extraLabels).map { case (n, v) => n.value -> v }

      // the prometheus collector registry returns 0.0 when calling `getSampleValue` even if the metric is missing,
      // despite what their Javadoc says
      state
        .metricFamilySamples()
        .asScala
        .find(_.name == n)
        .map { sample =>
          sample.samples.asScala.filter { sample =>
            val labels = sample.labelNames.asScala.zip(sample.labelValues.asScala).toMap

            labels - "le" == allLabels && labels.contains("le")
          }.map { sample =>
            (
              sample.labelNames.asScala.zip(sample.labelValues.asScala).collectFirst { case ("le", v) => v }.get,
              (
                sample.value,
                Option(sample.exemplar).map { exemplar =>
                  0.until(exemplar.getNumberOfLabels).foldLeft(Map.empty[String, String]) { case (acc, i) =>
                    acc.updated(exemplar.getLabelName(i), exemplar.getLabelValue(i))
                  }
                }
              )
            )
          }.toMap
        }
    }

  override def getSummaryValue(
      state: CollectorRegistry,
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[(Option[Map[String, Double]], Option[Double], Option[Double])] = IO {
    val n = NameUtils.makeName(prefix, name)

    val allLabels = (commonLabels.value ++ extraLabels).map { case (n, v) => n.value -> v }

    // the prometheus collector registry returns 0.0 when calling `getSampleValue` even if the metric is missing,
    // despite what their Javadoc says
    val quantiles = state
      .metricFamilySamples()
      .asScala
      .find(_.name == n)
      .map { sample =>
        sample.samples.asScala.filter { sample =>
          val labels = sample.labelNames.asScala.zip(sample.labelValues.asScala).toMap

          labels - "quantile" == allLabels && labels.contains("quantile")
        }.map { sample =>
          (
            sample.labelNames.asScala.zip(sample.labelValues.asScala).collectFirst { case ("quantile", v) => v }.get,
            sample.value
          )
        }.toMap
      }

    (
      quantiles,
      getMetricValue(state, prefix, show"${name}_count", commonLabels, extraLabels).map(_._1),
      getMetricValue(state, prefix, show"${name}_sum", commonLabels, extraLabels).map(_._1)
    )
  }

  override def getInfoValue(
      state: CollectorRegistry,
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String]
  ): IO[Option[Double]] = IO(getMetricValue(state, prefix, name, CommonLabels.empty, labels).map(_._1))

  test("returns an existing metric when labels and name are the same") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Set[Label.Name]
      ) =>
        stateResource.flatMap(metricRegistryResource).use { reg =>
          val metric = reg
            .createAndRegisterLabelledDoubleCounter[Map[Label.Name, String]](
              prefix,
              name,
              help,
              commonLabels,
              labels.toIndexedSeq
            )(_.values.toIndexedSeq)

          (metric >> metric).use_
        }
    }
  }

  test("fails to build a metric when a callback of the same name exists") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Set[Label.Name]
      ) =>
        stateResource
          .flatMap(JavaMetricRegistry.Builder().withRegistry(_).build)
          .use { reg =>
            val metric = reg
              .createAndRegisterLabelledDoubleCounter[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.toIndexedSeq
              )(_.values.toIndexedSeq)

            val callback = reg
              .registerLabelledDoubleCounterCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.toIndexedSeq,
                IO(NonEmptyList.one(0.0 -> Map.empty[Label.Name, String]))
              )(_.values.toIndexedSeq)

            (callback >> metric).use_
          }
          .attempt
          .map { res =>
            assertEquals(
              res.leftMap(_.getMessage),
              Left(
                s"A callback with the same name as '${NameUtils.makeName(prefix, name)}' is already registered with different labels and/or type"
              )
            )
          }
    }
  }

  test("fails to build a callback when a metric of the same name exists") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Set[Label.Name]
      ) =>
        stateResource
          .flatMap(JavaMetricRegistry.Builder().withRegistry(_).build)
          .use { reg =>
            val metric = reg
              .createAndRegisterLabelledDoubleCounter[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.toIndexedSeq
              )(_.values.toIndexedSeq)

            val callback = reg
              .registerLabelledDoubleCounterCallback[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.toIndexedSeq,
                IO(NonEmptyList.one(0.0 -> Map.empty[Label.Name, String]))
              )(_.values.toIndexedSeq)

            (metric >> callback).use_
          }
          .attempt
          .map { res =>
            assertEquals(
              res.leftMap(_.getMessage),
              Left(
                s"A metric with the same name as '${NameUtils.makeName(prefix, name)}' is already registered with different labels and/or type"
              )
            )
          }
    }
  }

  test("fails when a metric with the same name and different labels") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Counter.Name,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Set[Label.Name],
          labelName2: Label.Name
      ) =>
        stateResource
          .flatMap(metricRegistryResource)
          .use { reg =>
            (reg
              .createAndRegisterLabelledDoubleCounter[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                labels.toIndexedSeq
              )(_.values.toIndexedSeq) >> reg
              .createAndRegisterLabelledDoubleCounter[Map[Label.Name, String]](
                prefix,
                name,
                help,
                commonLabels,
                IndexedSeq(labelName2)
              )(_.values.toIndexedSeq)).use_
          }
          .attempt
          .map { res =>
            assertEquals(
              res.leftMap(_.getMessage),
              Left(
                s"A metric with the same name as '${NameUtils.makeName(prefix, name)}' is already registered with different labels and/or type"
              )
            )
          }

    }
  }

  test("fails when a metric with the same name and different type") {
    forAllF {
      (
          prefix: Option[Metric.Prefix],
          name: Metric.Prefix,
          help: Metric.Help,
          commonLabels: Metric.CommonLabels,
          labels: Set[Label.Name]
      ) =>
        val counterName = Counter.Name.from(s"${name.value}_total").toOption.get
        val gaugeName = Gauge.Name.from(name.value).toOption.get

        stateResource
          .flatMap(metricRegistryResource)
          .use { reg =>
            (reg
              .createAndRegisterLabelledDoubleCounter[Map[Label.Name, String]](
                prefix,
                counterName,
                help,
                commonLabels,
                labels.toIndexedSeq
              )(_.values.toIndexedSeq) >> reg
              .createAndRegisterLabelledDoubleGauge[Map[Label.Name, String]](
                prefix,
                gaugeName,
                help,
                commonLabels,
                labels.toIndexedSeq
              )(_.values.toIndexedSeq)).use_
          }
          .attempt
          .map { res =>
            assertEquals(
              res.leftMap(_.getMessage),
              Left(
                s"A metric with the same name as '${NameUtils.makeName(prefix, gaugeName)}' is already registered with different labels and/or type"
              )
            )
          }
    }
  }
}
