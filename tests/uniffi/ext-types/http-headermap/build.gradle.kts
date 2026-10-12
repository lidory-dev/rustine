import rustine.gradle.rust.dsl.hostNativeTarget

plugins {
    id("uniffi-tests-from-library")
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
