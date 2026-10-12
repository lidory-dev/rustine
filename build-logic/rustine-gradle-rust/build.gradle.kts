plugins {
    alias(libs.plugins.kotlin.jvm)
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
        description = "A Gradle plugin for configuring Rust toolchain and linking Rust libraries to Kotlin projects.",
        gradlePlugin = true,
    )
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.kotlin.multiplatform))

    implementation(libs.semver)

    api(project(":rustine-gradle"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    vcsUrl.set("https://github.com/lidory-dev/rustine")
    website.set(vcsUrl)

    plugins {
        create("rustine-gradle-rust") {
            id = "dev.lidory.rustine.rust"
            displayName = "Rustine Rust Gradle Plugin"
            implementationClass = "rustine.gradle.rust.RustPlugin"
            description = project.description
            tags.addAll("rust", "kotlin", "kotlin-multiplatform")
        }
    }
}
