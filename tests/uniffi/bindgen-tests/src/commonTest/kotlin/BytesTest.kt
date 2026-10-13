/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class BytesTest {
    @Test
    fun testBytes() {
        val bytes = ByteArray(4)
        bytes[0] = 0.toByte()
        bytes[1] = 1.toByte()
        bytes[2] = 2.toByte()
        bytes[3] = 3.toByte()
        roundtripBytes(bytes) shouldBe bytes
    }

    // Upstream passes direct `java.nio.ByteBuffer`s to `&[u8]` arguments. The Rustine bindings take
    // a `ByteArray` on every platform.
    @Test
    fun testBorrowedBytes() {
        sumBytesProcmacro(byteArrayOf()) shouldBe 0u
        sumBytesProcmacro(byteArrayOf(1, 2, 3)) shouldBe 6u
        firstByteProcmacro(byteArrayOf()) shouldBe null
        firstByteProcmacro(byteArrayOf(42)) shouldBe 42.toUByte()
    }
}
