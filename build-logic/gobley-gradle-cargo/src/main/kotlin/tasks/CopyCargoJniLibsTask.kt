/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.cargo.tasks

import gobley.gradle.InternalGobleyGradleApi
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.newInstance
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Copies Rust dynamic libraries into [outputDirectory] in the layout of the `jniLibs` directory of
 * Android projects, i.e., `<ABI name>/lib*.so`. [outputDirectory] is set by the Android Gradle
 * Plugin when this task is added as a generated `jniLibs` directory of an Android variant.
 */
@InternalGobleyGradleApi
@DisableCachingByDefault(because = "Copying files is not worth caching")
abstract class CopyCargoJniLibsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Nested
    val abis: MutableList<Abi> = mutableListOf()

    @get:Inject
    abstract val objectFactory: ObjectFactory

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    /**
     * Adds libraries to copy into the `abiName` directory.
     */
    fun abi(abiName: String, action: Action<Abi>) {
        abis.add(objectFactory.newInstance<Abi>().apply {
            this.abiName.set(abiName)
            action.execute(this)
        })
    }

    @TaskAction
    fun copyLibraries() {
        fileSystemOperations.sync {
            into(outputDirectory)
            for (abi in abis) {
                from(abi.libraries) {
                    into(abi.abiName.get())
                }
            }
        }
    }

    @InternalGobleyGradleApi
    abstract class Abi {
        @get:Input
        abstract val abiName: Property<String>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NAME_ONLY)
        abstract val libraries: ConfigurableFileCollection
    }
}
