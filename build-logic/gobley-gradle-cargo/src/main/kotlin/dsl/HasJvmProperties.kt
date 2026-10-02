/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import org.gradle.api.provider.Property

interface HasJvmProperties : HasJvmVariant, HasDynamicLibraries {
    /**
     * The resource prefix to use. In the `.jar` file, the Rust dynamic library will be located in
     * `<resource prefix>/<dynamic library>`. This defaults to `RustJvmTarget.jnaResourcePrefix`. The library then can
     * be loaded using [com.sun.jna.Native.register]. Users not using JNA can customize this property and implement
     * their own loading logic using [java.lang.ClassLoader]. If this property is empty, then the dynamic library
     * will be copied to the root of the `.jar` file.
     */
    val resourcePrefix: Property<String>

    /**
     * Determines whether to use the Rust dynamic library in Android local unit tests. Android local unit tests runs on
     * the build host's JVM, so it is required to include the Desktop Rust library in your Android `.jar` library. This
     * property defaults to `true` when this build targets your machine and `false` otherwise. For builds for Android
     * local unit tests, [jvmVariant] will be ignored and the plugin will select the variant automatically.
     *
     * In `com.android.application` and `com.android.library` modules, the library is added to the local unit tests of
     * each build type: the debug library when the name of the build type contains `debug`, and the release library
     * otherwise. In `com.android.kotlin.multiplatform.library` modules, the debug library is added to the host tests,
     * which are enabled using `kotlin { android { withHostTest {} } }`.
     */
    val androidUnitTest: Property<Boolean>
}
