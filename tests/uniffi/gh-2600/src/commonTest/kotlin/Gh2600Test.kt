/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import gh_2600.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

// Tests mozilla/uniffi-rs#2600, where objects with an alignment of 32 were freed too early.
class Gh2600Test {
    @Test
    fun objectIsNotFreedEarly() {
        val obj = MyStruct256()
        dropCount() shouldBe 0u
        obj.method()
        dropCount() shouldBe 0u
        // Rustine: also check that destroying the object frees it, which keeps it alive until
        // then.
        obj.destroy()
        dropCount() shouldBe 1u
    }
}
