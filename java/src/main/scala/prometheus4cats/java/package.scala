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

package prometheus4cats

import io.prometheus.client.SimpleCollector
import prometheus4cats.java.models.MetricType

package object java {
  private[java] type StateKey = (Option[Metric.Prefix], String) // TODO allow specific names maybe
  private[java] type MetricID = (IndexedSeq[Label.Name], MetricType)
  private[java] type StateValue = (MetricID, SimpleCollector[_])

  private[java] type State = Map[StateKey, StateValue]
}
