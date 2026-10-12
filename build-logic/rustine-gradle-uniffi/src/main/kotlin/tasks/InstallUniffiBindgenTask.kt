/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.uniffi.tasks

import rustine.gradle.BuildConfig
import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.cargo.tasks.CargoInstallTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile

@CacheableTask
abstract class InstallUniffiBindgenTask : CargoInstallTask() {
    @OptIn(InternalRustineGradleApi::class)
    @get:OutputFile
    val bindgen = binaryCrateOutput(BuildConfig.UNIFFI_BINDGEN_BIN)
}
