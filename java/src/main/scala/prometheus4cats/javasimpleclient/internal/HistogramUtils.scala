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

package prometheus4cats.javasimpleclient.internal

import java.util

import cats.data.NonEmptySeq
import io.prometheus.client.Collector
import io.prometheus.client.Collector.{MetricFamilySamples, Type}
import prometheus4cats.util.NameUtils
import prometheus4cats.{Histogram, Label, Metric}

import scala.jdk.CollectionConverters._

object HistogramUtils {
  private[javasimpleclient] def histogramSamples(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Map[Label.Name, String],
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  ): Seq[(Histogram.Value[Double], IndexedSeq[String])] => Collector.MetricFamilySamples = {
    lazy val stringName = NameUtils.makeName(prefix, name)

    lazy val allLabelNames = labelNames.map(_.value).toList ++ commonLabels.keys.map(_.value).toList

    lazy val labelNamesJava: util.List[String] = allLabelNames.asJava
    lazy val labelNamesWithLe: util.List[String] = ("le" :: allLabelNames).asJava
    lazy val commonLabelValues = commonLabels.values.toList

    lazy val bucketsWithInf = buckets.map(Collector.doubleToGoString) :+ "+Inf"
    values => {
      val metrics = values.flatMap { case (value, labelVs) =>
        val labelValues = labelVs.toList ++ commonLabelValues
        val labelValuesJava = labelValues.asJava

        val bucketSamples = bucketsWithInf.zipWith(value.bucketValues) { (bucketString, bucketValue) =>
          val allLabelValues = bucketString :: labelValues

          new MetricFamilySamples.Sample(
            s"${stringName}_bucket",
            labelNamesWithLe,
            allLabelValues.asJava,
            bucketValue
          )
        }

        bucketSamples.toSeq.toIndexedSeq ++ IndexedSeq(
          new MetricFamilySamples.Sample(
            s"${stringName}_count",
            labelNamesJava,
            labelValuesJava,
            value.bucketValues.last
          ),
          new MetricFamilySamples.Sample(
            s"${stringName}_sum",
            labelNamesJava,
            labelValuesJava,
            value.sum
          )
        )

      }

      new MetricFamilySamples(stringName, "", Type.HISTOGRAM, help.value, metrics.asJava)
    }
  }

  private[javasimpleclient] def labelledHistogramSamples(
      help: Metric.Help,
      buckets: NonEmptySeq[Double]
  ): (String, util.List[String], util.List[String], Histogram.Value[Double]) => Collector.MetricFamilySamples = {
    case (name, labelNames, labelValues, value) =>
      labelNames.add(0, "le")

      lazy val bucketsWithInf = buckets.map(Collector.doubleToGoString) :+ "+Inf"
      val metrics = {
        val bucketSamples = bucketsWithInf.zipWith(value.bucketValues) { (bucketString, bucketValue) =>
          labelValues.add(0, bucketString)

          new MetricFamilySamples.Sample(
            s"${name}_bucket",
            labelNames,
            labelValues,
            bucketValue
          )
        }

        bucketSamples.toSeq.toIndexedSeq ++ IndexedSeq(
          new MetricFamilySamples.Sample(
            s"${name}_count",
            labelNames,
            labelValues,
            value.bucketValues.last
          ),
          new MetricFamilySamples.Sample(
            s"${name}_sum",
            labelNames,
            labelValues,
            value.sum
          )
        )

      }

      new MetricFamilySamples(name, "", Type.HISTOGRAM, help.value, metrics.asJava)
  }
}
