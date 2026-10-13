/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class TimeTest {
    @Test
    fun testTime() {
        // Upstream uses java.time. `SystemTime` has a resolution of 100 nanoseconds on Windows, so
        // round the current time to it.
        val now = Clock.System.now().let {
            Instant.fromEpochSeconds(it.epochSeconds, it.nanosecondsOfSecond / 100 * 100)
        }
        roundtripSystemtime(now) shouldBe now
        roundtripDuration(5.minutes) shouldBe 5.minutes
    }
}
