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

import cats.Show
import cats.data.NonEmptyList
import cats.effect.kernel.{Ref, Unique}
import io.prometheus.client.{Collector, SimpleCollector}
import prometheus4cats.javasimpleclient.models.MetricType
import prometheus4cats.util.NameUtils

package object javasimpleclient {
  private[javasimpleclient] type StateKey = (Option[Metric.Prefix], String) // TODO allow specific names maybe
  private[javasimpleclient] type MetricID = (IndexedSeq[Label.Name], MetricType)
  private[javasimpleclient] type StateValue = (MetricID, (SimpleCollector[_], Int))

  private[javasimpleclient] type State = Map[StateKey, StateValue]

  private[javasimpleclient] val duplicateShow: Show[(Option[Metric.Prefix], String)] = Show.show {
    case (prefix, name) =>
      NameUtils.makeName(prefix, name)
  }

  private[javasimpleclient] type CallbackState[F[_]] =
    Map[StateKey, (MetricType, Ref[F, Map[Unique.Token, F[NonEmptyList[Collector.MetricFamilySamples]]]], Collector)]

}
