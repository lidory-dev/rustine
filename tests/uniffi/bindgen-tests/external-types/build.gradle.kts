import rustine.gradle.rust.dsl.hostNativeTarget

plugins {
    id("uniffi-tests-from-library")
}

// The library of :tests:uniffi:bindgen-tests contains this crate, so don't link this one into
// Kotlin/Native binaries.
project.afterEvaluate {
    kotlin {
        hostNativeTarget {
            compilations.getByName("main") {
                cinterops.getByName("rust").definitionFile =
                    layout.projectDirectory.file("rust.def")
            }
        }
    }
}

kotlin {
    // Upstream compiles the bindings with `kotlinc -Werror`.
    compilerOptions {
        allWarningsAsErrors = true
    }
}

uniffi {
    generateFromLibrary {
        namespace = "bindgen_tests_external_types"
    }
}
