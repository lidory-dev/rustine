plugins {
    id("uniffi-tests-from-library")
}

uniffi {
    generateFromLibrary {
        // Rustine: set in Gradle to test the DSL.
        mutableRecords.add("DslMutableRecord")
    }
}
