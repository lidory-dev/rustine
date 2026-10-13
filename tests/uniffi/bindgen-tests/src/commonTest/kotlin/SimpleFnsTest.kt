/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class SimpleFnsTest {
    @Test
    fun testSimpleFns() {
        // the test here is just that we can successfully call a function across the FFI
        testFunc()

        shouldThrow<InternalException> {
            testUnexpectedErrorFunc()
        }
    }
}
