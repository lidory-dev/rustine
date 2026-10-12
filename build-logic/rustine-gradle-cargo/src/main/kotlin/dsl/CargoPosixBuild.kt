/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.dsl

import rustine.gradle.RustineHost
import rustine.gradle.rust.targets.RustPosixTarget
import org.gradle.api.Project
import javax.inject.Inject

/**
 * Contains settings for Rust builds for mobile platforms.
 */
@Suppress("LeakingThis")
abstract class CargoPosixBuild @Inject constructor(
    project: Project,
    rustTarget: RustPosixTarget,
    extension: CargoExtension,
) : DefaultCargoBuild<RustPosixTarget, CargoPosixBuildVariant>(
    project,
    rustTarget,
    extension,
    CargoPosixBuildVariant::class,
), CargoJvmBuild<CargoPosixBuildVariant>, CargoNativeBuild<CargoPosixBuildVariant> {
    init {
        embedRustLibrary.convention(
            // On Windows, CargoWindowsBuild is used by default.
            !RustineHost.Platform.Windows.isCurrent || !rustTarget.isWindows()
        )
        resourcePrefix.convention(rustTarget.jnaResourcePrefix)
        androidUnitTest.convention(
            embedRustLibrary.map { it && rustTarget == RustineHost.current.rustTarget }
        )
    }
}
