/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.rust

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.PluginIds
import rustine.gradle.rust.dsl.RustExtension
import rustine.gradle.utils.DependencyUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/**
 * This plugin is for exposing the following functions.
 */
class RustPlugin : Plugin<Project> {
    companion object {
        internal const val TASK_GROUP = "rust"
    }

    private lateinit var rustExtension: RustExtension

    override fun apply(target: Project) {
        @OptIn(InternalRustineGradleApi::class)
        if (!target.plugins.hasPlugin(PluginIds.RUSTINE_CARGO)) {
            DependencyUtils.createCargoConfigurations(target)
        }
        @OptIn(InternalRustineGradleApi::class)
        if (!target.plugins.hasPlugin(PluginIds.RUSTINE_UNIFFI)) {
            DependencyUtils.createUniFfiConfigurations(target)
        }
        rustExtension = target.extensions.create<RustExtension>(TASK_GROUP)
        @OptIn(InternalRustineGradleApi::class)
        target.afterEvaluate {
            if (!target.plugins.hasPlugin(PluginIds.RUSTINE_CARGO)) {
                DependencyUtils.resolveCargoDependencies(target)
            }
            if (!target.plugins.hasPlugin(PluginIds.RUSTINE_UNIFFI)) {
                DependencyUtils.resolveUniFfiDependencies(target)
            }
        }
    }
}
