/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.kotlin

import com.android.build.api.dsl.CommonExtension
import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.PluginIds
import rustine.gradle.Variant
import rustine.gradle.android.androidBuildTypeVariant
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * The delegate for single-target Android projects, which use the built-in Kotlin support of the
 * Android Gradle Plugin. [pluginId] is [PluginIds.KOTLIN_ANDROID] even though the Kotlin Android
 * plugin is not applied.
 */
@OptIn(InternalRustineGradleApi::class)
class RustineKotlinAndroidExtensionDelegate(
    project: Project
) : RustineKotlinExtensionDelegate {
    private val kotlinAndroidExtension: KotlinAndroidProjectExtension =
        project.extensions.getByType()

    override val pluginId = PluginIds.KOTLIN_ANDROID

    override val targets: DomainObjectCollection<KotlinTarget> =
        project.container(KotlinTarget::class.java)

    override val sourceSets: RustineKotlinSourceSetCollection =
        RustineKotlinAndroidSourceSetCollection(project.extensions.getByType())

    override val implementationVersion: String?
        get() = kotlinAndroidExtension.javaClass.`package`.implementationVersion

    override val jvmTarget: KotlinTarget? = null

    override val androidTarget: KotlinTarget?
        get() = targets.firstOrNull()

    init {
        // The target is available as soon as the extension is created.
        targets.add(kotlinAndroidExtension.target)
    }
}

// With the built-in Kotlin support, the Android Gradle Plugin doesn't create Kotlin source sets
// (android.disallowKotlinSourceSets), and accessing kotlin.sourceSets is deprecated. Sources are
// added using the Android Gradle Plugin Variant API, and dependencies are added to the
// configurations of the Android source sets.
@OptIn(InternalRustineGradleApi::class)
private class RustineKotlinAndroidSourceSetCollection(
    private val androidExtension: CommonExtension,
) : RustineKotlinSourceSetCollection {
    override val commonMain: KotlinSourceSet get() = error("not supported")

    override val androidMain: KotlinSourceSet? get() = null

    override fun androidMainConfigurations(variant: Variant?): RustineConfigurationNames {
        return when (variant) {
            Variant.Debug -> RustineConfigurationNames("debugImplementation", "debugRuntimeOnly")
            Variant.Release -> RustineConfigurationNames("releaseImplementation", "releaseRuntimeOnly")
            null -> RustineConfigurationNames("implementation", "runtimeOnly")
        }
    }

    // The local unit tests of each build type, including custom build types, have their own
    // configurations, e.g., `testStagingRuntimeOnly`. Build types are mapped to Rustine variants as
    // in RustineAndroidVariant.variant. Must be called after the build script is evaluated.
    override fun androidUnitTestConfigurations(variant: Variant?): List<RustineConfigurationNames> {
        if (variant == null) {
            return listOf(RustineConfigurationNames("testImplementation", "testRuntimeOnly"))
        }
        return androidExtension.buildTypes.names
            .filter { androidBuildTypeVariant(it) == variant }
            .map { buildType ->
                val sourceSetName = "test${buildType.uppercaseFirstChar()}"
                RustineConfigurationNames(
                    "${sourceSetName}Implementation",
                    "${sourceSetName}RuntimeOnly",
                )
            }
    }

    override val jvmMain: KotlinSourceSet get() = error("not supported")
    override val jsMain: KotlinSourceSet get() = error("not supported")
    override val wasmJsMain: KotlinSourceSet get() = error("not supported")
    override val wasmWasiMain: KotlinSourceSet get() = error("not supported")
}
