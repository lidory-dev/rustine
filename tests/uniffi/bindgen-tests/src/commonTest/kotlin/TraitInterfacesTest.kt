/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TraitInterfacesTest {
    // `value` is private, as the JVM signature of its getter would clash with `getValue()`.
    class TraitImpl(private var value: UInt) : TestTraitInterface {
        override fun noop() {}

        override fun getValue(): UInt {
            return this.value
        }

        override fun setValue(value: UInt) {
            this.value = value
        }

        override fun throwIfEqual(numbers: CallbackInterfaceNumbers): CallbackInterfaceNumbers {
            if (numbers.a == numbers.b) {
                throw TestException.Failure1()
            }
            return numbers
        }

        override fun roundtripRecord(value: SimpleRec): SimpleRec = value

        override fun roundtripEnum(value: EnumWithData): EnumWithData = value

        override fun roundtripInterface(value: TestInterface): TestInterface = value
    }

    private fun checkRustImpl(rustTraitImpl: TestTraitInterface) {
        rustTraitImpl.noop()
        rustTraitImpl.getValue() shouldBe 42u
        rustTraitImpl.setValue(43u)
        rustTraitImpl.getValue() shouldBe 43u
        shouldThrow<TestException.Failure1> {
            rustTraitImpl.throwIfEqual(CallbackInterfaceNumbers(a = 10u, b = 10u))
        }
        rustTraitImpl.throwIfEqual(CallbackInterfaceNumbers(a = 10u, b = 11u)) shouldBe
            CallbackInterfaceNumbers(a = 10u, b = 11u)
        rustTraitImpl.roundtripRecord(SimpleRec(a = 10.toUByte())) shouldBe SimpleRec(a = 10.toUByte())
        rustTraitImpl.roundtripEnum(EnumWithData.A(10.toUByte(), 20.toUShort())) shouldBe
            EnumWithData.A(10.toUByte(), 20.toUShort())
        rustTraitImpl.roundtripInterface(TestInterface(20u)).getValue() shouldBe 20u
    }

    private fun checkKtImpl(ktTraitImpl: TestTraitInterface) {
        invokeTestTraitInterfaceNoop(ktTraitImpl)
        invokeTestTraitInterfaceGetValue(ktTraitImpl) shouldBe 42u
        invokeTestTraitInterfaceSetValue(ktTraitImpl, 43u)
        invokeTestTraitInterfaceGetValue(ktTraitImpl) shouldBe 43u

        shouldThrow<TestException.Failure1> {
            invokeTestTraitInterfaceThrowIfEqual(
                ktTraitImpl,
                CallbackInterfaceNumbers(a = 10u, b = 10u),
            )
        }
        invokeTestTraitInterfaceThrowIfEqual(
            ktTraitImpl,
            CallbackInterfaceNumbers(a = 10u, b = 11u),
        ) shouldBe CallbackInterfaceNumbers(a = 10u, b = 11u)
        invokeTestTraitInterfaceRoundtripRecord(ktTraitImpl, SimpleRec(a = 10.toUByte())) shouldBe
            SimpleRec(a = 10.toUByte())
        invokeTestTraitInterfaceRoundtripEnum(
            ktTraitImpl,
            EnumWithData.A(10.toUByte(), 20.toUShort()),
        ) shouldBe EnumWithData.A(10.toUByte(), 20.toUShort())
        invokeTestTraitInterfaceRoundtripInterface(ktTraitImpl, TestInterface(20u)).getValue() shouldBe 20u
    }

    @Test
    fun testRustImplementations() {
        checkRustImpl(createTestTraitInterface(42u))
        checkRustImpl(roundtripTestTraitInterface(createTestTraitInterface(42u)))
        checkRustImpl(roundtripTestTraitInterfaceList(listOf(createTestTraitInterface(42u)))[0])
    }

    @Test
    fun testKotlinImplementations() {
        checkKtImpl(TraitImpl(42u))
        checkKtImpl(roundtripTestTraitInterface(TraitImpl(42u)))
        checkKtImpl(roundtripTestTraitInterfaceList(listOf(TraitImpl(42u)))[0])

        // Rustine also checks that Kotlin implementations come back unchanged.
        val ktTraitImpl = TraitImpl(42u)
        roundtripTestTraitInterface(ktTraitImpl) shouldBeSameInstanceAs ktTraitImpl
        roundtripTestTraitInterfaceList(listOf(ktTraitImpl))[0] shouldBeSameInstanceAs ktTraitImpl
    }

    class AsyncTraitImpl(private var value: UInt) : AsyncTestTraitInterface {
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

    private suspend fun checkAsyncRustImpl(rustTraitImpl: AsyncTestTraitInterface) {
        rustTraitImpl.noop()
        rustTraitImpl.getValue() shouldBe 42u
        rustTraitImpl.setValue(43u)
        rustTraitImpl.getValue() shouldBe 43u
        shouldThrow<TestException.Failure1> {
            rustTraitImpl.throwIfEqual(CallbackInterfaceNumbers(a = 10u, b = 10u))
        }
        rustTraitImpl.throwIfEqual(CallbackInterfaceNumbers(a = 10u, b = 11u)) shouldBe
            CallbackInterfaceNumbers(a = 10u, b = 11u)
    }

    private suspend fun checkAsyncKtImpl(ktTraitImpl: AsyncTestTraitInterface) {
        invokeAsyncTestTraitInterfaceNoop(ktTraitImpl)
        invokeAsyncTestTraitInterfaceGetValue(ktTraitImpl) shouldBe 42u
        invokeAsyncTestTraitInterfaceSetValue(ktTraitImpl, 43u)
        invokeAsyncTestTraitInterfaceGetValue(ktTraitImpl) shouldBe 43u

        shouldThrow<TestException.Failure1> {
            invokeAsyncTestTraitInterfaceThrowIfEqual(
                ktTraitImpl,
                CallbackInterfaceNumbers(a = 10u, b = 10u),
            )
        }
        invokeAsyncTestTraitInterfaceThrowIfEqual(
            ktTraitImpl,
            CallbackInterfaceNumbers(a = 10u, b = 11u),
        ) shouldBe CallbackInterfaceNumbers(a = 10u, b = 11u)
    }

    @Test
    fun testAsyncRustImplementations() = runTest {
        checkAsyncRustImpl(createAsyncTestTraitInterface(42u))
        checkAsyncRustImpl(roundtripAsyncTestTraitInterface(createAsyncTestTraitInterface(42u)))
        checkAsyncRustImpl(
            roundtripAsyncTestTraitInterfaceList(listOf(createAsyncTestTraitInterface(42u)))[0]
        )
    }

    @Test
    fun testAsyncKotlinImplementations() = runTest {
        checkAsyncKtImpl(AsyncTraitImpl(42u))
        checkAsyncKtImpl(roundtripAsyncTestTraitInterface(AsyncTraitImpl(42u)))
        checkAsyncKtImpl(roundtripAsyncTestTraitInterfaceList(listOf(AsyncTraitImpl(42u)))[0])

        val ktTraitImpl = AsyncTraitImpl(42u)
        roundtripAsyncTestTraitInterface(ktTraitImpl) shouldBeSameInstanceAs ktTraitImpl
        roundtripAsyncTestTraitInterfaceList(listOf(ktTraitImpl))[0] shouldBeSameInstanceAs ktTraitImpl
    }

    // Rust-only trait: smoke-test for the #[uniffi::export(rust)] codegen path
    private fun checkRustOnlyImpl(impl: TestRustOnlyTraitInterface) {
        shouldThrow<TestException.Failure1> {
            impl.throwIfEqual(CallbackInterfaceNumbers(a = 10u, b = 10u))
        }
        impl.throwIfEqual(CallbackInterfaceNumbers(a = 10u, b = 11u)) shouldBe
            CallbackInterfaceNumbers(a = 10u, b = 11u)
    }

    @Test
    fun testRustOnlyTraits() {
        checkRustOnlyImpl(createRustOnlyTestTraitInterface())
    }

    // Foreign-only trait: smoke-test for the #[uniffi::export(foreign)] codegen path
    class ForeignOnlyImpl : TestForeignOnlyTraitInterface {
        override fun throwIfEqual(numbers: CallbackInterfaceNumbers): CallbackInterfaceNumbers {
            if (numbers.a == numbers.b) {
                throw TestException.Failure1()
            }
            return numbers
        }
    }

    private fun checkForeignOnlyImpl(impl: TestForeignOnlyTraitInterface) {
        shouldThrow<TestException.Failure1> {
            invokeTestForeignOnlyTraitThrowIfEqual(impl, CallbackInterfaceNumbers(a = 10u, b = 10u))
        }
        invokeTestForeignOnlyTraitThrowIfEqual(impl, CallbackInterfaceNumbers(a = 10u, b = 11u)) shouldBe
            CallbackInterfaceNumbers(a = 10u, b = 11u)
    }

    @Test
    fun testForeignOnlyTraits() {
        checkForeignOnlyImpl(ForeignOnlyImpl())
        checkForeignOnlyImpl(roundtripTestForeignOnlyTrait(ForeignOnlyImpl()))

        val impl = ForeignOnlyImpl()
        roundtripTestForeignOnlyTrait(impl) shouldBeSameInstanceAs impl
    }
}
