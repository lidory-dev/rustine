/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.rust.dsl

import rustine.gradle.PluginIds
import rustine.gradle.RustineHost
import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.utils.PluginUtils
import rustine.gradle.utils.RustVersionUtils
import rustine.gradle.utils.command
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

abstract class RustExtension(project: Project) {
    /**
     * The directory where `cargo` and `rustup` are installed. Defaults to `~/.cargo/bin`.
     */
    val toolchainDirectory: Property<File> =
        project.objects.property<File>()
            .convention(RustineHost.current.platform.defaultToolchainDirectory)
}

fun KotlinMultiplatformExtension.hostNativeTarget(
    configure: KotlinNativeTarget.() -> Unit = {},
): KotlinNativeTarget {
    return when (RustineHost.Platform.current) {
        RustineHost.Platform.Windows -> mingwX64(configure)
        RustineHost.Platform.MacOS -> when (RustineHost.Arch.current) {
            RustineHost.Arch.X64 -> macosX64(configure)
            RustineHost.Arch.Arm64 -> macosArm64(configure)
        }

        RustineHost.Platform.Linux -> when (RustineHost.Arch.current) {
            RustineHost.Arch.X64 -> linuxX64(configure)
            RustineHost.Arch.Arm64 -> linuxArm64(configure)
        }
    }
}

fun KotlinMultiplatformExtension.hostNativeTarget(
    name: String,
    configure: KotlinNativeTarget.() -> Unit = {},
): KotlinNativeTarget {
    return when (RustineHost.Platform.current) {
        RustineHost.Platform.Windows -> mingwX64(name, configure)
        RustineHost.Platform.MacOS -> when (RustineHost.Arch.current) {
            RustineHost.Arch.X64 -> macosX64(name, configure)
            RustineHost.Arch.Arm64 -> macosArm64(name, configure)
        }

        RustineHost.Platform.Linux -> when (RustineHost.Arch.current) {
            RustineHost.Arch.X64 -> linuxX64(name, configure)
            RustineHost.Arch.Arm64 -> linuxArm64(name, configure)
        }
    }
}

fun KotlinNativeCompilation.useRustUpLinker() {
    compileTaskProvider.configure {
        @OptIn(InternalRustineGradleApi::class)
        PluginUtils.ensurePluginIsApplied(
            project,
            PluginUtils.PluginInfo(
                "Rust Kotlin Multiplatform",
                PluginIds.RUSTINE_RUST,
            ),
            PluginUtils.PluginInfo(
                "Cargo Kotlin Multiplatform",
                PluginIds.RUSTINE_CARGO,
            ),
        )
        val rustUpLinker = project.rustUpLinker(project.rustToolchainDirectory.get()).absolutePath
        val konanTarget = this@useRustUpLinker.target.konanTarget
        compilerOptions.freeCompilerArgs.add(
            "-Xoverride-konan-properties=linker.${RustineHost.current.konanName}-${konanTarget.name}=$rustUpLinker"
        )
        if (RustineHost.current.konanName == konanTarget.name) {
            compilerOptions.freeCompilerArgs.add(
                "-Xoverride-konan-properties=linker.${RustineHost.current.konanName}=$rustUpLinker"
            )
        }
    }
}

private val Project.rustToolchainDirectory: Provider<File>
    get() = project.providers.provider {
        project.extensions.findByType<RustExtension>()?.toolchainDirectory?.get()
            ?: RustineHost.current.platform.defaultToolchainDirectory
    }

@OptIn(InternalRustineGradleApi::class)
private fun Project.rustUpLinker(toolchainDirectory: File): File {
    val rustUpHome = command("rustup") {
        arguments("show", "home")
        additionalEnvironmentPath(toolchainDirectory)
        captureStandardOutput()
    }.get().apply {
        assertNormalExitValue()
    }.standardOutput!!.trim()

    val activeToolchains = command("rustup") {
        arguments("show", "active-toolchain")
        additionalEnvironmentPath(toolchainDirectory)
        captureStandardOutput()
    }.get().apply {
        assertNormalExitValue()
    }.standardOutput!!.trim().split('\n')

    val toolchain = activeToolchains.firstNotNullOf {
        it.trim().split(' ').getOrNull(0)?.takeUnless(String::isEmpty)
    }
    return RustineHost.current.platform.chooseExeExtension(
        File(rustUpHome).resolve("toolchains/$toolchain/lib/rustlib")
            .resolve(RustineHost.current.rustTarget.rustTriple).resolve("bin/gcc-ld/ld.lld")
    )
}

val Project.rustVersion: Provider<String>
    get() = rustToolchainDirectory.map { toolchainDirectory ->
        @OptIn(InternalRustineGradleApi::class)
        RustVersionUtils.getRustVersion(project, toolchainDirectory).toString()
    }
