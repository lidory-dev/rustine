import rustine.gradle.cargo.dsl.wasm

plugins {
    kotlin("multiplatform")
    id("dev.lidory.rustine.cargo")
}

cargo {
    builds.wasm {
        variants {
            transformWasmProvider.configure {
                functionImportsFile = projectLayout.projectDirectory.file("function-imports.txt")
            }
        }
    }
    wasmTransformerFromPath(
        rootProject.layout.projectDirectory.dir("crates/rustine-wasm-transformer")
    )
}

kotlin {
    js {
        nodejs()
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}