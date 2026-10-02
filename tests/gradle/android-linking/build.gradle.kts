import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.android
import gobley.gradle.rust.targets.RustAndroidTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("dev.gobley.cargo")
    alias(libs.plugins.android.library)
}

// Build a library manually to test passing an absolute path to `dynamicLibraries` works well
val anotherCustomCppLibraryRoot: Directory =
    project.layout.projectDirectory.dir("another-android-linking-cpp")
val androidTargets = RustAndroidTarget.values()
val anotherCustomCppLibraryCmakeOutputDirectories = androidTargets.associateWith {
    project.layout.buildDirectory.dir("intermediates/ninja/project/debug/${it.androidAbiName}")
        .get()
}
val anotherCustomCppLibraryLocations = anotherCustomCppLibraryCmakeOutputDirectories.mapValues {
    it.value.file("libanother-android-linking-cpp.so")
}
// The SDK and NDK directories are resolved when the tasks below are configured, so the build can
// be configured even when CMake or the NDK is not installed.
val androidSdkCMakeDirectory = androidComponents.sdkComponents.sdkDirectory.map { sdkDirectory ->
    sdkDirectory.asFile
        .resolve("cmake")
        .listFiles()
        ?.firstOrNull { file -> file.name.startsWith("3.") }
        ?.resolve("bin") ?: error("CMake is not installed in Android SDK")
}
val androidSdkCMake = androidSdkCMakeDirectory.map {
    it.resolve(GobleyHost.Platform.current.convertExeName("cmake"))
}
val androidSdkNinja = androidSdkCMakeDirectory.map {
    it.resolve(GobleyHost.Platform.current.convertExeName("ninja"))
}
val androidNdkDirectory = androidComponents.sdkComponents.ndkDirectory.map { it.asFile }
val anotherCustomCppLibraryBuildTasks = androidTargets.associateWith {
    val cmakeOutputDirectory = anotherCustomCppLibraryCmakeOutputDirectories[it]!!
    val libraryLocation = anotherCustomCppLibraryLocations[it]!!
    val configureTask = tasks.register<Exec>("configureCustomCppLibraryCMake${it.friendlyName}") {
        commandLine(
            androidSdkCMake.get(),
            "-H$anotherCustomCppLibraryRoot",
            "-B$cmakeOutputDirectory",
            "-DANDROID_ABI=${it.androidAbiName}",
            "-DANDROID_PLATFORM=29",
            "-DANDROID_NDK=${androidNdkDirectory.get()}",
            "-DCMAKE_TOOLCHAIN_FILE=${androidNdkDirectory.get()}/build/cmake/android.toolchain.cmake",
            "-DCMAKE_MAKE_PROGRAM=${androidSdkNinja.get()}",
            "-G",
            "Ninja",
        )

        inputs.dir(anotherCustomCppLibraryRoot)
        outputs.dir(cmakeOutputDirectory)
    }

    tasks.register<Exec>("buildCustomCppLibrary${it.friendlyName}") {
        commandLine(
            androidSdkCMake.get(),
            "--build",
            "$cmakeOutputDirectory"
        )
        dependsOn(configureTask)

        inputs.dir(cmakeOutputDirectory)
        outputs.file(libraryLocation)
    }
}

cargo {
    builds.android {
        val anotherCustomCppLibraryBuildTask = anotherCustomCppLibraryBuildTasks[rustTarget]!!
        val libraryLocation = anotherCustomCppLibraryLocations[rustTarget]!!
        dynamicLibraries.addAll("c++_shared", libraryLocation.asFile.absolutePath)
        variants {
            findDynamicLibrariesTaskProvider.configure {
                dependsOn(anotherCustomCppLibraryBuildTask)
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

android {
    namespace = "dev.gobley.uniffi.tests.gradle.androidlinking"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters.add("arm64-v8a")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = File("CMakeLists.txt")
        }
    }
}
