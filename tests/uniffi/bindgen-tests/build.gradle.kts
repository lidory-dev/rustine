plugins {
    id("uniffi-tests-from-library")
}

kotlin {
    // Upstream compiles the bindings and the tests with `kotlinc -Werror`.
    compilerOptions {
        allWarningsAsErrors = true
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":tests:uniffi:bindgen-tests:external-types"))
            }
        }
    }
}

tasks.withType<Test> {
    // The bindings of the external types load this library, which contains their crate.
    systemProperties["uniffi.component.bindgen_tests_external_types.libraryOverride"] =
        "rustine_fixture_bindgen_tests"
}
