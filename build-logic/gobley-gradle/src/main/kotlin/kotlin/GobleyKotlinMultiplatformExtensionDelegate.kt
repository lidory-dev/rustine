/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.kotlin

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import gobley.gradle.Variant
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinWasmTargetType
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

@OptIn(InternalGobleyGradleApi::class)
class GobleyKotlinMultiplatformExtensionDelegate(
    project: Project
) : GobleyKotlinExtensionDelegate {
    private val kotlinMultiplatformExtension: KotlinMultiplatformExtension =
        project.extensions.getByType()

    override val pluginId = PluginIds.KOTLIN_MULTIPLATFORM

    override val targets: DomainObjectCollection<KotlinTarget>
        get() = kotlinMultiplatformExtension.targets

    override val sourceSets: GobleyKotlinSourceSetCollection
        get() = GobleyKotlinSourceSetCollection(
            kotlinMultiplatformExtension.targets,
            kotlinMultiplatformExtension.sourceSets,
        )

    override val implementationVersion: String?
        get() = kotlinMultiplatformExtension.javaClass.`package`.implementationVersion

    override val jvmTarget: KotlinTarget?
        get() = targets.firstOrNull { it is KotlinJvmTarget }

    // The target of the `com.android.kotlin.multiplatform.library` plugin is not a
    // `KotlinAndroidTarget`.
    override val androidTarget: KotlinTarget?
        get() = targets.firstOrNull { it.platformType == KotlinPlatformType.androidJvm }
}

@OptIn(InternalGobleyGradleApi::class)
private fun GobleyKotlinSourceSetCollection(
    targets: DomainObjectCollection<KotlinTarget>,
    sourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
): GobleyKotlinSourceSetCollection {
    return object : GobleyKotlinSourceSetCollection {
        override val commonMain: KotlinSourceSet
            get() = sourceSets.getByName("commonMain")

        private fun androidTarget(): KotlinTarget? {
            return targets.firstOrNull { it.platformType == KotlinPlatformType.androidJvm }
        }

        // The compilations of the `com.android.kotlin.multiplatform.library` plugin are `main`,
        // `hostTest`, and `deviceTest`, whose default source sets are `androidMain`,
        // `androidHostTest`, and `androidDeviceTest`. `hostTest` exists only when host tests are
        // enabled using `withHostTest {}`.
        override val androidMain: KotlinSourceSet?
            get() = androidTarget()?.compilations?.findByName("main")?.defaultSourceSet

        override fun androidMainConfigurations(variant: Variant?): GobleyConfigurationNames? {
            return when (variant) {
                // There is a single variant without build type source sets.
                Variant.Debug, Variant.Release -> null
                null -> androidMain?.configurationNames
            }
        }

        override fun androidUnitTestConfigurations(variant: Variant?): List<GobleyConfigurationNames> {
            return when (variant) {
                // Host tests use the debug Rust library for the host.
                Variant.Debug, null -> listOfNotNull(
                    androidTarget()
                        ?.compilations
                        ?.findByName("hostTest")
                        ?.defaultSourceSet
                        ?.configurationNames
                )

                Variant.Release -> emptyList()
            }
        }

        private fun getSourceSetByTarget(
            targetName: String,
            target: () -> KotlinTarget?
        ): KotlinSourceSet {
            val retrievedTarget = target() ?: error("$targetName target not present")
            return sourceSets.getByName("${retrievedTarget.name}Main")
        }

        private fun jvmTarget(): KotlinJvmTarget? {
            return targets.filterIsInstance<KotlinJvmTarget>().firstOrNull()
        }

        override val jvmMain: KotlinSourceSet
            get() = getSourceSetByTarget("JVM", ::jvmTarget)

        private fun jsTarget(): KotlinJsIrTarget? {
            val jsIrTarget = targets.firstOrNull {
                it.platformType == KotlinPlatformType.js
            } as? KotlinJsIrTarget
            return jsIrTarget
        }

        override val jsMain: KotlinSourceSet
            get() = getSourceSetByTarget("JS", ::jsTarget)

        private fun wasmTarget(wasmTargetType: KotlinWasmTargetType): KotlinJsIrTarget? {
            return targets
                .filterIsInstance<KotlinJsIrTarget>()
                .firstOrNull {
                    it.platformType == KotlinPlatformType.wasm
                            && it.wasmTargetType == wasmTargetType
                }
        }

        override val wasmJsMain: KotlinSourceSet
            get() = getSourceSetByTarget("WASM JS") {
                wasmTarget(KotlinWasmTargetType.JS)
            }

        override val wasmWasiMain: KotlinSourceSet
            get() = getSourceSetByTarget("WASM WASI") {
                wasmTarget(KotlinWasmTargetType.WASI)
            }
    }
}