/*
 * Copyright 2022-2025 Permutive Ltd. <https://permutive.com>
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

import scala.jdk.CollectionConverters._

import cats.data.NonEmptySeq

import io.prometheus.client.Collector
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import prometheus4cats.Histogram
import prometheus4cats.Label
import prometheus4cats.Metric
import prometheus4cats.util.NameUtils

private[javasimpleclient] object HistogramUtils {

  private[javasimpleclient] def histogramSamples(
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Map[Label.Name, String],
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double]
  ): Seq[(Histogram.Value[Double], IndexedSeq[String])] => Collector.MetricFamilySamples = {
    lazy val stringName    = NameUtils.makeName(prefix, name)
    lazy val allLabelNames = labelNames.map(_.value).toList ++ commonLabels.keys.map(_.value).toList
    values => {
      val samples = values.flatMap { case (value, labelValues) =>
        metricSamples(buckets, stringName, allLabelNames, labelValues.toList, value)
      }
      new MetricFamilySamples(stringName, "", Type.HISTOGRAM, help.value, samples.asJava)
    }
  }

  private[javasimpleclient] def labelledHistogramSamples(
      help: Metric.Help,
      buckets: NonEmptySeq[Double]
  ): (String, List[String], List[String], Histogram.Value[Double]) => Collector.MetricFamilySamples = {
    case (name, labelNames, labelValues, value) =>
      val samples = metricSamples(buckets, name, labelNames, labelValues, value)
      new MetricFamilySamples(name, "", Type.HISTOGRAM, help.value, samples.asJava)
  }

  private def metricSamples(
      buckets: NonEmptySeq[Double],
      name: String,
      labelNames: List[String],
      labelValues: List[String],
      value: Histogram.Value[Double]
  ): List[MetricFamilySamples.Sample] = {
    val enhancedLabelNames = "le" :: labelNames

    val bucketsWithInf = buckets.map(Collector.doubleToGoString) :+ "+Inf"
    val bucketSamples = bucketsWithInf.zipWith(value.bucketValues) { (bucketString, bucketValue) =>
      val enhancedLabelValues = bucketString :: labelValues

      new MetricFamilySamples.Sample(
        s"${name}_bucket",
        enhancedLabelNames.asJava,
        enhancedLabelValues.asJava,
        bucketValue
      )
    }

    (bucketSamples.toSeq ++ List(
      new MetricFamilySamples.Sample(
        s"${name}_count",
        labelNames.asJava,
        labelValues.asJava,
        value.bucketValues.last
      ),
      new MetricFamilySamples.Sample(
        s"${name}_sum",
        labelNames.asJava,
        labelValues.asJava,
        value.sum
      )
    )).toList

  }

}
