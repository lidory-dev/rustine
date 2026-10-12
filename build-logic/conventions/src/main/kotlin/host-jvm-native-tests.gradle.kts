import rustine.gradle.RustineHost
import rustine.gradle.rust.dsl.hostNativeTarget
import rustine.gradle.rust.dsl.useRustUpLinker
import org.gradle.accessors.dm.*

plugins {
    id("dev.lidory.rustine.rust")
    kotlin("multiplatform")
    kotlin("plugin.atomicfu")
}

// https://github.com/gradle/gradle/issues/15383
apply<VersionCatalogPlugin>()
val libs = extensions.getByName("libs") as LibrariesForLibs

fun Project.propertyIsTrue(propertyName: String, default: Boolean = true): Boolean {
    if (!hasProperty(propertyName)) return default
    val propertyValue = findProperty(propertyName)?.toString()?.lowercase() ?: return default
    return propertyValue == "true" || propertyValue == "1"
}

kotlin {
    if (propertyIsTrue("rustine.projects.jvmTests")) {
        jvmToolchain(17)
        jvm()
        // Gradle 9 no longer provides the JUnit Platform launcher implicitly.
        sourceSets.getByName("jvmTest").dependencies {
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
    if (propertyIsTrue("rustine.projects.nativeTests")) {
        hostNativeTarget {
            if (RustineHost.Platform.Windows.isCurrent) {
                compilations.getByName("test") {
                    useRustUpLinker()
                }
            }
        }
    }
    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
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

tasks.withType<Test> {
    useJUnitPlatform()
    reports {
        junitXml.required.set(true)
    }
}
