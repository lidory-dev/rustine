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
        description = "A Gradle plugin for building Rust libraries and linking them to Kotlin projects.",
        gradlePlugin = true,
    )
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.kotlin.android))
    compileOnly(plugin(libs.plugins.kotlin.jvm))
    compileOnly(plugin(libs.plugins.kotlin.multiplatform))
    compileOnly(plugin(libs.plugins.android.application))
    compileOnly(plugin(libs.plugins.android.library))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.semver)
    compileOnly(libs.jna)

    api(project(":rustine-gradle"))
    api(project(":rustine-gradle-rust"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    vcsUrl.set("https://github.com/lidory-dev/rustine")
    website.set(vcsUrl)

    plugins {
        create("rustine-gradle-cargo") {
            id = "dev.lidory.rustine.cargo"
            displayName = "Rustine Cargo Gradle Plugin"
            implementationClass = "rustine.gradle.cargo.CargoPlugin"
            description = project.description
            tags.addAll("rust", "kotlin", "kotlin-multiplatform")
        }
    }
}
