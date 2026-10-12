import rustine.gradle.RustineHost
import rustine.gradle.cargo.dsl.android
import rustine.gradle.rust.targets.RustAndroidTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("dev.lidory.rustine.rust")
    id("dev.lidory.rustine.cargo")
    id("dev.lidory.rustine.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

cargo {
    builds.android {
        dynamicLibraries.addAll("aaudio", "c++_shared")
        // The Android Kotlin Multiplatform library plugin has no ABI filters.
        embedRustLibrary = rustTarget == RustAndroidTarget.Arm64
    }
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/rustine-uniffi-bindgen"))
    generateFromLibrary()
}

kotlin {
    // The Android application is in the androidApp subproject.
    android {
        namespace = "dev.gobley.uniffi.examples.audiocppapp.kotlin"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = 26

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    if (RustineHost.Platform.MacOS.isCurrent) {
        arrayOf(
            iosArm64(),
            iosSimulatorArm64(),
            iosX64(),
        ).forEach {
            it.binaries.framework {
                baseName = "AudioCppAppKotlin"
                isStatic = true
                binaryOption("bundleId", "dev.gobley.uniffi.examples.audiocppapp.kotlin")
                binaryOption("bundleVersion", "0")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}