/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ErrorsTest {
    @Test
    fun testErrors() {
        shouldThrow<TestException.Failure1> {
            funcWithError(0u)
        }
        shouldThrow<TestException.Failure2> {
            funcWithError(1u)
        }.data shouldBe "DATA"
        shouldThrow<TestException.Failure3> {
            funcWithError(50u)
        }.v1 shouldBe 50u
        // This shouldn't throw
        funcWithError(200u)
    }

    @Test
    fun testFlatErrors() {
        shouldThrow<TestFlatException.IoException> {
            funcWithFlatError(0u)
        }
        // This shouldn't throw
        funcWithFlatError(1u)
    }

    @Test
    fun testErrorsWithoutData() {
        shouldThrow<TestErrorNoData.Failure1> {
            funcWithErrorNoData(0u)
        }
        shouldThrow<TestErrorNoData.Failure2> {
            funcWithErrorNoData(1u)
        }
        shouldThrow<TestErrorNoData.Failure3> {
            funcWithErrorNoData(2u)
        }
        // This shouldn't throw
        funcWithErrorNoData(200u)
    }
}
