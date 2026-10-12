/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.kotlin

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.Variant
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

@InternalRustineGradleApi
interface RustineKotlinSourceSetCollection {
    val commonMain: KotlinSourceSet

    /**
     * The main source set of the Android target in Kotlin Multiplatform projects. `null` in
     * single-target Android projects, which use the built-in Kotlin support of the Android Gradle
     * Plugin and don't have Kotlin source sets.
     */
    val androidMain: KotlinSourceSet?

    /**
     * The configurations to add dependencies of the Android main code to. When [variant] is not
     * `null`, the configurations of the given variant are returned. `null` when the configurations
     * are not available, e.g., when the Android module has no build types.
     */
    fun androidMainConfigurations(variant: Variant? = null): RustineConfigurationNames?

    /**
     * The configurations to add dependencies of Android local unit tests to. When [variant] is
     * `null`, the configurations shared by all local unit tests are returned. Otherwise, the
     * configurations of the local unit tests using the Rust library of [variant] are returned, e.g.,
     * those of every build type using [variant]. Empty when the configurations are not available,
     * e.g., when host tests are not enabled in Kotlin Multiplatform projects.
     */
    fun androidUnitTestConfigurations(variant: Variant? = null): List<RustineConfigurationNames>

    val jvmMain: KotlinSourceSet
    val jsMain: KotlinSourceSet
    val wasmJsMain: KotlinSourceSet
    val wasmWasiMain: KotlinSourceSet
}

/**
 * The names of dependency configurations. The configurations may not exist yet.
 */
@InternalRustineGradleApi
data class RustineConfigurationNames(
    val implementation: String,
    val runtimeOnly: String,
)

@InternalRustineGradleApi
internal val KotlinSourceSet.configurationNames: RustineConfigurationNames
    get() = RustineConfigurationNames(
        implementation = implementationConfigurationName,
        runtimeOnly = runtimeOnlyConfigurationName,
    )
