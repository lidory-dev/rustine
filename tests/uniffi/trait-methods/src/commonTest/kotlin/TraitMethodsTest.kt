/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import trait_methods.*
import kotlin.test.Test

class TraitMethodsTest {
    @Test
    fun testDisplay() {
        val m = TraitMethods("yo")
        m.toString() shouldBe "TraitMethods(yo)"
    }

    @Test
    fun testEq() {
        val m = TraitMethods("yo")
        m shouldBe TraitMethods("yo")
        m shouldNotBe TraitMethods("yoyo")
    }

    @Test
    fun testHash() {
        val m = TraitMethods("yo")
        val map = mapOf(m to 1, TraitMethods("yoyo") to 2)
        map[m] shouldBe 1
        map[TraitMethods("yoyo")] shouldBe 2
    }

    @Test
    fun testOrd() {
        val m = TraitMethods("yo")
        (ProcTraitMethods("a") < ProcTraitMethods("b")).shouldBeTrue()
        (m < TraitMethods("z")).shouldBeTrue()
        (m <= TraitMethods("z")).shouldBeTrue()
        (TraitMethods("z") > m).shouldBeTrue()
    }

    // Rustine: lowering a destroyed object throws instead of cloning its freed Rust object.
    @Test
    fun testDestroyedObjectsAreNotLowered() {
        val m = TraitMethods("yo")
        val destroyed = TraitMethods("yo")
        destroyed.destroy()
        destroyed.uniffiIsDestroyed.shouldBeTrue()
        shouldThrow<IllegalStateException> {
            m.compareTo(destroyed)
        }.message shouldBe "TraitMethods object has already been destroyed"
        shouldThrow<IllegalStateException> { m == destroyed }
        shouldThrow<IllegalStateException> {
            destroyed.toString()
        }.message shouldBe "TraitMethods object has already been destroyed"
    }

    @Test
    fun testProcMacroDisplay() {
        val m = ProcTraitMethods("yo")
        m.toString() shouldBe "ProcTraitMethods(yo)"
    }

    @Test
    fun testProcMacroEq() {
        val m = ProcTraitMethods("yo")
        m shouldBe ProcTraitMethods("yo")
        m shouldNotBe ProcTraitMethods("yoyo")
    }

    @Test
    fun testProcMacroHash() {
        val m = ProcTraitMethods("yo")
        val map = mapOf(m to 1, ProcTraitMethods("yoyo") to 2)
        map[m] shouldBe 1
        map[ProcTraitMethods("yoyo")] shouldBe 2
    }

    // The Rust implementations of the traits of records, enums and errors deliberately differ from
    // the ones Kotlin would generate, so these tests show that the Rust ones are used.

    @Test
    fun testRecords() {
        (UdlRecord(s = "yo", i = 2) == UdlRecord(s = "yo", i = 2)).shouldBeTrue()
        (UdlRecord(s = "yo", i = 2) == UdlRecord(s = "yo", i = 3)).shouldBeTrue()
        (UdlRecord(s = "hi", i = 2) != UdlRecord(s = "yo", i = 3)).shouldBeTrue()
        (UdlRecord(s = "a", i = 2) < UdlRecord(s = "yo", i = 1)).shouldBeTrue()

        TraitRecord(s = "yo", i = 2).toString() shouldBe "TraitRecord { s: \"yo\", i: 2 }"
        (TraitRecord(s = "yo", i = 2) == TraitRecord(s = "yo", i = 2)).shouldBeTrue()
        (TraitRecord(s = "yo", i = 2) == TraitRecord(s = "yo", i = 3)).shouldBeTrue()
        (TraitRecord(s = "hi", i = 2) != TraitRecord(s = "yo", i = 3)).shouldBeTrue()
        (TraitRecord(s = "a", i = 2) < TraitRecord(s = "yo", i = 1)).shouldBeTrue()
        TraitRecord(s = "yo", i = 2).hashCode() shouldBe TraitRecord(s = "yo", i = 3).hashCode()
    }

