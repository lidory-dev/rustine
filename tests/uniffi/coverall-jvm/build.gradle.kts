import rustine.gradle.RustineHost
import rustine.gradle.cargo.dsl.jvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.lidory.rustine.cargo")
    id("dev.lidory.rustine.uniffi")
    alias(libs.plugins.kotlin.atomicfu)
}

cargo {
    builds.jvm {
        embedRustLibrary = rustTarget == RustineHost.current.rustTarget
    }
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/rustine-uniffi-bindgen"))
    generateFromLibrary {
        namespace = name.replace('-', '_')
        packageName = "coverall"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    sourceSets {
        test {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}
