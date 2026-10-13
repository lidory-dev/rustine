/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.tasks

import rustine.gradle.RustineHost
import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.cargo.dsl.CargoBinaryCrateSource
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.io.File

@CacheableTask
abstract class CargoInstallTask : CargoTask() {
    @get:Input
    val quiet: Property<Boolean> = project.objects.property<Boolean>().convention(true)

    @get:Input
    abstract val binaryCrateSource: Property<CargoBinaryCrateSource>

    @get:OutputDirectory
    abstract val installDirectory: DirectoryProperty

    init {
        // A crate at a local path can change while the path stays the same. Track the files
        // Cargo reads to build it, so the binary is reinstalled when one of them changes instead
        // of being reused while stale. The task stays up to date otherwise, which matters because
        // `cargo install` updates the registry index even when there is nothing to rebuild, and
        // fails without network access.
        val crateFiles = binaryCrateSource.map { source ->
            when (source) {
                is CargoBinaryCrateSource.Path -> localCrateFiles(File(source.path))
                else -> emptyList()
            }
        }
        inputs.files(crateFiles)
            .withPropertyName("localCrateFiles")
            .withPathSensitivity(PathSensitivity.ABSOLUTE)
        // The tracked files do not cover everything that affects the binary, such as path
        // dependencies or the installed toolchains, so do not share it through the build cache.
        outputs.doNotCacheIf("The binary crate is installed from a local path") { task ->
            (task as CargoInstallTask).binaryCrateSource.get() is CargoBinaryCrateSource.Path
        }
    }

    protected fun binaryCrateOutput(crateName: String): RegularFileProperty {
        return project.objects.fileProperty()
            .convention(
                installDirectory.file(
                    RustineHost.current.platform.convertExeName("bin/$crateName")
                )
            )
    }

    // TODO: Make this public in the next version
    @InternalRustineGradleApi
    @get:Input
    internal val locked: Property<Boolean> = project.objects.property<Boolean>().convention(true)

    @TaskAction
    fun installBinaries() {
        @OptIn(InternalRustineGradleApi::class)
        cargo("install") {
            arguments("--root", installDirectory)
            arguments("--target", RustineHost.current.rustTarget.rustTriple)
            if (quiet.get()) {
                arguments("--quiet")
            }
            if (locked.get()) {
                arguments("--locked")
            }

            when (val source = binaryCrateSource.get()) {
                is CargoBinaryCrateSource.Registry -> {
                    arguments("${source.packageName}@${source.version}")
                    if (source.registry != null) {
                        arguments("--registry", source.registry)
                    }
                }

                is CargoBinaryCrateSource.Path -> arguments("--path", source.path)
                is CargoBinaryCrateSource.Git -> {
                    arguments("--git", source.repository)
                    when (source.commit) {
                        is CargoBinaryCrateSource.Git.Commit.Branch -> arguments(
                            "--branch",
                            source.commit.branch
                        )

                        is CargoBinaryCrateSource.Git.Commit.Tag -> arguments(
                            "--tag",
                            source.commit.tag
                        )

                        is CargoBinaryCrateSource.Git.Commit.Revision -> arguments(
                            "--rev",
                            source.commit.revision
                        )

                        else -> {}
                    }
                }
            }
            suppressXcodeIosToolchains()
        }.get().assertNormalExitValue()
    }
}

/**
 * The sources of the crate at [crateDirectory], plus the manifests, lockfiles, toolchain files and
 * Cargo configurations that Cargo looks up in its ancestor directories. Files that do not exist
 * are included too, so creating one also reinstalls the crate. This only computes paths and does
 * not access the file system.
 */
private fun localCrateFiles(crateDirectory: File): List<File> {
    val directory = crateDirectory.absoluteFile
    val crateSources = listOf("src", "build.rs", "askama.toml").map(directory::resolve)
    val workspaceFiles = generateSequence(directory) { it.parentFile }.flatMap { ancestor ->
        listOf(
            "Cargo.toml",
            "Cargo.lock",
            "rust-toolchain",
            "rust-toolchain.toml",
            ".cargo/config",
            ".cargo/config.toml",
        ).map(ancestor::resolve)
    }
    return crateSources + workspaceFiles
}
