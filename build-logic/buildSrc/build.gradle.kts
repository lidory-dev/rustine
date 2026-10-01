plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    compileOnly(gradleKotlinDsl())
}

kotlin {
    jvmToolchain(17)
}
