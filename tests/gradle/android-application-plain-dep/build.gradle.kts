// Tests whether Android projects with Rustine plugins can depend on plain Android library projects
// without Rustine plugins. Such projects have variants of multiple build types, which Rustine must
// not fail to choose from.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

android {
    namespace = "dev.lidory.rustine.uniffi.tests.gradle.androidapplication.plaindep"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
