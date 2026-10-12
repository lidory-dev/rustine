plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `kotlin-dsl`
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.kotlin.jvm))
    compileOnly(plugin(libs.plugins.vanniktech.maven.publish))
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.tomlkt)
}

kotlin {
    jvmToolchain(17)
}

// Copied from KotlinDslExtensions.kt
fun DependencyHandler.plugin(dependency: Provider<PluginDependency>): Dependency =
    dependency.get().run { create("$pluginId:$pluginId.gradle.plugin:$version") }

gradlePlugin {
    plugins.create("rustine-gradle-build") {
        id = "rustine-gradle-build"
        implementationClass = "rustine.gradle.build.RustineGradleBuildPlugin"
    }
}