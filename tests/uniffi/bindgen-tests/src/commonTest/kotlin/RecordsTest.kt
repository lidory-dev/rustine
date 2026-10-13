/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RecordsTest {
    @Test
    fun testRecords() {
        roundtripSimpleRec(SimpleRec(a = 42.toUByte())) shouldBe SimpleRec(a = 42.toUByte())
        // Rustine generates an object for records without fields, where upstream generates a
        // class whose instances are all equal.
        UnitRec shouldBe UnitRec
        val complexRec = ComplexRec(
            fieldU8 = 0.toUByte(),
            fieldI8 = (-1).toByte(),
            fieldU16 = 2.toUShort(),
            fieldI16 = (-3).toShort(),
            fieldU32 = 4u,
            fieldI32 = -5,
            fieldU64 = 6uL,
            fieldI64 = -7L,
            fieldF32 = 8.5.toFloat(),
            fieldF64 = 9.5,
            fieldString = "test",
            fieldRec = SimpleRec(a = 42.toUByte()),
        )
        roundtripComplexRec(complexRec) shouldBe ComplexRec(
            fieldU8 = 0.toUByte(),
            fieldI8 = (-1).toByte(),
            fieldU16 = 2.toUShort(),
            fieldI16 = (-3).toShort(),
            fieldU32 = 4u,
            fieldI32 = -5,
            fieldU64 = 6uL,
            fieldI64 = -7L,
            fieldF32 = 8.5.toFloat(),
            fieldF64 = 9.5,
            fieldString = "test",
            fieldRec = SimpleRec(a = 42.toUByte()),
        )
    }

    @Test
    fun testMethods() {
        SimpleRec(a = 42.toUByte()).roundtrip() shouldBe SimpleRec(a = 42.toUByte())
    }
}
