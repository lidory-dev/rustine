/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OptionsTest {
    @Test
    fun testOptions() {
        roundtripOptionU8(67.toUByte()) shouldBe 67.toUByte()
        roundtripOptionU8(null) shouldBe null
        roundtripOptionI8(67.toByte()) shouldBe 67.toByte()
        roundtripOptionI8(null) shouldBe null
        roundtripOptionU16(67.toUShort()) shouldBe 67.toUShort()
        roundtripOptionU16(null) shouldBe null
        roundtripOptionI16(67.toShort()) shouldBe 67.toShort()
        roundtripOptionI16(null) shouldBe null
        roundtripOptionU32(67u) shouldBe 67u
        roundtripOptionU32(null) shouldBe null
        roundtripOptionI32(67) shouldBe 67
        roundtripOptionI32(null) shouldBe null
        roundtripOptionU64(67uL) shouldBe 67uL
        roundtripOptionU64(null) shouldBe null
        roundtripOptionI64(67L) shouldBe 67L
        roundtripOptionI64(null) shouldBe null
        roundtripOptionF32(67.0f) shouldBe 67.0f
        roundtripOptionF32(null) shouldBe null
        roundtripOptionF64(67.0) shouldBe 67.0
        roundtripOptionF64(null) shouldBe null
        roundtripOptionBool(true) shouldBe true
        roundtripOptionBool(null) shouldBe null
        roundtripOptionString("test-string") shouldBe "test-string"
        roundtripOptionString(null) shouldBe null
        roundtripOptionRec(OptionsRec(67.toUByte())) shouldBe OptionsRec(67.toUByte())
        roundtripOptionRec(null) shouldBe null
    }
}
