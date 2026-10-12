/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.dsl

import rustine.gradle.RustineHost
import rustine.gradle.rust.targets.RustWindowsTarget
import org.gradle.api.Project
import javax.inject.Inject

/**
 * Contains settings for Rust builds for Windows.
 */
@Suppress("LeakingThis")
abstract class CargoWindowsBuild @Inject constructor(
    project: Project,
    rustTarget: RustWindowsTarget,
    extension: CargoExtension,
) : DefaultCargoBuild<RustWindowsTarget, CargoWindowsBuildVariant>(
    project,
    rustTarget,
    extension,
    CargoWindowsBuildVariant::class,
), CargoJvmBuild<CargoWindowsBuildVariant> {
    init {
        embedRustLibrary.convention(true)
        resourcePrefix.convention(rustTarget.jnaResourcePrefix)
        androidUnitTest.convention(
            embedRustLibrary.map { it && rustTarget == RustineHost.current.rustTarget }
        )
    }
}
