import rustine.gradle.RustineHost
import rustine.gradle.cargo.dsl.android
import rustine.gradle.cargo.dsl.appleMobile
import rustine.gradle.rust.targets.RustAndroidTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("dev.lidory.rustine.cargo")
    id("dev.lidory.rustine.uniffi")
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

if (RustineHost.Platform.Windows.isCurrent) {
    afterEvaluate {
        cargo {
            // A workaround for #207
            builds.android {
                // Like the Rustine plugins, use the latest NDK installed in the SDK directory. The
                // NDK of Android Kotlin Multiplatform libraries can only be configured using the
                // `com.android.settings` plugin.
                val apiLevel = kotlin.android.minSdk ?: 21
                val envVariables = rustTarget.ndkEnvVariables(
                    sdkRoot = androidComponents.sdkComponents.sdkDirectory.get().asFile,
                    apiLevel = apiLevel,
                ).toMutableMap()
                val envVariableNamesToModify = arrayOf(
                    "ANDROID_HOME",
                    "ANDROID_NDK_HOME",
                    "ANDROID_NDK_ROOT",
                    "CC_${rustTarget.rustTriple}",
                    "CXX_${rustTarget.rustTriple}",
                    "AR_${rustTarget.rustTriple}",
                    "RANLIB_${rustTarget.rustTriple}",
                )
                for (envVariableNameToModify in envVariableNamesToModify) {
                    var envVariable = envVariables[envVariableNameToModify]!! as File
                    if (envVariableNameToModify.startsWith("CC_")) {
                        envVariable = envVariable.parentFile!!.resolve("clang.exe")
                    }
                    if (envVariableNameToModify.startsWith("CXX_")) {
                        envVariable = envVariable.parentFile!!.resolve("clang++.exe")
                    }
                    envVariables[envVariableNameToModify] = envVariable.path.replace('\\', '/')
                }
                // Calling clang directly loses the API level that the wrapper scripts of the NDK pass
                // in the target, and NDK r30 and later reject Android targets without one. cc-rs adds
                // the flags of these variables after its own `--target`, so clang uses this one.
                val clangTarget = "--target=${rustTarget.ndkLlvmTriple}$apiLevel"
                envVariables["CFLAGS_${rustTarget.rustTriple}"] = clangTarget
                envVariables["CXXFLAGS_${rustTarget.rustTriple}"] = clangTarget
                variants {
                    buildTaskProvider.configure {
                        additionalEnvironment.putAll(envVariables)
                    }
                    checkTaskProvider.configure {
                        additionalEnvironment.putAll(envVariables)
                    }
                }
            }
        }
    }
}
if (RustineHost.Platform.MacOS.isCurrent) {
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

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/rustine-uniffi-bindgen"))
    generateFromLibrary()
}

kotlin {
    // The Android application is in the androidApp subproject.
    android {
        namespace = "dev.lidory.rustine.uniffi.examples.tokioblake3app.kotlin"
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

    if (RustineHost.Platform.MacOS.isCurrent) {
        arrayOf(
            iosArm64(),
            iosSimulatorArm64(),
            iosX64(),
        ).forEach {
            it.binaries.framework {
                baseName = "TokioBlake3AppKotlin"
                isStatic = true
                binaryOption("bundleId", "dev.lidory.rustine.uniffi.examples.tokioblake3app.kotlin")
                binaryOption("bundleVersion", "0")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}