/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import bindgen_tests_external_types.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ExternalTypesTest {
    @Test
    fun testExternalTypes() {
        roundtripExtRecord(ExternalRec(a = 42.toUByte())) shouldBe ExternalRec(a = 42.toUByte())
        roundtripExtEnum(ExternalEnum.TWO) shouldBe ExternalEnum.TWO

        val extInterface = ExternalInterface(20u)
        roundtripExtInterface(extInterface).getValue() shouldBe 20u

        roundtripExtCustomType(100uL) shouldBe 100uL
    }
}
