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

package com.permutive.metrics

import cats.{Applicative, Monad, ~>}
import com.permutive.metrics.Metric.CommonLabels
import com.permutive.metrics.internal._
import com.permutive.metrics.internal.counter.CounterDsl
import com.permutive.metrics.internal.gauge.GaugeDsl
import com.permutive.metrics.internal.histogram.BucketDsl

sealed abstract class MetricsFactory[F[_]](
    registry: MetricsRegistry[F],
    prefix: Option[Metric.Prefix],
    suffix: Option[Metric.Suffix],
    commonLabels: CommonLabels
) {
  def mapK[G[_]: Monad: RecordAttempt](fk: F ~> G): MetricsFactory[G] =
    new MetricsFactory[G](
      MetricsRegistry.mapK(registry, fk),
      prefix,
      suffix,
      commonLabels
    ) {}

  def gauge(name: Gauge.Name): HelpStep[GaugeDsl[F]] = new HelpStep(
    new GaugeDsl[F](registry, prefix, suffix, name, _, commonLabels)
  )

  def counter(name: Counter.Name): HelpStep[CounterDsl[F]] =
    new HelpStep[CounterDsl[F]](
      new CounterDsl[F](registry, prefix, suffix, name, _, commonLabels)
    )

  def histogram(name: Histogram.Name): HelpStep[BucketDsl[F]] =
    new HelpStep[BucketDsl[F]](
      new BucketDsl[F](registry, prefix, suffix, name, _, commonLabels)
    )

  def dropPrefix: MetricsFactory[F] = new MetricsFactory[F](registry, None, suffix, commonLabels) {}

  def withPrefix(prefix: Metric.Prefix): MetricsFactory[F] =
    new MetricsFactory[F](registry, Some(prefix), suffix, commonLabels) {}

  def dropSuffix: MetricsFactory[F] = new MetricsFactory[F](registry, prefix, None, commonLabels) {}

  def withSuffix(suffix: Metric.Suffix): MetricsFactory[F] =
    new MetricsFactory[F](registry, prefix, Some(suffix), commonLabels) {}
}

object MetricsFactory {
  def noop[F[_]: Applicative]: MetricsFactory[F] =
    new MetricsFactory[F](
      MetricsRegistry.noop,
      None,
      None,
      CommonLabels.empty
    ) {}

  class Builder private[metrics] (
      prefix: Option[Metric.Prefix] = None,
      suffix: Option[Metric.Suffix] = None,
      commonLabels: CommonLabels = CommonLabels.empty
  ) {
    def withPrefix(prefix: Metric.Prefix): Builder =
      new Builder(Some(prefix), suffix, commonLabels)

    def withSuffix(suffix: Metric.Suffix): Builder =
      new Builder(prefix, Some(suffix), commonLabels)

    def withCommonLabels(labels: CommonLabels): Builder =
      new Builder(prefix, suffix, labels)

    def build[F[_]](registry: MetricsRegistry[F]): MetricsFactory[F] =
      new MetricsFactory[F](registry, prefix, suffix, commonLabels) {}

    def noop[F[_]: Applicative]: MetricsFactory[F] =
      MetricsFactory.noop[F]
  }

  def builder = new Builder()
}
