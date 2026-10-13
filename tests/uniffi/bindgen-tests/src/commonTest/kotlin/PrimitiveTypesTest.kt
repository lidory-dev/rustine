/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PrimitiveTypesTest {
    @Test
    fun testInputs() {
        // Test passing arguments from Kotlin to Rust
        inputU8(42.toUByte())
        inputI8((-42).toByte())
        inputU16(42.toUShort())
        inputI16((-42).toShort())
        inputU32(42u)
        inputI32(-42)
        inputU64(42uL)
        inputI64(-42L)
        inputF32(0.5f)
        inputF64(-3.5)
        inputBool(true)
        inputString("ABC")
    }

    @Test
    fun testOutputs() {
        // Test returning values from Rust to Kotlin
        outputU8() shouldBe 1.toUByte()
        outputI8() shouldBe 1.toByte()
        outputU16() shouldBe 1.toUShort()
        outputI16() shouldBe 1.toShort()
        outputU32() shouldBe 1u
        outputI32() shouldBe 1
        outputU64() shouldBe 1uL
        outputI64() shouldBe 1L
        outputF32() shouldBe 1.0f
        outputF64() shouldBe 1.0
        outputBool() shouldBe true
        outputString() shouldBe "test-string"
        outputU8Max() shouldBe 255.toUByte()
        outputU16Max() shouldBe 65535.toUShort()
    }

    @Test
    fun testRoundtrips() {
        // Test passing values both ways
        roundtripU8(42.toUByte()) shouldBe 42.toUByte()
        roundtripI8((-42).toByte()) shouldBe (-42).toByte()
        roundtripU16(42.toUShort()) shouldBe 42.toUShort()
        roundtripI16((-42).toShort()) shouldBe (-42).toShort()
        roundtripU8(255.toUByte()) shouldBe 255.toUByte()
        roundtripU16(65535.toUShort()) shouldBe 65535.toUShort()
        roundtripU32(42u) shouldBe 42u
        roundtripI32(-42) shouldBe -42
        roundtripU64(42uL) shouldBe 42uL
        roundtripI64(-42L) shouldBe -42L
        roundtripF32(0.5f) shouldBe 0.5f
        roundtripF64(-3.5) shouldBe -3.5
        roundtripBool(true) shouldBe true
        roundtripString("ABC") shouldBe "ABC"
    }

    @Test
    fun testManyArguments() {
        // Test calling a function with lots of args
        // This function will sum up all the numbers, then negate the value since we passed in `true`
        sumWithManyTypes(
            1.toUByte(),
            (-2).toByte(),
            3.toUShort(),
            (-4).toShort(),
            5u,
            -6,
            7uL,
            -8L,
            9.5f,
            -10.5,
            true,
        ) shouldBe 5.0
    }

    @Test
    fun testArgumentNameMapping() {
        // Test argument name mapping
        // the following calls will fail if the argument name differs
        funcWithMultiWordArg(theArgument = 16) shouldBe 16
    }

    // Upstream only tests this in Swift.
    @Test
    fun testStringWithByteOrderMark() {
        // The string converters must keep a leading U+FEFF instead of treating it as encoding
        // metadata.
        stringWithBom() shouldBe "﻿hello"
    }
}
