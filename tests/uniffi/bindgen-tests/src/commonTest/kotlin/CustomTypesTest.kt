/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CustomTypesTest {
    @Test
    fun testCustomTypes() {
        roundtripCustomType1(100uL) shouldBe 100uL
        // uniffi.toml maps CustomType2 to a Map.
        roundtripCustomType2(mapOf("value" to 200uL)) shouldBe mapOf("value" to 200uL)
    }
}
