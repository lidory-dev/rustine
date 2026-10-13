pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "rustine"

fun ExtraPropertiesExtension.propertyIsTrue(propertyName: String, default: Boolean = true): Boolean {
    if (!has(propertyName)) return default
    val propertyValue = this[propertyName]?.toString()?.lowercase() ?: return default
    return propertyValue == "true" || propertyValue == "1"
}

if (ext.propertyIsTrue("rustine.projects.gradleTests")) {
    include(":tests:gradle:android-application")
    include(":tests:gradle:android-application-plain-dep")
    include(":tests:gradle:android-linking")
    include(":tests:gradle:cargo-only")
    include(":tests:gradle:js-only")
    include(":tests:gradle:jvm-only")
}

if (ext.propertyIsTrue("rustine.projects.uniffiTests")) {
    include(":tests:uniffi:callbacks")
    include(":tests:uniffi:chronological")
    include(":tests:uniffi:coverall")
    include(":tests:uniffi:coverall-android")
    include(":tests:uniffi:coverall-jvm")
    include(":tests:uniffi:coverall-pure-kotlin-dep")
    include(":tests:uniffi:docstring")
    include(":tests:uniffi:docstring-proc-macro")
    include(":tests:uniffi:dynamic-library-dependencies")
    include(":tests:uniffi:enum-payload-clash")
    include(":tests:uniffi:enum-types")
    include(":tests:uniffi:error-types")
    include(":tests:uniffi:keywords")
    include(":tests:uniffi:large-enum")
    include(":tests:uniffi:large-error")
    include(":tests:uniffi:proc-macro")
    include(":tests:uniffi:simple-fns")
    include(":tests:uniffi:simple-iface")
    include(":tests:uniffi:struct-default-values")
    include(":tests:uniffi:trait-methods")
    include(":tests:uniffi:type-limits")
}
// Run :tests:uniffi:ext-types separately
if (ext.propertyIsTrue("rustine.projects.uniffiTests.extTypes")) {
    // Required by ext-types and ext-types-proc-macro
    include(":examples:custom-types")
    include(":tests:uniffi:ext-types:custom-types")
    include(":tests:uniffi:ext-types:ext-types")
    include(":tests:uniffi:ext-types:ext-types-proc-macro")
    include(":tests:uniffi:ext-types:http-headermap")
    include(":tests:uniffi:ext-types:sub-lib")
    include(":tests:uniffi:ext-types:uniffi-one")
}
// Run :tests:uniffi:futures separately
if (ext.propertyIsTrue("rustine.projects.uniffiTests.futures")) {
    include(":tests:uniffi:futures")
}

if (ext.propertyIsTrue("rustine.projects.examples.app")) {
    include(":examples:app")
    include(":examples:app:androidApp")
    include(":examples:arithmetic-procmacro")
    include(":examples:todolist")
}
if (ext.propertyIsTrue("rustine.projects.examples.audioCppApp")) {
    include(":examples:audio-cpp-app")
    include(":examples:audio-cpp-app:androidApp")
}
if (ext.propertyIsTrue("rustine.projects.examples.customTypes")) {
    include(":examples:custom-types")
}
if (ext.propertyIsTrue("rustine.projects.examples.tokioBlake3App")) {
    include(":examples:tokio-blake3-app")
    include(":examples:tokio-blake3-app:androidApp")
}
if (ext.propertyIsTrue("rustine.projects.examples.tokioBoringApp")) {
    include(":examples:tokio-boring-app")
    include(":examples:tokio-boring-app:androidApp")
}
