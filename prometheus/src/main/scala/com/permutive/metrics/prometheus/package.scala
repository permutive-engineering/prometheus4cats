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

import com.permutive.metrics.prometheus.models.MetricType
import io.prometheus.client.SimpleCollector

package object prometheus {
  private[prometheus] type StateKey = (Option[Metric.Prefix], String) // TODO allow specific names maybe
  private[prometheus] type MetricID = (IndexedSeq[Label.Name], MetricType)
  private[prometheus] type StateValue = (MetricID, SimpleCollector[_])

  private[prometheus] type State = Map[StateKey, StateValue]
}