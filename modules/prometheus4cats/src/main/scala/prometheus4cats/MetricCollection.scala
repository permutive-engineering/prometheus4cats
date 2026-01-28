/*
 * Copyright 2022-2026 Permutive Ltd. <https://permutive.com>
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

package prometheus4cats

import cats.data.NonEmptySeq
import cats.kernel.Monoid
import cats.syntax.semigroup._

final class MetricCollection private (
    val counters: Map[(Counter.Name, IndexedSeq[Label.Name]), List[MetricCollection.Value.Counter]],
    val gauges: Map[(Gauge.Name, IndexedSeq[Label.Name]), List[MetricCollection.Value.Gauge]],
    val histograms: Map[(Histogram.Name, IndexedSeq[Label.Name]), List[MetricCollection.Value.Histogram]],
    val summaries: Map[(Summary.Name, IndexedSeq[Label.Name]), List[MetricCollection.Value.Summary]]
) {

  def ++(other: MetricCollection) = new MetricCollection(
    counters |+| other.counters,
    gauges |+| other.gauges,
    histograms |+| other.histograms,
    summaries |+| other.summaries
  )

  def appendLongCounter(
      name: Counter.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name],
      values: List[(Long, IndexedSeq[String])]
  ): MetricCollection =
    new MetricCollection(
      counters |+| Map(
        (name, labelNames) -> values.map { case (v, ls) =>
          MetricCollection.Value.LongCounter(help, ls, v)
        }
      ),
      gauges,
      histograms,
      summaries
    )

  def appendLongCounter(
      name: Counter.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String],
      value: Long
  ): MetricCollection =
    appendLongCounter(name, help, labels.keys.toIndexedSeq, List(value -> labels.values.toIndexedSeq))

  def appendDoubleCounter(
      name: Counter.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name],
      values: List[(Double, IndexedSeq[String])]
  ): MetricCollection =
    new MetricCollection(
      counters |+| Map(
        (name, labelNames) -> values.map { case (v, ls) =>
          MetricCollection.Value.DoubleCounter(help, ls, v)
        }
      ),
      gauges,
      histograms,
      summaries
    )

  def appendDoubleCounter(
      name: Counter.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String],
      value: Double
  ): MetricCollection =
    appendDoubleCounter(name, help, labels.keys.toIndexedSeq, List(value -> labels.values.toIndexedSeq))

  def appendLongGauge(
      name: Gauge.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name],
      values: List[(Long, IndexedSeq[String])]
  ): MetricCollection =
    new MetricCollection(
      counters,
      gauges |+| Map((name, labelNames) -> values.map { case (v, ls) =>
        MetricCollection.Value.LongGauge(help, ls, v)
      }),
      histograms,
      summaries
    )

  def appendLongGauge(
      name: Gauge.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String],
      value: Long
  ): MetricCollection = appendLongGauge(name, help, labels.keys.toIndexedSeq, List(value -> labels.values.toIndexedSeq))

  def appendDoubleGauge(
      name: Gauge.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name],
      values: List[(Double, IndexedSeq[String])]
  ): MetricCollection =
    new MetricCollection(
      counters,
      gauges |+| Map((name, labelNames) -> values.map { case (v, ls) =>
        MetricCollection.Value.DoubleGauge(help, ls, v)
      }),
      histograms,
      summaries
    )

  def appendDoubleGauge(
      name: Gauge.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String],
      value: Double
  ): MetricCollection =
    appendDoubleGauge(name, help, labels.keys.toIndexedSeq, List(value -> labels.values.toIndexedSeq))

  def appendLongHistogram(
      name: Histogram.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Long],
      values: List[(Histogram.Value[Long], IndexedSeq[String])]
  ): MetricCollection =
    new MetricCollection(
      counters,
      gauges,
      histograms |+| Map((name, labelNames) -> values.map { case (v, ls) =>
        MetricCollection.Value.LongHistogram(buckets, help, ls, v)
      }),
      summaries
    )

  def appendLongHistogram(
      name: Histogram.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String],
      buckets: NonEmptySeq[Long],
      value: Histogram.Value[Long]
  ): MetricCollection =
    appendLongHistogram(name, help, labels.keys.toIndexedSeq, buckets, List(value -> labels.values.toIndexedSeq))

  def appendDoubleHistogram(
      name: Histogram.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name],
      buckets: NonEmptySeq[Double],
      values: List[(Histogram.Value[Double], IndexedSeq[String])]
  ): MetricCollection =
    new MetricCollection(
      counters,
      gauges,
      histograms |+| Map((name, labelNames) -> values.map { case (v, ls) =>
        MetricCollection.Value.DoubleHistogram(buckets, help, ls, v)
      }),
      summaries
    )

  def appendDoubleHistogram(
      name: Histogram.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String],
      buckets: NonEmptySeq[Double],
      value: Histogram.Value[Double]
  ): MetricCollection =
    appendDoubleHistogram(name, help, labels.keys.toIndexedSeq, buckets, List(value -> labels.values.toIndexedSeq))

  def appendLongSummary(
      name: Summary.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name],
      values: List[(Summary.Value[Long], IndexedSeq[String])]
  ): MetricCollection =
    new MetricCollection(
      counters,
      gauges,
      histograms,
      summaries |+| Map((name, labelNames) -> values.map { case (v, ls) =>
        MetricCollection.Value.LongSummary(help, ls, v)
      })
    )

  def appendLongSummary(
      name: Summary.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String],
      value: Summary.Value[Long]
  ): MetricCollection =
    appendLongSummary(name, help, labels.keys.toIndexedSeq, List(value -> labels.values.toIndexedSeq))

  def appendDoubleSummary(
      name: Summary.Name,
      help: Metric.Help,
      labelNames: IndexedSeq[Label.Name],
      values: List[(Summary.Value[Double], IndexedSeq[String])]
  ): MetricCollection =
    new MetricCollection(
      counters,
      gauges,
      histograms,
      summaries |+| Map((name, labelNames) -> values.map { case (v, ls) =>
        MetricCollection.Value.DoubleSummary(help, ls, v)
      })
    )

  def appendDoubleSummary(
      name: Summary.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String],
      value: Summary.Value[Double]
  ): MetricCollection =
    appendDoubleSummary(name, help, labels.keys.toIndexedSeq, List(value -> labels.values.toIndexedSeq))

}

object MetricCollection {

  val empty: MetricCollection = new MetricCollection(Map.empty, Map.empty, Map.empty, Map.empty)

  sealed trait Type

  object Type {

    case object Counter extends Type

    case object Gauge extends Type

    case object Histogram extends Type

    case object Summary extends Type

  }

  sealed trait Value {

    def help: Metric.Help

    def labelValues: IndexedSeq[String]

  }

  object Value {

    sealed trait Counter extends Value

    final case class LongCounter(help: Metric.Help, labelValues: IndexedSeq[String], value: Long) extends Counter

    final case class DoubleCounter(help: Metric.Help, labelValues: IndexedSeq[String], value: Double) extends Counter

    sealed trait Gauge extends Value

    final case class LongGauge(help: Metric.Help, labelValues: IndexedSeq[String], value: Long) extends Gauge

    final case class DoubleGauge(help: Metric.Help, labelValues: IndexedSeq[String], value: Double) extends Gauge

    sealed trait Histogram extends Value

    final case class LongHistogram(
        buckets: NonEmptySeq[Long],
        help: Metric.Help,
        labelValues: IndexedSeq[String],
        value: Histogram.Value[Long]
    ) extends Histogram

    final case class DoubleHistogram(
        buckets: NonEmptySeq[Double],
        help: Metric.Help,
        labelValues: IndexedSeq[String],
        value: Histogram.Value[Double]
    ) extends Histogram

    sealed trait Summary extends Value

    final case class LongSummary(
        help: Metric.Help,
        labelValues: IndexedSeq[String],
        value: Summary.Value[Long]
    ) extends Summary

    final case class DoubleSummary(
        help: Metric.Help,
        labelValues: IndexedSeq[String],
        value: Summary.Value[Double]
    ) extends Summary

  }

  implicit val catsInstances: Monoid[MetricCollection] = new Monoid[MetricCollection] {

    override def empty: MetricCollection = MetricCollection.empty

    override def combine(x: MetricCollection, y: MetricCollection): MetricCollection = x ++ y

  }

}
