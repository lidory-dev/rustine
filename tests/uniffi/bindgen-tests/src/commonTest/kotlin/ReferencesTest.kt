/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReferencesTest {
    @Test
    fun testReferences() {
        roundtripU8Ref(2.toUByte()) shouldBe 2.toUByte()

        val i = ReferenceTestInterface()
        i.doubleValue(2u) shouldBe 4u
        callDoubleValue(i, 3u) shouldBe 6u

        val t = createReferenceTestTraitInterface()
        callTripleValueTraitInterface(t, 10u) shouldBe 30u
    }
}
