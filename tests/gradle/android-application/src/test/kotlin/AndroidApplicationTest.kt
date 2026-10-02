/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.tests.gradle.androidapplication

import gobley.uniffi.tests.gradle.androidapplication.plaindep.formatGreeting
import org.junit.Assert.assertEquals
import org.junit.Test

// Runs as an Android local unit test of the debug and staging build types, which loads the Rust
// library built for the host.
class AndroidApplicationTest {
    @Test
    fun addCallsRust() {
        assertEquals(5, add(2, 3))
    }

    @Test
    fun greeterCallsRust() {
        Greeter("Hello").use { greeter ->
            assertEquals(formatGreeting("Hello", "Gobley"), greeter.greet("Gobley"))
        }
    }
}
