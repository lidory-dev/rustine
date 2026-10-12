/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.dsl

import rustine.gradle.rust.targets.RustAndroidTarget
import org.gradle.api.Project
import javax.inject.Inject

/**
 * Contains settings for Rust builds for Android.
 */
@Suppress("LeakingThis")
abstract class CargoAndroidBuild @Inject constructor(
    project: Project,
    rustTarget: RustAndroidTarget,
    extension: CargoExtension,
) : DefaultCargoBuild<RustAndroidTarget, CargoAndroidBuildVariant>(
    project,
    rustTarget,
    extension,
    CargoAndroidBuildVariant::class,
), CargoMobileBuild<CargoAndroidBuildVariant>,
    CargoNativeBuild<CargoAndroidBuildVariant>,
    HasDynamicLibraries,
    HasAndroidVariant {
    init {
        embedRustLibrary.convention(true)
    }
}
