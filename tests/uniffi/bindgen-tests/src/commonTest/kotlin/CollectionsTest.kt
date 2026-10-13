/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CollectionsTest {
    @Test
    fun testLists() {
        roundtripVecI8(listOf(1.toByte(), 2.toByte(), 3.toByte())) shouldBe
            listOf(1.toByte(), 2.toByte(), 3.toByte())
        roundtripVecU16(listOf(1.toUShort(), 2.toUShort(), 3.toUShort())) shouldBe
            listOf(1.toUShort(), 2.toUShort(), 3.toUShort())
        roundtripVecI16(listOf(1.toShort(), 2.toShort(), 3.toShort())) shouldBe
            listOf(1.toShort(), 2.toShort(), 3.toShort())
        roundtripVecU32(listOf(1u, 2u, 3u)) shouldBe listOf(1u, 2u, 3u)
        roundtripVecI32(listOf(1, 2, 3)) shouldBe listOf(1, 2, 3)
        roundtripVecU64(listOf(1uL, 2uL, 3uL)) shouldBe listOf(1uL, 2uL, 3uL)
        roundtripVecI64(listOf(1L, 2L, 3L)) shouldBe listOf(1L, 2L, 3L)
        roundtripVecF32(listOf(1.0f, 2.0f, 3.0f)) shouldBe listOf(1.0f, 2.0f, 3.0f)
        roundtripVecF64(listOf(1.0, 2.0, 3.0)) shouldBe listOf(1.0, 2.0, 3.0)
        roundtripVecBool(listOf(true, false)) shouldBe listOf(true, false)
        roundtripVecString(listOf("test-string")) shouldBe listOf("test-string")
        roundtripVecRec(listOf(CollectionsRec(67.toUByte()))) shouldBe
            listOf(CollectionsRec(67.toUByte()))
    }

    @Test
    fun testMapsAndSets() {
        roundtripHashMap(mapOf("a" to 1u, "b" to 2u)) shouldBe mapOf("a" to 1u, "b" to 2u)
        roundtripHashSet(setOf("a", "b", "c")) shouldBe setOf("a", "b", "c")
        roundtripHashMapU32Key(mapOf(1u to 2u, 2u to 4u)) shouldBe mapOf(1u to 2u, 2u to 4u)
    }

    @Test
    fun testRecordWithCollections() {
        roundtripRecWithCollections(
            RecWithCollections(
                EnumWithCollections.A(null),
                null,
                listOf(true, false),
                mapOf("a" to 10.toUByte(), "b" to 20.toUByte()),
            )
        ) shouldBe RecWithCollections(
            EnumWithCollections.A(null),
            null,
            listOf(true, false),
            mapOf("a" to 10.toUByte(), "b" to 20.toUByte()),
        )
    }

    @Test
    fun testNestedCollections() {
        roundtripVecHashSet(listOf(setOf("a", "b"))) shouldBe listOf(setOf("a", "b"))
        roundtripVecHashSet(null) shouldBe null
        roundtripComplexCollectionType(
            listOf(
                mapOf(
                    "a" to CollectionsComplexRec(10u, "Test", CollectionsEnum.A(100)),
                    "b" to CollectionsComplexRec(20u, "Test2", CollectionsEnum.B(1.0f, true)),
                )
            )
        ) shouldBe listOf(
            mapOf(
                "a" to CollectionsComplexRec(10u, "Test", CollectionsEnum.A(100)),
                "b" to CollectionsComplexRec(20u, "Test2", CollectionsEnum.B(1.0f, true)),
            )
        )
        roundtripComplexCollectionType(null) shouldBe null
    }
}
