/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.uniffi.tests.gradle.androidapplication

import android.app.Application
import android.util.Log

// Calls into Rust when the application starts, so the bindings are kept when the application is
// minified.
class AndroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Greeter("Hello").use { greeter ->
            Log.i("AndroidApplication", "${greeter.greet("Rustine")} 1 + 2 = ${add(1, 2)}")
        }
    }
}
