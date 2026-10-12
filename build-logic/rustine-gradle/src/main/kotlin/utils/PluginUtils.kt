/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.utils

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.PluginIds
import rustine.gradle.android.RustineAndroidExtensionDelegate
import rustine.gradle.android.RustineAndroidPluginKind
import rustine.gradle.kotlin.RustineKotlinAndroidExtensionDelegate
import rustine.gradle.kotlin.RustineKotlinExtensionDelegate
import rustine.gradle.kotlin.RustineKotlinJvmExtensionDelegate
import rustine.gradle.kotlin.RustineKotlinMultiplatformExtensionDelegate
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

@InternalRustineGradleApi
object PluginUtils {
    @InternalRustineGradleApi
    data class PluginInfo(val pluginName: String, val id: String)

    fun ensurePluginIsApplied(project: Project, pluginName: String, id: String) {
        ensurePluginIsApplied(project, PluginInfo(pluginName, id))
    }

    fun ensurePluginIsApplied(project: Project, vararg plugins: PluginInfo) {
        if (plugins.isEmpty()) {
            return
        }
        for (plugin in plugins) {
            if (project.plugins.hasPlugin(plugin.id)) {
                return
            }
        }
        project.logger.error(requiredPluginMessage(*plugins))
        if (plugins.size == 1) {
            throw GradleException("No ${plugins[0].pluginName} Gradle plugin found")
        } else {
            throw GradleException("Some required Gradle plugins not found")
        }
    }

    /**
     * Fails the build when [kotlinPluginFound] is `false`, i.e., when [withKotlinPlugin] has not
     * found any supported Kotlin plugin.
     */
    fun ensureKotlinPluginIsApplied(project: Project, kotlinPluginFound: Boolean) {
        if (kotlinPluginFound) {
            return
        }
        val plugins = arrayOf(
            PluginInfo("Kotlin Multiplatform", PluginIds.KOTLIN_MULTIPLATFORM),
            PluginInfo("Kotlin JVM", PluginIds.KOTLIN_JVM),
            PluginInfo("Android Application", PluginIds.ANDROID_APPLICATION),
            PluginInfo("Android Library", PluginIds.ANDROID_LIBRARY),
        )
        project.logger.error(StringBuilder().apply {
            append(requiredPluginMessage(*plugins))
            append("\n")
            append("Android application and library projects are supported through the built-in Kotlin support of ")
            append("the Android Gradle Plugin. For Kotlin Multiplatform projects targeting Android, apply ")
            append("the ${PluginIds.ANDROID_KOTLIN_MULTIPLATFORM_LIBRARY} plugin as well.\n")
        }.toString())
        throw GradleException("Some required Gradle plugins not found")
    }

    private fun requiredPluginMessage(vararg plugins: PluginInfo): String {
        val message = if (plugins.size == 1) {
            "Please include the ${plugins[0].pluginName} Gradle plugin in your build configuration."
        } else {
            "Please include one of the following Gradle plugins in your build configuration."
        }
        return StringBuilder().apply {
            append(message)
            append("\n\n")
            append("plugins {\n")
            append("  // ...\n")
            var first = true
            for (plugin in plugins) {
                if (!first) {
                    append("  // or\n")
                }
                first = false
                append("  id(\"${plugin.id}\")\n")
            }
            append("  // ...\n")
            append("}\n")
        }.toString()
    }

    fun withKotlinPlugin(project: Project, action: (RustineKotlinExtensionDelegate) -> Unit) {
        project.plugins.withId(PluginIds.KOTLIN_MULTIPLATFORM) {
            action(RustineKotlinMultiplatformExtensionDelegate(project))
        }
        project.plugins.withId(PluginIds.KOTLIN_JVM) {
            action(RustineKotlinJvmExtensionDelegate(project))
        }
        // With the built-in Kotlin support of the Android Gradle Plugin, which is enabled by
        // default since AGP 9.0, no Kotlin plugin ID is applied. Instead, the Android Gradle Plugin
        // registers the `kotlin` extension while it is applied, so the extension is available when
        // the following callbacks are invoked.
        for (androidPluginId in arrayOf(PluginIds.ANDROID_APPLICATION, PluginIds.ANDROID_LIBRARY)) {
            project.plugins.withId(androidPluginId) {
                if (project.extensions.findByType<KotlinAndroidProjectExtension>() != null) {
                    action(RustineKotlinAndroidExtensionDelegate(project))
                }
            }
        }
    }

    /**
     * Invokes [action] when an Android Gradle Plugin is applied. As [action] is invoked right after
     * the Android Gradle Plugin is applied, [action] can register Android Gradle Plugin callbacks
     * like [RustineAndroidExtensionDelegate.onVariants].
     */
    fun withAndroidPlugin(project: Project, action: (RustineAndroidExtensionDelegate) -> Unit) {
        project.plugins.withId(PluginIds.ANDROID_APPLICATION) {
            action(RustineAndroidExtensionDelegate(project, RustineAndroidPluginKind.Application))
        }
        project.plugins.withId(PluginIds.ANDROID_LIBRARY) {
            action(RustineAndroidExtensionDelegate(project, RustineAndroidPluginKind.Library))
        }
        project.plugins.withId(PluginIds.ANDROID_KOTLIN_MULTIPLATFORM_LIBRARY) {
            action(
                RustineAndroidExtensionDelegate(
                    project,
                    RustineAndroidPluginKind.KotlinMultiplatformLibrary,
                )
            )
        }
    }
}
