plugins {
    `kotlin-dsl`
    id("rustine-gradle-build")
}

dependencies {
    implementation(plugin(libs.plugins.kotlin.multiplatform))
    implementation(plugin(libs.plugins.kotlin.atomicfu))

    implementation(project(":rustine-gradle-uniffi"))

    // https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
