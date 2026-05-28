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

package prometheus4cats.javaclient

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.Show
import cats.data.NonEmptySeq
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._

import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot
import io.prometheus.metrics.model.snapshots.InfoSnapshot
import io.prometheus.metrics.model.snapshots.SummarySnapshot
import munit.CatsEffectSuite
import prometheus4cats.Metric.CommonLabels
import prometheus4cats._
import prometheus4cats.testkit.CallbackRegistrySuite
import prometheus4cats.testkit.MetricRegistrySuite
import prometheus4cats.util.NameUtils

/** Wires the comprehensive testkit's `MetricRegistrySuite` + `CallbackRegistrySuite` (~47 property-based tests covering
  * register/observe/de-register lifecycles, exemplars, callbacks, duplicate-name detection, nested resource usage,
  * concurrent resource usage) onto the new javaclient backend.
  *
  * Translates the legacy adapter's `state.metricFamilySamples().asScala` snapshot extraction to the 1.x equivalent —
  * `state.scrape().asScala` returning typed snapshots — while the testkit's expected-shape contracts remain the same.
  */
@SuppressWarnings(Array("all"))
class JavaMetricRegistryTestkitSuite
    extends CatsEffectSuite
    with MetricRegistrySuite[PrometheusRegistry]
    with CallbackRegistrySuite[PrometheusRegistry] {

  implicit override val exemplar: Exemplar[IO] = new Exemplar[IO] {

    override def get: IO[Option[Exemplar.Labels]] =
      IO(Exemplar.Labels.of(Exemplar.LabelName("test") -> "test").toOption)

  }

  override val stateResource: Resource[IO, PrometheusRegistry] =
    Resource.eval(IO.delay(new PrometheusRegistry()))

  override def metricRegistryResource(state: PrometheusRegistry): Resource[IO, MetricRegistry[IO]] =
    JavaMetricRegistry.Builder[IO]().withRegistry(state).build

  override def callbackRegistryResource(state: PrometheusRegistry): Resource[IO, CallbackRegistry[IO]] =
    JavaMetricRegistry.Builder[IO]().withRegistry(state).withCallbackTimeout(100.millis).build

  // ─── snapshot lookup helpers ──────────────────────────────────────────────────────────────────────
  //
  // Counters: upstream stores the metric under the base name (the `_total` suffix is stripped by the
  // builder and re-added by the exposition writer). Lookups against a Counter.Name (which the
  // prometheus4cats refined type guarantees ends in `_total`) need to try both forms — the base
  // (matches an upstream-stored CounterSnapshot) AND the full name (matches if upstream changed
  // behaviour or for non-Counter callers passing a `_total`-suffixed string).
  //
  // For other metric kinds, getName matches the user-supplied name verbatim.

  private def matchesName[A: Show](snapshotName: String, name: A): Boolean = {
    val full = name.show
    val base = if (full.endsWith("_total")) full.dropRight("_total".length) else full
    snapshotName == full || snapshotName == base
  }

  private def labelsToMap(labels: io.prometheus.metrics.model.snapshots.Labels): Map[String, String] =
    labels.asScala.map(l => l.getName -> l.getValue).toMap

  private def exemplarToMap(
      exemplar: io.prometheus.metrics.model.snapshots.Exemplar
  ): Option[Map[String, String]] =
    Option(exemplar).map(_.getLabels.asScala.map(l => l.getName -> l.getValue).toMap)

  private def findCounterDataPoint(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      allLabels: Map[String, String]
  ): Option[CounterSnapshot.CounterDataPointSnapshot] = {
    val rendered = NameUtils.makeName(prefix, name)
    state
      .scrape()
      .asScala
      .collect {
        case s: CounterSnapshot
            if matchesName(
              s.getMetadata.getName,
              prefix.fold(name.value)(p => s"${p.value}_${name.value}").asInstanceOf[String]
            ) =>
          s
      }
      .find(s =>
        matchesName(s.getMetadata.getName, rendered) || s.getMetadata.getName == rendered.dropRight("_total".length)
      )
      .flatMap(_.getDataPoints.asScala.find(dp => labelsToMap(dp.getLabels) == allLabels))
  }

  private def findGaugeDataPoint(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      allLabels: Map[String, String]
  ): Option[GaugeSnapshot.GaugeDataPointSnapshot] = {
    val rendered = NameUtils.makeName(prefix, name)
    state
      .scrape()
      .asScala
      .collectFirst { case s: GaugeSnapshot if s.getMetadata.getName == rendered => s }
      .flatMap(_.getDataPoints.asScala.find(dp => labelsToMap(dp.getLabels) == allLabels))
  }

  private def findHistogramDataPoint(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      allLabels: Map[String, String]
  ): Option[HistogramSnapshot.HistogramDataPointSnapshot] = {
    val rendered = NameUtils.makeName(prefix, name)
    state
      .scrape()
      .asScala
      .collectFirst { case s: HistogramSnapshot if s.getMetadata.getName == rendered => s }
      .flatMap(_.getDataPoints.asScala.find(dp => labelsToMap(dp.getLabels) == allLabels))
  }

  private def findSummaryDataPoint(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      allLabels: Map[String, String]
  ): Option[SummarySnapshot.SummaryDataPointSnapshot] = {
    val rendered = NameUtils.makeName(prefix, name)
    state
      .scrape()
      .asScala
      .collectFirst { case s: SummarySnapshot if s.getMetadata.getName == rendered => s }
      .flatMap(_.getDataPoints.asScala.find(dp => labelsToMap(dp.getLabels) == allLabels))
  }

  private def findInfoDataPoint(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      allLabels: Map[String, String]
  ): Option[InfoSnapshot.InfoDataPointSnapshot] = {
    // Upstream stores Info under the base name (without `_info`). The full name with `_info` is
    // re-added by the exposition writer.
    val full = NameUtils.makeName(prefix, name)
    val base = if (full.endsWith("_info")) full.dropRight("_info".length) else full
    state
      .scrape()
      .asScala
      .collectFirst { case s: InfoSnapshot if s.getMetadata.getName == base || s.getMetadata.getName == full => s }
      .flatMap(_.getDataPoints.asScala.find(dp => labelsToMap(dp.getLabels) == allLabels))
  }

  // ─── testkit overrides ────────────────────────────────────────────────────────────────────────────

  private def allLabelsMap(commonLabels: CommonLabels, extra: Map[Label.Name, String]): Map[String, String] =
    (extra ++ commonLabels.value).map { case (n, v) => n.value -> v }

  override def getCounterValue(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[Option[Double]] =
    IO(findCounterDataPoint(state, prefix, name, allLabelsMap(commonLabels, extraLabels)).map(_.getValue))

  override def getExemplarCounterValue(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Counter.Name,
      help: Metric.Help,
      commonLabels: CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[Option[(Double, Option[Map[String, String]])]] =
    IO(
      findCounterDataPoint(state, prefix, name, allLabelsMap(commonLabels, extraLabels))
        .map(dp => dp.getValue -> exemplarToMap(dp.getExemplar))
    )

  override def getGaugeValue(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Gauge.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[Option[Double]] =
    IO(findGaugeDataPoint(state, prefix, name, allLabelsMap(commonLabels, extraLabels)).map(_.getValue))

  override def getHistogramValue(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: CommonLabels,
      buckets: NonEmptySeq[Double],
      extraLabels: Map[Label.Name, String]
  ): IO[Option[Map[String, Double]]] =
    getExemplarHistogramValue(state, prefix, name, help, commonLabels, buckets, extraLabels).map(
      _.map(_.map { case (k, (v, _)) => k -> v })
    )

  override def getExemplarHistogramValue(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Histogram.Name,
      help: Metric.Help,
      commonLabels: Metric.CommonLabels,
      buckets: NonEmptySeq[Double],
      extraLabels: Map[Label.Name, String]
  ): IO[Option[Map[String, (Double, Option[Map[String, String]])]]] =
    IO {
      findHistogramDataPoint(state, prefix, name, allLabelsMap(commonLabels, extraLabels)).map { dp =>
        // The testkit's expected shape is a Map keyed by bucket upper-bound string (matching the
        // simpleclient `le` label values) → (CUMULATIVE count, optional exemplar). Upstream's
        // ClassicHistogramBucket.getCount returns PER-BUCKET counts, so we scan the buckets
        // (sorted ascending by upper-bound) and accumulate counts into cumulative form to match
        // the wire-format / testkit expectation.
        //
        // Exemplars are per-bucket: an exemplar with value v belongs to the smallest bucket whose
        // upper-bound >= v (i.e. the "exclusive lower" / "inclusive upper" range it actually
        // landed in). The cumulative count rule does NOT apply to exemplars.
        val classicBuckets   = dp.getClassicBuckets.asScala.toList.sortBy(_.getUpperBound)
        val cumulativeCounts = classicBuckets.scanLeft(0L)((acc, b) => acc + b.getCount).tail
        val allExemplars     = Option(dp.getExemplars).map(_.asScala.toList).getOrElse(Nil)
        val lowerBounds      = Double.NegativeInfinity +: classicBuckets.map(_.getUpperBound).init
        classicBuckets
          .zip(cumulativeCounts)
          .zip(lowerBounds)
          .map { case ((b, cumCount), lower) =>
            val key = if (b.getUpperBound == Double.PositiveInfinity) "+Inf" else doubleToGoString(b.getUpperBound)
            val maybeExemplar: Option[Map[String, String]] =
              allExemplars.find(e => e.getValue > lower && e.getValue <= b.getUpperBound).map { e =>
                e.getLabels.asScala.map(l => l.getName -> l.getValue).toMap
              }
            key -> (cumCount.toDouble, maybeExemplar)
          }
          .toMap
      }
    }

  override def getSummaryValue(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Summary.Name,
      help: Metric.Help,
      commonLabels: CommonLabels,
      extraLabels: Map[Label.Name, String]
  ): IO[(Option[Map[String, Double]], Option[Long], Option[Double])] =
    IO {
      val dpOpt = findSummaryDataPoint(state, prefix, name, allLabelsMap(commonLabels, extraLabels))
      val quantiles = dpOpt.map { dp =>
        dp.getQuantiles.asScala.map(q => doubleToGoString(q.getQuantile) -> q.getValue).toMap
      }
      val count = dpOpt.map(_.getCount)
      val sum   = dpOpt.map(_.getSum)
      (quantiles, count, sum)
    }

  override def getInfoValue(
      state: PrometheusRegistry,
      prefix: Option[Metric.Prefix],
      name: Info.Name,
      help: Metric.Help,
      labels: Map[Label.Name, String]
  ): IO[Option[Double]] =
    IO(findInfoDataPoint(state, prefix, name, labels.map { case (n, v) => n.value -> v }).map(_ => 1.0))

  /** Local copy of simpleclient's Collector.doubleToGoString — used to format a bucket upper-bound (e.g. 0.005) into
    * the canonical Go-style string the testkit expects (e.g. "0.005"). The 1.x library doesn't expose an equivalent
    * helper publicly, so we reimplement the format inline.
    */
  private def doubleToGoString(d: Double): String = {
    if (d == Double.PositiveInfinity) "+Inf"
    else if (d == Double.NegativeInfinity) "-Inf"
    else if (java.lang.Double.isNaN(d)) "NaN"
    else {
      val s = d.toString
      // Scala's Double.toString uses E-notation in some ranges; the testkit and simpleclient use
      // a consistent decimal form for typical bucket boundaries, which Scala's toString matches
      // for the values exercised by the property-based tests here.
      s
    }
  }

}
