/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class CallbackInterfacesTest {
    // `value` is private, as the JVM signature of its getter would clash with `getValue()`.
    class CallbackImpl(private var value: UInt) : TestCallbackInterface {
        override fun noop() {
        }

        override fun getValue(): UInt {
            return this.value
        }

        override fun setValue(value: UInt) {
            this.value = value
        }

        override fun throwIfEqual(numbers: CallbackInterfaceNumbers): CallbackInterfaceNumbers {
            if (numbers.a == 6u && numbers.b == 7u) {
                throw RuntimeException("unexpected failure")
            } else if (numbers.a == numbers.b) {
                throw TestException.Failure1()
            } else {
                return numbers
            }
        }

        override fun echo(s: String): String {
            return s
        }
    }

    @Test
    fun testCallbackInterfaces() {
        // Construct a callback interface to pass to rust
        val cbi = CallbackImpl(42u)
        // Test calling callback interface methods, which we can only do indirectly.
        // Each of these Rust functions inputs a callback interface, calls a method on it, then
        // returns the result.
        invokeTestCallbackInterfaceNoop(cbi)
        invokeTestCallbackInterfaceGetValue(cbi) shouldBe 42u
        invokeTestCallbackInterfaceSetValue(cbi, 43u)
        invokeTestCallbackInterfaceGetValue(cbi) shouldBe 43u
        invokeTestCallbackInterfaceEcho(cbi, "test-string") shouldBe "test-string"

        invokeTestCallbackInterfaceThrowIfEqual(cbi, CallbackInterfaceNumbers(1u, 2u)) shouldBe
            CallbackInterfaceNumbers(1u, 2u)
        shouldThrow<TestException.Failure1> {
            invokeTestCallbackInterfaceThrowIfEqual(cbi, CallbackInterfaceNumbers(1u, 1u))
        }
    }

    @Test
    fun testUnexpectedErrors() {
        val cbi = CallbackImpl(42u)
        // The RuntimeException is caught, converted to TestException.Failure2, and thrown
        shouldThrow<TestException.Failure2> {
            invokeTestCallbackInterfaceThrowIfEqual(cbi, CallbackInterfaceNumbers(6u, 7u))
        }.data shouldContain "unexpected failure"
    }
}
