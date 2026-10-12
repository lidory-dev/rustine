/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.kotlin

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.PluginIds
import rustine.gradle.Variant
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@OptIn(InternalRustineGradleApi::class)
class RustineKotlinJvmExtensionDelegate(
    project: Project
) : RustineKotlinExtensionDelegate {
    private val kotlinJvmExtension: KotlinJvmProjectExtension =
        project.extensions.getByType()

    override val pluginId = PluginIds.KOTLIN_JVM

    override val targets: DomainObjectCollection<KotlinTarget> =
        project.container(KotlinTarget::class.java)

    override val sourceSets: RustineKotlinSourceSetCollection
        get() = RustineKotlinSourceSetCollection(kotlinJvmExtension.sourceSets)

    override val implementationVersion: String?
        get() = kotlinJvmExtension.javaClass.`package`.implementationVersion

    override val jvmTarget: KotlinTarget?
        get() = targets.firstOrNull()

    override val androidTarget: KotlinTarget? = null

    init {
        kotlinJvmExtension.target { targets.add(this) }
    }
}

@OptIn(InternalRustineGradleApi::class)
private fun RustineKotlinSourceSetCollection(sourceSets: NamedDomainObjectContainer<KotlinSourceSet>): RustineKotlinSourceSetCollection {
    return object : RustineKotlinSourceSetCollection {
        override val commonMain get() = jvmMain
        override val androidMain: KotlinSourceSet? get() = null
        override fun androidMainConfigurations(variant: Variant?): RustineConfigurationNames? = null
        override fun androidUnitTestConfigurations(variant: Variant?): List<RustineConfigurationNames> = emptyList()
        override val jvmMain: KotlinSourceSet get() = sourceSets.getByName("main")
        override val jsMain: KotlinSourceSet get() = error("not supported")
        override val wasmJsMain: KotlinSourceSet get() = error("not supported")
        override val wasmWasiMain: KotlinSourceSet get() = error("not supported")
    }
}