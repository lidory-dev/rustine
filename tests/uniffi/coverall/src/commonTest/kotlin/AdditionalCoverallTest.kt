/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import coverall.EnumWithMethods
import coverall.FlatEnumWithMethods
import coverall.InternalException
import coverall.MethodsException
import coverall.NotConstructible
import coverall.NotConstructible2
import coverall.NotConstructibleException
import coverall.RecordWithMethods
import coverall.newNotConstructible
import coverall.newNotConstructible2
import coverall.throwMethodsError
import io.kotest.assertions.throwables.shouldThrow
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
}