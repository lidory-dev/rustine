/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.dsl

import rustine.gradle.Variant
import rustine.gradle.rust.targets.RustAppleMobileTarget
import org.gradle.api.Project
import javax.inject.Inject

abstract class CargoAppleMobileBuildVariant @Inject constructor(
    project: Project,
    build: CargoAppleMobileBuild,
    variant: Variant,
    extension: CargoExtension,
) : DefaultCargoBuildVariant<RustAppleMobileTarget, CargoAppleMobileBuild>(project, build, variant, extension),
    CargoMobileBuildVariant<RustAppleMobileTarget>,
    CargoNativeBuildVariant<RustAppleMobileTarget>
