/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.android

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import gobley.gradle.InternalGobleyGradleApi
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import java.io.File
import com.android.build.api.variant.Variant as AndroidVariant

/**
 * The delegate for `com.android.application` and `com.android.library`.
 */
@InternalGobleyGradleApi
internal abstract class GobleyAndroidCommonExtensionDelegate(
    project: Project,
) : GobleyAndroidExtensionDelegate {
    private val androidExtension: CommonExtension = project.extensions.getByType()

    protected abstract val androidComponents: AndroidComponentsExtension<*, *, *>

    override val hasBuildTypes: Boolean = true

    override val androidSdkRoot: File
        get() = androidComponents.sdkComponents.sdkDirectory.get().asFile

    // TODO: Read <uses-sdk> from AndroidManifest.xml
    // androidExtension.sourceSets.getByName("main").manifest.srcFile
    override val androidMinSdk: Int
        get() = androidExtension.defaultConfig.minSdk ?: 21
    override val androidNdkRoot: File?
        get() = androidExtension.ndkPath?.let(::File)
    override val androidNdkVersion: String?
        get() = androidExtension.ndkVersion.takeIf(String::isNotEmpty)
    override val abiFilters: Set<String>
        get() = androidExtension.defaultConfig.ndk.abiFilters
}

@InternalGobleyGradleApi
internal class GobleyAndroidApplicationExtensionDelegate(
    project: Project,
) : GobleyAndroidCommonExtensionDelegate(project) {
    override val androidComponents: ApplicationAndroidComponentsExtension =
        project.extensions.getByType()

    override val kind: GobleyAndroidPluginKind = GobleyAndroidPluginKind.Application

    override fun onVariants(action: (GobleyAndroidVariant) -> Unit) {
        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            action(GobleyAndroidVariantImpl(variant))
        }
    }

    override fun addProguardFile(file: Provider<RegularFile>, enabled: Provider<Boolean>) {
        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            if (enabled.get()) {
                // The provider carries the dependency on the task generating the file.
                variant.proguardFiles.add(file)
            }
        }
    }
}

@InternalGobleyGradleApi
internal class GobleyAndroidLibraryExtensionDelegate(
    project: Project,
) : GobleyAndroidCommonExtensionDelegate(project) {
    override val androidComponents: LibraryAndroidComponentsExtension =
        project.extensions.getByType()

    override val kind: GobleyAndroidPluginKind = GobleyAndroidPluginKind.Library

    override fun onVariants(action: (GobleyAndroidVariant) -> Unit) {
        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            action(GobleyAndroidVariantImpl(variant))
        }
    }

    @Suppress("UnstableApiUsage")
    override fun addProguardFile(file: Provider<RegularFile>, enabled: Provider<Boolean>) {
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

@InternalGobleyGradleApi
internal class GobleyAndroidVariantImpl(
    private val androidVariant: AndroidVariant,
) : GobleyAndroidVariant {
    override val name: String
        get() = androidVariant.name

    override val buildType: String?
        get() = androidVariant.buildType

    override fun <T : Task> addGeneratedJniLibsDirectory(
        task: TaskProvider<T>,
        wiredWith: (T) -> DirectoryProperty,
    ) {
        val jniLibs = androidVariant.sources.jniLibs
            ?: throw GradleException("Android variant ${androidVariant.name} does not support jniLibs")
        jniLibs.addGeneratedSourceDirectory(task, wiredWith)
    }

    override fun <T : Task> addGeneratedKotlinDirectory(
        task: TaskProvider<T>,
        wiredWith: (T) -> DirectoryProperty,
    ) {
        val kotlin = androidVariant.sources.kotlin
            ?: throw GradleException("Android variant ${androidVariant.name} does not support Kotlin sources")
        kotlin.addGeneratedSourceDirectory(task, wiredWith)
    }

    @Suppress("UnstableApiUsage")
    override fun registerPreBuildDependencies(vararg dependencies: Any) {
        androidVariant.lifecycleTasks.registerPreBuild(*dependencies)
    }
}
