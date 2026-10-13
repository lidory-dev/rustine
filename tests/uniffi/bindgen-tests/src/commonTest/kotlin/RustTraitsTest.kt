/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class RustTraitsTest {
    @Test
    fun testDisplayAndDebug() {
        // Kotlin uses the display impl for `toString`
        RustTraitTest(1, 2).toString() shouldBe "display-test-string"

        // Kotlin falls back to the debug impl for `Display` is not exported
        RustTraitTest2(1, 2).toString() shouldBe "debug-test-string"
    }

    @Test
    fun testEq() {
        // The Rust code only uses `a` for the equality
        RustTraitTest(1, 2) shouldBe RustTraitTest(1, 3)
        RustTraitTest(1, 2) shouldNotBe RustTraitTest(2, 2)
    }

    @Test
    fun testOrd() {
        // The Rust code only uses `a` for the ordering
        (RustTraitTest(1, 2) < RustTraitTest(2, 3)).shouldBeTrue()
        (RustTraitTest(1, 2) <= RustTraitTest(1, 3) && RustTraitTest(1, 2) >= RustTraitTest(1, 3))
            .shouldBeTrue()
    }

    @Test
    fun testHash() {
        // The Rust code only uses `a` for the hash
        RustTraitTest(1, 2).hashCode() shouldBe RustTraitTest(1, 3).hashCode()
        RustTraitTest(2, 2).hashCode() shouldNotBe RustTraitTest(1, 2).hashCode()
        // Upstream doesn't call this function in Kotlin. `hashCode()` truncates the same hash.
        rustTraitTestHash(RustTraitTest(1, 2)).toInt() shouldBe RustTraitTest(1, 3).hashCode()
    }
}