    @Test
    fun testEnums() {
        TraitEnum.N.toString() shouldBe "TraitEnum::N"
        TraitEnum.S("hello").toString() shouldBe "TraitEnum::S(\"hello\")"
        TraitEnum.I(1).toString() shouldBe "TraitEnum::I(1)"
        UdlEnum.S("hello").toString() shouldBe "S { s: \"hello\" }"

        (UdlEnum.S("hello") == UdlEnum.S("hello")).shouldBeTrue()
        (UdlEnum.S("hello") == UdlEnum.S("other")).shouldBeTrue()
        (UdlEnum.S("hello") < UdlEnum.I(0)).shouldBeTrue()
        (TraitEnum.I(1) == TraitEnum.I(1)).shouldBeTrue()
        (TraitEnum.I(1) == TraitEnum.I(2)).shouldBeTrue()
        (TraitEnum.S("hello") < TraitEnum.I(0)).shouldBeTrue()
        (TraitEnum.I(0) < TraitEnum.N).shouldBeTrue()
        val n: TraitEnum = TraitEnum.N
        (n != TraitEnum.I(0)).shouldBeTrue()
        TraitEnum.I(1).hashCode() shouldBe TraitEnum.I(2).hashCode()
    }

    @Test
    fun testFlatEnums() {
        // flat enum with Display only - Kotlin doesn't support Eq/Ord/Hash exports for flat enums
        val flatAlpha = getFlatTraitEnum(0u)
        flatAlpha shouldBe FlatTraitEnum.ALPHA
        (getFlatTraitEnum(1u) > flatAlpha).shouldBeTrue()
        flatAlpha.toString() shouldBe "FlatTraitEnum::flat-alpha"
        getFlatTraitEnum(2u).toString() shouldBe "FlatTraitEnum::flat-gamma"
        val flatSet = setOf(flatAlpha)
        flatSet.contains(FlatTraitEnum.ALPHA) shouldBe true
        flatSet.contains(FlatTraitEnum.BETA) shouldBe false
        XyzEnum.XyzNone shouldBe XyzEnum.XyzNone
        (XyzEnum.XyzNamed(0u) < XyzEnum.XyzNamed(1u)).shouldBeTrue()
    }

    @Test
    fun testErrors() {
        // flat error - Display export on flat errors
        shouldThrow<FlatException.NotFound> {
            throwFlatError(0u)
        }.toString() shouldBe "error: not found"

        // error with multiple exported traits (Debug, Display, Eq, Ord, Hash)
        val err1 = shouldThrow<MultipleTraitException.NoData> {
            throwMultipleTraitError(0u)
        }
        err1.toString() shouldBe "MultipleTraitError::NoData"

        // test nested error with Display
        val err2 = shouldThrow<MultipleTraitException.Nested> {
            throwMultipleTraitError(1u)
        }
        err2.toString() shouldBe "nested error: error: not found"

        // test Eq on flat variants
        val err3 = shouldThrow<MultipleTraitException.NoData> {
            throwMultipleTraitError(0u)
        }
        (err3 == err1).shouldBeTrue()
        err3.hashCode() shouldBe err1.hashCode()

        // test Ord
        (err1 < err2).shouldBeTrue()

        // error that doesn't end in "Error" - tests class_name filter doesn't break non-Error types
        val apiErr = shouldThrow<ApiFailure.NetworkIssue> {
            throwApiFailure(0u)
        }
        apiErr.toString() shouldBe "api network issue"

        // test Eq on non-Error type
        val apiErr3 = shouldThrow<ApiFailure.NetworkIssue> {
            throwApiFailure(0u)
        }
        (apiErr3 == apiErr).shouldBeTrue()

        // test Ord on non-Error type
        val apiErr2 = shouldThrow<ApiFailure.ServerDown> {
            throwApiFailure(1u)
        }
        (apiErr < apiErr2).shouldBeTrue()
    }
}
