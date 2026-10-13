/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.matchers.shouldBe
import mutable_records.*
import kotlin.test.Test

class MutableRecordsTest {
    @Test
    fun mutableRecords() {
        // MutableRecord should have var properties (can reassign)
        val mutable = MutableRecord(value = "initial")
        mutable.value = "modified"
        mutable.value shouldBe "modified"

        val dslMutable = DslMutableRecord(value = "initial")
        dslMutable.value = "modified"
        dslMutable.value shouldBe "modified"

        // ImmutableRecord should have val properties (immutable by default)
        // Just verify we can create it — attempting to assign would be a compile error
        val immutable = ImmutableRecord(value = "fixed")
        immutable.value shouldBe "fixed"
    }
}
