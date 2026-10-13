/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.*
import type_limits.*
import kotlin.test.*

class TypeLimitsTest {
    @Test
    fun testStringLimits() {
        shouldThrow<CharacterCodingException> {
            takeString("\ud800")
        }
        takeString("") shouldBe ""
        takeString("愛") shouldBe "愛"
        takeString("💖") shouldBe "💖"
    }

    @Test
    fun testBytes() {
        takeBytes(byteArrayOf()) shouldBe byteArrayOf()
        takeBytes(byteArrayOf(1, 2, 3)) shouldBe byteArrayOf(1, 2, 3)
    }

    @Test
    fun testBorrowedBytes() {
        takeBytesByRef(byteArrayOf()) shouldBe byteArrayOf()
        takeBytesByRef(byteArrayOf(-1, 0, 1)) shouldBe byteArrayOf(-1, 0, 1)
        sumBytesUdl(byteArrayOf()) shouldBe 0u
        sumBytesUdl(byteArrayOf(1, 2, 3)) shouldBe 6u
        sumBytesUdl(byteArrayOf(-1)) shouldBe 255u

        val large = ByteArray(8 * 1024 * 1024 + 1) { it.toByte() }
        takeBytesByRef(large).contentEquals(large) shouldBe true
        sumBytesUdl(large) shouldBe large.sumOf { it.toUByte().toUInt() }
    }
}
