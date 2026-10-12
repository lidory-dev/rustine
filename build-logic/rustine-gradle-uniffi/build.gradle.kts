plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `kotlin-dsl`
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    id("rustine-gradle-build")
}

apiValidation {
    nonPublicMarkers += "rustine.gradle.InternalRustineGradleApi"
}

rustineGradleBuild {
    configureRustineGradleProject(
        description = "A Gradle plugin for generating UniFFI Kotlin Multiplatform bindings for Rust libraries.",
        gradlePlugin = true,
    )
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.kotlin.android))
    compileOnly(plugin(libs.plugins.kotlin.jvm))
    compileOnly(plugin(libs.plugins.kotlin.multiplatform))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tomlkt)

    api(project(":rustine-gradle"))
    api(project(":rustine-gradle-rust"))
    api(project(":rustine-gradle-cargo"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    vcsUrl.set("https://github.com/lidory-dev/rustine")
    website.set(vcsUrl)

    plugins {
        create("rustine-gradle-uniffi") {
            id = "dev.lidory.rustine.uniffi"
            displayName = "Rustine UniFFI Gradle Plugin"
            implementationClass = "rustine.gradle.uniffi.UniFfiPlugin"
            description = project.description
            tags.addAll("uniffi", "rust", "kotlin", "kotlin-multiplatform")
        }
    }
}
