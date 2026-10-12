import rustine.gradle.InternalRustineGradleApi
import org.gradle.api.Project

plugins {
    id("uniffi-tests")
}

fun Project.propertyIsTrue(propertyName: String, default: Boolean = true): Boolean {
    if (!hasProperty(propertyName)) return default
    val propertyValue = findProperty(propertyName)?.toString()?.lowercase() ?: return default
    return propertyValue == "true" || propertyValue == "1"
}

uniffi {
    generateFromLibrary {
        namespace = name.replace('-', '_')
        generateImmutableRecords =
            propertyIsTrue("rustine.projects.uniffiTests.generateImmutableRecords", false)
        omitChecksums =
            propertyIsTrue("rustine.projects.uniffiTests.omitChecksums", false)
        @OptIn(InternalRustineGradleApi::class)
        enableJnaInterfaceMapping =
            propertyIsTrue("rustine.projects.uniffiTests.enableJnaInterfaceMapping", false)
    }
}
