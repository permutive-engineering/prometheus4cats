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

package prometheus4cats.javaclient.internal

import java.util.concurrent.atomic.AtomicInteger

import munit.FunSuite

class DataPointResolverSuite extends FunSuite {

  private def resolver(
      resolutions: AtomicInteger,
      renders: AtomicInteger,
      maxCachedLabelSets: Int = DataPointResolver.MaxCachedLabelSets
  ): DataPointResolver[(String, String), String] =
    new DataPointResolver[(String, String), String](
      f = { case (a, b) =>
        renders.incrementAndGet()
        IndexedSeq(a, b)
      },
      commonLabelValues = Array("common"),
      getDataPoint = { arr =>
        resolutions.incrementAndGet()
        arr.mkString(",")
      },
      maxCachedLabelSets = maxCachedLabelSets
    )

  test("resolves a label value once and reuses the data point") {
    val resolutions = new AtomicInteger()
    val renders     = new AtomicInteger()
    val r           = resolver(resolutions, renders)

    val first  = r(("a", "b"))
    val second = r(("a", "b"))
    val third  = r(("a", "b"))

    assertEquals(first, "a,b,common")
    assertEquals(second, first)
    assertEquals(third, first)
    assertEquals(resolutions.get(), 1)
    assertEquals(renders.get(), 1)
  }

  test("distinct label values resolve to distinct data points") {
    val resolutions = new AtomicInteger()
    val renders     = new AtomicInteger()
    val r           = resolver(resolutions, renders)

    assertEquals(r(("a", "b")), "a,b,common")
    assertEquals(r(("c", "d")), "c,d,common")
    assertEquals(resolutions.get(), 2)
  }

  test("failed resolutions are not cached") {
    val attempts = new AtomicInteger()
    val r = new DataPointResolver[String, String](
      f = IndexedSeq(_),
      commonLabelValues = Array.empty,
      getDataPoint = { arr =>
        if (attempts.incrementAndGet() == 1) throw new IllegalArgumentException("boom") // scalafix:ok
        arr.mkString(",")
      }
    )

    val _ = intercept[IllegalArgumentException](r("a"))
    assertEquals(r("a"), "a")
    assertEquals(attempts.get(), 2)
  }

  test("stops caching new label values beyond the cap but keeps serving cached ones") {
    val resolutions = new AtomicInteger()
    val renders     = new AtomicInteger()
    val r           = resolver(resolutions, renders, maxCachedLabelSets = 2)

    assertEquals(r(("a", "1")), "a,1,common")
    assertEquals(r(("a", "2")), "a,2,common")
    // cap reached: new label values resolve directly, every time
    assertEquals(r(("a", "3")), "a,3,common")
    assertEquals(r(("a", "3")), "a,3,common")
    assertEquals(resolutions.get(), 4)
    // cached values are still served from the cache
    assertEquals(r(("a", "1")), "a,1,common")
    assertEquals(resolutions.get(), 4)
  }

  test("labelArray renders dynamic labels followed by common label values") {
    val r = resolver(new AtomicInteger(), new AtomicInteger())

    assertEquals(r.labelArray(("x", "y")).toList, List("x", "y", "common"))
  }

}
