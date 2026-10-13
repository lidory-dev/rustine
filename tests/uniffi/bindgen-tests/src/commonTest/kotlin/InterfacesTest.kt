/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class InterfacesTest {
    @Test
    fun testInterfaces() {
        val testInterface = TestInterface(20u)
        testInterface.getValue() shouldBe 20u
        cloneInterface(testInterface).getValue() shouldBe 20u
    }

    @Test
    fun testOptionalInterfaces() {
        roundtripOptionalInterface(null) shouldBe null
        roundtripOptionalInterface(TestInterface(20u))?.getValue() shouldBe 20u
    }

    @Test
    fun testSecondaryConstructors() {
        TestInterface.secondaryConstructor(20u).getValue() shouldBe 40u
    }

    @Test
    fun testRecordsWithInterfaces() {
        val two = TwoTestInterfaces(first = TestInterface(1u), second = TestInterface(2u))
        val swapped = swapTestInterfaces(two)
        swapped.first.getValue() shouldBe 2u
        swapped.second.getValue() shouldBe 1u
    }

    @Test
    fun testEnumsWithInterfaces() {
        val en = TestInterfaceEnum.One(TestInterface(1u))
        en.i.getValue() shouldBe 1u
    }

    @Test
    fun testArgumentNameMapping() {
        val testInterface = TestInterface(20u)
        // the following calls will fail if the argument name differs
        testInterface.methodWithMultiWordArg(theArgument = "test") shouldBe "test"
    }

    // Upstream doesn't test this in Kotlin, as the garbage collector releases the Rust objects at
    // some point. `destroy()` releases them right away, like the other bindings do.
    @Test
    fun testReferenceCounts() {
        val testInterface = TestInterface(20u)
        testInterface.refCount() shouldBe 1u
        val clone = cloneInterface(testInterface)
        testInterface.refCount() shouldBe 2u
        clone.destroy()
        testInterface.refCount() shouldBe 1u

        val swapped = swapTestInterfaces(TwoTestInterfaces(testInterface, testInterface))
        testInterface.refCount() shouldBe 3u
        swapped.destroy()
        testInterface.refCount() shouldBe 1u
        testInterface.destroy()
    }
}
