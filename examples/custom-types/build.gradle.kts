import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.rust.dsl.useRustUpLinker
import gobley.gradle.rust.targets.RustAndroidTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.rust")
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
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/rustine-uniffi-bindgen"))
    generateFromLibrary {
        packageName = "gobley.uniffi.examples.customtypes"
        customType("Url") {
            typeName = "io.ktor.http.Url"
            imports.add("io.ktor.http.Url")
            lift = "Url({})"
            lower = "{}.toString()"
        }
    }
}

kotlin {
    android {
        namespace = "dev.gobley.uniffi.examples.customtypes"
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
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.http)
            }
        }
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