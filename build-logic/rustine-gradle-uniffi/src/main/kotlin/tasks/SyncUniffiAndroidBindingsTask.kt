/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.uniffi.tasks

import rustine.gradle.InternalRustineGradleApi
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Copies the generated bindings of single-target Android projects into [outputDirectory], which is
 * added to the Kotlin sources of each Android variant using the Android Gradle Plugin Variant API.
 * [outputDirectory] is set by the Android Gradle Plugin.
 */
@InternalRustineGradleApi
@DisableCachingByDefault(because = "Copying files is not worth caching")
abstract class SyncUniffiAndroidBindingsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bindingsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun syncBindings() {
        fileSystemOperations.sync {
            from(bindingsDirectory)
            into(outputDirectory)
        }
    }
}
