/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import gobley.gradle.InternalGobleyGradleApi
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

/**
 * The delegate for `com.android.kotlin.multiplatform.library`. The plugin has a single variant
 * without a build type, and has no `android {}` project extension.
 */
@InternalGobleyGradleApi
internal class GobleyAndroidKotlinMultiplatformLibraryExtensionDelegate(
    private val project: Project,
) : GobleyAndroidExtensionDelegate {
    private val androidComponents: KotlinMultiplatformAndroidComponentsExtension =
        project.extensions.getByType()

    // The target is created when the Kotlin Multiplatform plugin is applied, which may happen
    // after this delegate is created.
    private val androidTarget: KotlinMultiplatformAndroidLibraryTarget?
        get() = project.extensions.findByType<KotlinMultiplatformExtension>()
            ?.targets
            ?.withType(KotlinMultiplatformAndroidLibraryTarget::class.java)
            ?.firstOrNull()

    init {
        // Gobley recognizes the Android target by the `androidJvm` platform type. With this
        // experimental option, the target uses the `jvm` platform type instead and would be
        // treated as a JVM target.
        val useJvmPlatformType = project.providers
            .gradleProperty("android.kmp.use.jvm.platform.type")
            .orNull
            ?.toBoolean() == true
        if (useJvmPlatformType) {
            throw GradleException(
                "Gobley does not support android.kmp.use.jvm.platform.type=true. Please remove the property."
            )
        }
    }

    override val kind: GobleyAndroidPluginKind = GobleyAndroidPluginKind.KotlinMultiplatformLibrary

    override val hasBuildTypes: Boolean = false

    override val androidSdkRoot: File
        get() = androidComponents.sdkComponents.sdkDirectory.get().asFile

    override val androidMinSdk: Int
        get() = androidTarget?.minSdk ?: 21

    // The NDK version and path can only be configured using the `com.android.settings` plugin.
    // `androidComponents.sdkComponents.ndkDirectory` is not used here as it throws when the default
    // NDK version of the Android Gradle Plugin is not installed. When these are `null`, the latest
    // NDK installed in the SDK directory is used.
    override val androidNdkRoot: File?
        get() = null
    override val androidNdkVersion: String?
        get() = null

    // There is no ABI filter DSL. All ABIs are built unless `embedRustLibrary` is set to `false`.
    override val abiFilters: Set<String>
        get() = emptySet()

    override fun onVariants(action: (GobleyAndroidVariant) -> Unit) {
        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            action(GobleyAndroidVariantImpl(variant))
        }
    }

    @Suppress("UnstableApiUsage")
    override fun addProguardFile(file: Provider<RegularFile>, enabled: Provider<Boolean>) {
        // Publishing consumer ProGuard rules is opt-in for Kotlin Multiplatform libraries. Turn it
        // on before the Android Gradle Plugin locks the DSL.
        androidComponents.finalizeDsl { extension ->
            if (enabled.get()) {
                extension.optimization.consumerKeepRules.publish = true
            }
        }
        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            if (enabled.get()) {
                // Used when the library itself is minified.
                variant.proguardFiles.add(file)
                // Published with the library and used when the consumer is minified.
                variant.consumerProguardFiles.add(file)
            }
        }
    }
}
