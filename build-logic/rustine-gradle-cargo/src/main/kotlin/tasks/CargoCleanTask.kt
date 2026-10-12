/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.tasks

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.tasks.GloballyLockedTask
import rustine.gradle.tasks.globalLock
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Cleaning task cannot be cached")
@OptIn(InternalRustineGradleApi::class)
abstract class CargoCleanTask : CargoPackageTask(), GloballyLockedTask {
    @TaskAction
    fun cleanPackage() {
        val workspaceRoot = cargoPackage.get().workspaceRoot.asFile.absoluteFile
        val rootProjectDirectory = rootProjectDirectory.get().asFile.absoluteFile
        val relativePath = workspaceRoot.relativeToOrNull(rootProjectDirectory)?.toString()
        val identifier = when {
            relativePath.isNullOrEmpty() -> "cargoClean"
            // If the workspace directory is under the root project, use lock `cargoClean/<relative path>`.
            else -> "cargoClean/$relativePath"
        }
        globalLock(identifier) {
            cargo("clean").get().assertNormalExitValue()
        }
    }
}
