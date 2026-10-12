import rustine.gradle.rust.dsl.hostNativeTarget

plugins {
    id("uniffi-tests")
}

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

uniffi {
    generateFromLibrary {
        namespace = "kmm_uniffi_one"
    }
}
