/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import coverall.EnumWithBytesAndEq
import coverall.EnumWithMethods
import coverall.FlatEnumWithMethods
import coverall.InternalException
import coverall.MethodsException
import coverall.NotConstructible
import coverall.NotConstructible2
import coverall.NotConstructibleException
import coverall.OrderedObject
import coverall.RecordWithBytesAndEq
import coverall.RecordWithMethods
import coverall.newNotConstructible
import coverall.newNotConstructible2
import coverall.throwMethodsError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

// Covers test cases not in the original unit test in upstream.
class AdditionalCoverallTest {
    @Test
    fun testPanicInsideFunctionReturningObject() {
        if (uniffiSupported) {
            shouldThrow<InternalException> {
                NotConstructible()
            }
            shouldThrow<InternalException> {
                newNotConstructible()
            }
        }
    }

    @Test
    fun testErrorThrownFromFunctionReturningObject() {
        if (uniffiSupported) {
            shouldThrow<NotConstructibleException> {
                NotConstructible2()
            }
            shouldThrow<NotConstructibleException> {
                newNotConstructible2()
            }
        }
    }

    @Test
    fun testMethodsOfRecordsEnumsAndErrors() = runTest {
        if (uniffiSupported) {
            RecordWithMethods(21).doubled() shouldBe 42
            RecordWithMethods(21).doubledAsync() shouldBe 42
            FlatEnumWithMethods.FIRST.other() shouldBe FlatEnumWithMethods.SECOND
            EnumWithMethods.Empty.valueOr(1) shouldBe 1
            EnumWithMethods.Value(2).valueOr(1) shouldBe 2
            shouldThrow<MethodsException.Code> {
                throwMethodsError(42)
            }.code() shouldBe 42
        }
    }

    @Test
    fun testTraitsOfRecordsEnumsErrorsAndObjects() {
        if (uniffiSupported) {
            RecordWithMethods(1).toString() shouldBe "RecordWithMethods { value: 1 }"
            (RecordWithMethods(1) == RecordWithMethods(1)).shouldBeTrue()
            RecordWithMethods(1).hashCode() shouldBe RecordWithMethods(1).hashCode()
            (RecordWithMethods(1) < RecordWithMethods(2)).shouldBeTrue()
            FlatEnumWithMethods.FIRST.toString() shouldBe "first"
            val empty: EnumWithMethods = EnumWithMethods.Empty
            (empty < EnumWithMethods.Value(0)).shouldBeTrue()
            (EnumWithMethods.Value(0) < EnumWithMethods.Value(1)).shouldBeTrue()
            (EnumWithMethods.Value(1) == EnumWithMethods.Value(1)).shouldBeTrue()
            shouldThrow<MethodsException.Code> {
                throwMethodsError(42)
            }.toString() shouldBe "code 42"

            (RecordWithBytesAndEq(byteArrayOf(1)) == RecordWithBytesAndEq(byteArrayOf(1))).shouldBeTrue()
            RecordWithBytesAndEq(byteArrayOf(1)).hashCode() shouldBe RecordWithBytesAndEq(byteArrayOf(1)).hashCode()
            (EnumWithBytesAndEq.Bytes(byteArrayOf(1)) == EnumWithBytesAndEq.Bytes(byteArrayOf(1))).shouldBeTrue()
            EnumWithBytesAndEq.Bytes(byteArrayOf(1)).hashCode() shouldBe EnumWithBytesAndEq.Bytes(byteArrayOf(1)).hashCode()

            OrderedObject(1).toString() shouldBe "OrderedObject { value: 1 }"
            (OrderedObject(1) == OrderedObject(1)).shouldBeTrue()
            (OrderedObject(1) < OrderedObject(2)).shouldBeTrue()
        }
    }
}