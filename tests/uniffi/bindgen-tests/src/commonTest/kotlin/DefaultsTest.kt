/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DefaultsTest {
    @Test
    fun testRecords() {
        val r = RecWithDefault()
        r.n shouldBe 42.toUByte()
        r.v shouldBe listOf()
    }

    @Test
    fun testEnums() {
        val e = EnumWithDefault.OtherVariant()
        e shouldBe EnumWithDefault.OtherVariant(a = "default")
    }

    @Test
    fun testDefaultArguments() {
        funcWithDefault() shouldBe "DEFAULT"
        funcWithDefault("NON-DEFAULT") shouldBe "NON-DEFAULT"

        val i = InterfaceWithDefaults()
        i.methodWithDefault() shouldBe "DEFAULT"
        i.methodWithDefault("NON-DEFAULT") shouldBe "NON-DEFAULT"
    }
}
