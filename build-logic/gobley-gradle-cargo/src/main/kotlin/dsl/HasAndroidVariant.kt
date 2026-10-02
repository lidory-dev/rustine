/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.dsl

import gobley.gradle.Variant
import org.gradle.api.provider.Property

interface HasAndroidVariant {
    /**
     * The variant to embed into Android modules without build types, which are Kotlin
     * Multiplatform libraries using the `com.android.kotlin.multiplatform.library` plugin.
     * Defaults to [Variant.Release] when a task requested in the command line of the root build
     * looks like a release build or a publishing task (its name contains `release` or starts with
     * `publish`, e.g., `assembleRelease`, `bundleRelease`, or `publishToMavenLocal`), and to
     * [Variant.Debug] otherwise. Setting this will override the `androidVariant` properties in outer blocks. For
     * example, in the following DSL:
     * ```kotlin
     * cargo {
     *   androidVariant = Variant.Release
     *   builds.android {
     *     androidVariant = Variant.Debug
     *   }
     * }
     * ```
     * The Android builds will use the debug profile.
     *
     * This property is ignored in `com.android.application` and `com.android.library` modules,
     * where the build type of each Android variant decides the variant: [Variant.Debug] when the
     * name of the build type contains `debug`, and [Variant.Release] otherwise.
     *
     * Since the default value depends on the requested task names, tasks requested in other ways
     * (e.g., `./gradlew build`, `./gradlew assemble`, or abbreviated task names) use
     * [Variant.Debug]. Use Gradle properties or environment variables to control this property in
     * such cases.
     * ```kotlin
     * cargo {
     *   findProperty("my.project.android.variant")?.let { androidVariant = Variant(it.toString()) }
     * }
     * ```
     */
    val androidVariant: Property<Variant>
}
