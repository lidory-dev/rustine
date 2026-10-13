/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FuturesTest {
    @Test
    fun testSimpleFunctions() = runTest {
        asyncRoundtripU8(42.toUByte()) shouldBe 42.toUByte()
        asyncRoundtripI8((-42).toByte()) shouldBe (-42).toByte()
        asyncRoundtripU16(42.toUShort()) shouldBe 42.toUShort()
        asyncRoundtripI16((-42).toShort()) shouldBe (-42).toShort()
        asyncRoundtripU32(42u) shouldBe 42u
        asyncRoundtripI32(-42) shouldBe -42
        asyncRoundtripU64(42uL) shouldBe 42uL
        asyncRoundtripI64(-42L) shouldBe -42L
        asyncRoundtripF32(0.5f) shouldBe 0.5f
        asyncRoundtripF64(-0.5) shouldBe -0.5
        asyncRoundtripString("hi") shouldBe "hi"
        asyncRoundtripVec(listOf(42u)) shouldBe listOf(42u)
        asyncRoundtripMap(mapOf("hello" to "world")) shouldBe mapOf("hello" to "world")
    }

    @Test
    fun testErrors() = runTest {
        shouldThrow<TestException.Failure1> {
            asyncThrowError()
        }
    }

    @Test
    fun testObjectsAndMethods() = runTest {
        val obj = AsyncInterface("Alice")
        obj.name() shouldBe "Alice"

        val obj2 = asyncRoundtripObj(obj)
        obj2.name() shouldBe "Alice"

        val rec = AsyncRecord("Bob")
        rec.getName() shouldBe "Bob"
    }

    // Async callback interfaces
    class AsyncCallbackImpl(private var value: UInt) : TestAsyncCallbackInterface {
        override suspend fun noop() {}

        override suspend fun getValue(): UInt {
            return this.value
        }

        override suspend fun setValue(value: UInt) {
            this.value = value
        }

        override suspend fun throwIfEqual(numbers: CallbackInterfaceNumbers): CallbackInterfaceNumbers {
            if (numbers.a == numbers.b) {
                throw TestException.Failure1()
            }
            return numbers
        }
    }

    @Test
    fun testAsyncCallbackInterfaces() = runTest {
        val cbi = AsyncCallbackImpl(42u)
        invokeTestAsyncCallbackInterfaceNoop(cbi)

        invokeTestAsyncCallbackInterfaceGetValue(cbi) shouldBe 42u

        invokeTestAsyncCallbackInterfaceSetValue(cbi, 43u)
        invokeTestAsyncCallbackInterfaceGetValue(cbi) shouldBe 43u

        shouldThrow<TestException.Failure1> {
            invokeTestAsyncCallbackInterfaceThrowIfEqual(
                cbi,
                CallbackInterfaceNumbers(a = 10u, b = 10u),
            )
        }

        invokeTestAsyncCallbackInterfaceThrowIfEqual(
            cbi,
            CallbackInterfaceNumbers(a = 10u, b = 11u),
        ) shouldBe CallbackInterfaceNumbers(a = 10u, b = 11u)
    }
}
