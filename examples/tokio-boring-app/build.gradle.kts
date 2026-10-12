import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.cargo.dsl.appleMobile
import gobley.gradle.rust.targets.RustAndroidTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("dev.gobley.cargo")
    id("dev.gobley.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

cargo {
    builds.android {
        // The Android Kotlin Multiplatform library plugin has no ABI filters.
        embedRustLibrary = rustTarget == RustAndroidTarget.Arm64
    }
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/rustine-uniffi-bindgen"))
    generateFromLibrary()
}

if (GobleyHost.Platform.Windows.isCurrent) {
    cargo {
        builds.android {
            variants {
                // Windows CMake uses Visual Studio as the default generator.
                // Ensure Ninja is used as the CMake generator.
                buildTaskProvider.configure {
                    additionalEnvironment.put("CMAKE_GENERATOR", "Ninja")
                }
                checkTaskProvider.configure {
                    additionalEnvironment.put("CMAKE_GENERATOR", "Ninja")
                }
            }
        }
    }
}
if (GobleyHost.Platform.MacOS.isCurrent) {
    cargo {
        builds.appleMobile {
            if (rustTarget.cinteropName == "ios") {
                variants {
                    buildTaskProvider.configure {
                        additionalEnvironment.put("IPHONEOS_DEPLOYMENT_TARGET", "16.0.0")
                    }
                }
            }
        }
    }
}

kotlin {
    // The Android application is in the androidApp subproject.
    android {
        namespace = "dev.gobley.uniffi.examples.tokioboringapp.kotlin"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = 24

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    if (GobleyHost.Platform.MacOS.isCurrent) {
        arrayOf(
            iosArm64(),
            iosSimulatorArm64(),
            iosX64(),
        ).forEach {
            it.binaries.framework {
                baseName = "TokioBoringAppKotlin"
                isStatic = true
                binaryOption("bundleId", "dev.gobley.uniffi.examples.tokioboringapp.kotlin")
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