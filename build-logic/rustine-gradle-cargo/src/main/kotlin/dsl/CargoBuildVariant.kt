/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.dsl

import rustine.gradle.cargo.tasks.CargoBuildTask
import rustine.gradle.cargo.tasks.CargoCheckTask
import rustine.gradle.rust.targets.RustTarget
import org.gradle.api.tasks.TaskProvider

/**
 * Contains settings for a variant of a build.
 */
interface CargoBuildVariant<out RustTargetT : RustTarget> : CargoVariant,
    HasRustTarget<RustTargetT> {
    /**
     * The build containing this variant.
     */
    val build: CargoBuild<CargoBuildVariant<RustTargetT>>

    /**
     * The Rust target triple to use to build the current target. The name of the `CargoBuild` is
     * `rustTarget.friendlyName`.
     */
    override val rustTarget: RustTargetT

    /**
     * The build task of the current variant.
     */
    val buildTaskProvider: TaskProvider<CargoBuildTask>

    /**
     * The check task of the current variant.
     */
    val checkTaskProvider: TaskProvider<CargoCheckTask>
}
