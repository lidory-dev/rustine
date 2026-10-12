/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.dsl

import rustine.gradle.rust.targets.RustNativeTarget

/**
 * Contains settings for Rust builds for platforms supporting Kotlin/Native.
 */
interface CargoNativeBuild<out CargoBuildVariantT : CargoNativeBuildVariant<RustNativeTarget>> :
    CargoBuild<CargoBuildVariantT>, HasNativeVariant {
    override val rustTarget: RustNativeTarget
}
