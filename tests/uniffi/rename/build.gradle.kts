plugins {
    id("uniffi-tests-from-library")
}

uniffi {
    generateFromLibrary {
        // Rustine: these entries are set in Gradle to test the DSL. Upstream sets them in
        // uniffi.toml.
        rename.put("BindingObject.method.arg", "kotlinArg")
        exclude.add("RecordToExclude")
    }
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.http)
            }
        }
    }
}
