/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.android

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.Variant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * The Android Gradle Plugin applied to the project.
 */
@InternalRustineGradleApi
enum class RustineAndroidPluginKind {
    /**
     * `com.android.application`
     */
    Application,

    /**
     * `com.android.library`
     */
    Library,

    /**
     * `com.android.kotlin.multiplatform.library`
     */
    KotlinMultiplatformLibrary,
}

/**
 * Wraps the Android Gradle Plugin extensions. Instances are created by
 * [rustine.gradle.utils.PluginUtils.withAndroidPlugin] when the Android Gradle Plugin is applied.
 *
 * The properties read the Android DSL, so read them only after the build script is evaluated.
 */
@InternalRustineGradleApi
interface RustineAndroidExtensionDelegate {
    val kind: RustineAndroidPluginKind

    /**
     * `false` when the project has a single variant without a build type, which is the case of
     * `com.android.kotlin.multiplatform.library`.
     */
    val hasBuildTypes: Boolean

    val androidSdkRoot: File
    val androidMinSdk: Int
    val androidNdkRoot: File?
    val androidNdkVersion: String?
    val abiFilters: Set<String>

    /**
     * Registers [action] to be invoked for every Android variant. As the Android Gradle Plugin
     * rejects callbacks registered after its `afterEvaluate` callback, this must be called when the
     * plugin is applied. [action] is invoked during the `afterEvaluate` callback of the Android
     * Gradle Plugin, which may run before the `afterEvaluate` callbacks of Rustine plugins. Tasks
     * registered in the `afterEvaluate` callbacks of Rustine plugins must not be retrieved in
     * [action].
     */
    fun onVariants(action: (RustineAndroidVariant) -> Unit)

    /**
     * Adds the given ProGuard rule file to every Android variant when [enabled] is `true`.
     * Application modules use the file to minify the application, while library modules publish
     * the file as a consumer rule file as well. Must be called when the plugin is applied.
     */
    fun addProguardFile(file: Provider<RegularFile>, enabled: Provider<Boolean>)
}

/**
 * Wraps a variant of the Android Gradle Plugin Variant API.
 */
@InternalRustineGradleApi
interface RustineAndroidVariant {
    val name: String

    /**
     * `null` when the Android module has no build types, i.e., when the module is using the
     * `com.android.kotlin.multiplatform.library` plugin.
     */
    val buildType: String?

    /**
     * The Rustine variant corresponding to [buildType]: [Variant.Debug] when the name of the build
     * type contains `debug`, and [Variant.Release] otherwise. `null` when [buildType] is `null`.
     */
    val variant: Variant?
        get() = buildType?.let(::androidBuildTypeVariant)

    /**
     * Adds the output directory of [task] as a generated `jniLibs` directory of this variant. The
     * directory must be laid out as `<ABI name>/lib*.so`.
     */
    fun <T : Task> addGeneratedJniLibsDirectory(
        task: TaskProvider<T>,
        wiredWith: (T) -> DirectoryProperty,
    )

    /**
     * Adds the output directory of [task] as a generated Kotlin source directory of this variant.
     */
    fun <T : Task> addGeneratedKotlinDirectory(
        task: TaskProvider<T>,
        wiredWith: (T) -> DirectoryProperty,
    )

    /**
     * Makes the pre-build lifecycle task of this variant depend on [dependencies], so that the
     * tasks of this variant run after [dependencies]. [dependencies] follow the rules of
     * [Task.dependsOn].
     */
    fun registerPreBuildDependencies(vararg dependencies: Any)
}

/**
 * Returns the Rustine variant used by the Android build type named [buildType]: [Variant.Debug] when
 * the name contains `debug`, and [Variant.Release] otherwise.
 */
internal fun androidBuildTypeVariant(buildType: String): Variant {
    return if (buildType.contains("debug", ignoreCase = true)) Variant.Debug else Variant.Release
}

@InternalRustineGradleApi
fun RustineAndroidExtensionDelegate(
    project: Project,
    kind: RustineAndroidPluginKind,
): RustineAndroidExtensionDelegate {
    return when (kind) {
        RustineAndroidPluginKind.Application -> RustineAndroidApplicationExtensionDelegate(project)
        RustineAndroidPluginKind.Library -> RustineAndroidLibraryExtensionDelegate(project)
        RustineAndroidPluginKind.KotlinMultiplatformLibrary ->
            RustineAndroidKotlinMultiplatformLibraryExtensionDelegate(project)
    }
}
