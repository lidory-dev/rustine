import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

fun Project.propertyIsTrue(propertyName: String, default: Boolean = true): Boolean {
    if (!hasProperty(propertyName)) return default
    val propertyValue = findProperty(propertyName)?.toString()?.lowercase() ?: return default
    return propertyValue == "true" || propertyValue == "1"
}

yarn.apply {
    val gradleTestsEnabled = propertyIsTrue("rustine.projects.gradleTests")
    val uniffiTestsEnabled = propertyIsTrue("rustine.projects.uniffiTests")
    val examplesEnabled = propertyIsTrue("rustine.projects.examples")
    val postfix = when {
        gradleTestsEnabled && uniffiTestsEnabled && examplesEnabled -> ""
        !gradleTestsEnabled && !uniffiTestsEnabled && !examplesEnabled -> "-none"
        else -> StringBuilder().apply {
            if (gradleTestsEnabled) {
                append("-gradle")
            }
            if (uniffiTestsEnabled) {
                append("-uniffi")
            }
            if (examplesEnabled) {
                append("-examples")
            }
        }.toString()
    }
    lockFileName = "yarn${postfix}.lock"
    // Kotlin/Wasm uses a separate lock file in kotlin-js-store/wasm. Use the same name for it.
    WasmYarnRootExtension[project].lockFileName = lockFileName
}

// Kotlin 2.4 leaves the Kotlin/Wasm npm tooling directory empty when the yarn install of
// kotlinWasmToolingSetup has to wait for another yarn instance, so the Wasm tests can't find
// kotlin-test-karma-runner.js (https://slack-chats.kotlinlang.org/t/34065440). Run the yarn installs
// one after another instead.
val yarnInstallTaskNames = listOf("kotlinNpmInstall", "kotlinWasmNpmInstall", "kotlinWasmToolingSetup")
yarnInstallTaskNames.forEachIndexed { index, taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        mustRunAfter(tasks.matching { it.name in yarnInstallTaskNames.subList(0, index) })
    }
}

// The cargoCheck<Target>Release tasks only run `cargo check` with the release profile, and nothing
// consumes their outputs. CI disables them in the jobs that only test the debug builds, where they
// would cost minutes per project and target.
if (propertyIsTrue("rustine.projects.skipReleaseCargoChecks", default = false)) {
    allprojects {
        tasks.matching { it.name.startsWith("cargoCheck") && it.name.endsWith("Release") }.configureEach {
            enabled = false
        }
    }
}
