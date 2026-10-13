/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class EnumsTest {
    @Test
    fun testSimpleEnums() {
        // Upstream doesn't call this function in Kotlin.
        roundtripEnumNoData(EnumNoData.B) shouldBe EnumNoData.B

        roundtripEnumWithData(EnumWithData.A(10.toUByte(), 20.toUShort())) shouldBe
            EnumWithData.A(10.toUByte(), 20.toUShort())
        roundtripEnumWithData(EnumWithData.B("Ten", 10u)) shouldBe EnumWithData.B("Ten", 10u)
        roundtripEnumWithData(EnumWithData.C) shouldBe EnumWithData.C

        // Test field names for tuple-style enums
        EnumWithData.B("Ten", 10u).v1 shouldBe "Ten"
        EnumWithData.B("Ten", 10u).v2 shouldBe 10u
    }

    @Test
    fun testComplexEnums() {
        roundtripComplexEnum(ComplexEnum.A(EnumNoData.C)) shouldBe ComplexEnum.A(EnumNoData.C)
        roundtripComplexEnum(ComplexEnum.B(EnumWithData.A(20.toUByte(), 40.toUShort()))) shouldBe
            ComplexEnum.B(EnumWithData.A(20.toUByte(), 40.toUShort()))
        roundtripComplexEnum(ComplexEnum.C(SimpleRec(a = 30.toUByte()))) shouldBe
            ComplexEnum.C(SimpleRec(a = 30.toUByte()))
    }

    @Test
    fun testDiscriminants() {
        ExplicitValuedEnum.FIRST.value shouldBe 1.toUByte()
        ExplicitValuedEnum.SECOND.value shouldBe 2.toUByte()
        ExplicitValuedEnum.FOURTH.value shouldBe 4.toUByte()
        ExplicitValuedEnum.TENTH.value shouldBe 10.toUByte()
        ExplicitValuedEnum.ELEVENTH.value shouldBe 11.toUByte()
        ExplicitValuedEnum.THIRTEENTH.value shouldBe 13.toUByte()
        // Some discriminants specified, increment by one for any unspecified variants
        GappedEnum.ONE.value shouldBe 10.toUByte()
        GappedEnum.TWO.value shouldBe 11.toUByte() // Sequential value after ONE (10+1)
        GappedEnum.THREE.value shouldBe 14.toUByte() // Explicit value again
    }

    @Test
    fun testMethods() {
        EnumWithData.A(20.toUByte(), 40.toUShort()).roundtrip() shouldBe
            EnumWithData.A(20.toUByte(), 40.toUShort())
    }
}
