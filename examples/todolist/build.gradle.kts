import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.cargo.dsl.appleMobile
import gobley.gradle.rust.dsl.rustVersion
import gobley.gradle.rust.dsl.useRustUpLinker
import gobley.gradle.rust.targets.RustAndroidTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

cargo {
    builds.android {
        // The Android Kotlin Multiplatform library plugin has no ABI filters.
        embedRustLibrary = rustTarget == RustAndroidTarget.Arm64
    }
    builds.appleMobile {
        variants {
            if (rustTarget.tier(project.rustVersion.get()) >= 3) {
                buildTaskProvider.configure {
                    nightly = true
                    extraArguments.add("-Zbuild-std")
                }
                checkTaskProvider.configure {
                    nightly = true
                    extraArguments.add("-Zbuild-std")
                }
            }
        }
    }
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/gobley-uniffi-bindgen"))
    generateFromUdl {
        udlFile = layout.projectDirectory.file("src/todolist.udl")
        namespace = "todolist"
    }
}

kotlin {
    android {
        namespace = "dev.gobley.uniffi.examples.todolist"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = 21

        // Run commonTest as Android host tests.
        withHostTest {}

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    jvmToolchain(17)
    jvm("desktop")
    arrayOf(
        mingwX64(),
    ).forEach { nativeTarget ->
        nativeTarget.compilations.getByName("test") {
            useRustUpLinker()
        }
    }

    androidNativeArm64()
    androidNativeArm32()
    androidNativeX64()
    androidNativeX86()
    linuxX64()
    linuxArm64()
    if (GobleyHost.Platform.MacOS.isCurrent) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
        macosArm64()
        macosX64()
        tvosArm64()
        tvosSimulatorArm64()
        tvosX64()
        watchosSimulatorArm64()
        watchosDeviceArm64()
        watchosX64()
        watchosArm64()
        watchosArm32()
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}