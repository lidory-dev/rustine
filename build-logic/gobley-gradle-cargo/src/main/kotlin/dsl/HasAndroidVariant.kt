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
     * Defaults to [Variant.Debug]. Setting this will override the `androidVariant` properties in
     * outer blocks. For example, in the following DSL:
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
     * In modules without build types, the UniFFI plugin also uses this property to determine the
     * variant of the Android build used to generate bindings.
     *
     * **FOR LIBRARY DEVELOPERS**: Unlike [HasJvmVariant], [HasAndroidVariant] does not distinguish
     * the publishing variant and the Android variant. It is very likely that you are using
     * [Variant.Debug] for library publishing. Use Gradle properties or environment variables to
     * control this property, and set this to [Variant.Release] for publishing.
     * ```kotlin
     * cargo {
     *   androidVariant = Variant(findProperty("my.project.android.variant")?.toString() ?: "debug")
     * }
     * ```
     */
    val androidVariant: Property<Variant>
}
