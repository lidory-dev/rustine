/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.utils

import rustine.gradle.DependencyVersions
import rustine.gradle.RustineHost
import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.Variant
import rustine.gradle.rust.targets.RustJvmTarget
import rustine.gradle.rust.targets.RustTarget
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.io.File
import java.util.Locale

@Suppress("UnstableApiUsage")
@InternalRustineGradleApi
object DependencyUtils {
    private fun configureEachCommonProjectDependencies(
        configurations: ConfigurationContainer,
        action: (ProjectDependency) -> Unit,
    ) {
        configureEachCommonDependencies(configurations) { dependency ->
            if (dependency is ProjectDependency) {
                action(dependency)
            }
        }
    }

    private fun addDependencyEachCommonProjectDependencies(
        currentProject: Project,
        configurationName: String
    ) {
        configureEachCommonProjectDependencies(currentProject.configurations) { dependency ->
            currentProject.dependencies.add(
                configurationName,
                currentProject.project(dependency.path),
            )
        }
    }

    private val kindAttribute = Attribute.of("dev.lidory.rustine.kind", String::class.java)
    private const val KIND_RUST = "RUST"
    private const val KIND_UNIFFI = "UNIFFI"

    private val rustTargetAttribute = Attribute.of("dev.lidory.rustine.rust.target", String::class.java)
    private val rustVariantAttribute = Attribute.of("dev.lidory.rustine.rust.variant", String::class.java)

    private fun Configuration.addRustAttributes(
        superConfiguration: Configuration? = null,
        rustTarget: RustTarget,
        variant: Variant,
    ) {
        if (superConfiguration != null) {
            extendsFrom(superConfiguration)
        }
        attributes.attribute(kindAttribute, KIND_RUST)
        attributes.attribute(rustTargetAttribute, rustTarget.friendlyName)
        attributes.attribute(rustVariantAttribute, variant.toString())
    }

    fun createCargoConfigurations(currentProject: Project) {
        val rustRuntimeOnlyConfiguration =
            currentProject.configurations.dependencyScope("rustRuntimeOnly")
        addDependencyEachCommonProjectDependencies(currentProject, "rustRuntimeOnly")

        for (rustTarget in RustineHost.current.platform.supportedTargets) {
            if (rustTarget !is RustJvmTarget) {
                continue
            }
            for (variant in Variant.entries) {
                currentProject.configurations.resolvable(
                    androidUnitTestRuntimeRustLibraryConfigurationName(
                        rustTarget, variant
                    )
                ) { configuration ->
                    configuration.addRustAttributes(
                        superConfiguration = rustRuntimeOnlyConfiguration.get(),
                        rustTarget = rustTarget,
                        variant = variant,
                    )
                }
                currentProject.configurations.consumable(
                    androidUnitTestConsumableRuntimeRustLibraryConfigurationName(
                        rustTarget, variant
                    )
                ) { configuration ->
                    configuration.addRustAttributes(
                        superConfiguration = rustRuntimeOnlyConfiguration.get(),
                        rustTarget = rustTarget,
                        variant = variant,
                    )
                }
            }
        }
    }

    fun resolveCargoDependencies(currentProject: Project) {
        for (rustTarget in RustineHost.current.platform.supportedTargets) {
            if (rustTarget !is RustJvmTarget) {
                continue
            }
            for (variant in Variant.entries) {
                val androidUnitTestConfiguration = currentProject.configurations.findByName(
                    androidUnitTestRuntimeRustLibraryConfigurationName(
                        rustTarget, variant
                    )
                ) ?: continue
                registerAndroidUnitTestLibraryToClassPaths(
                    currentProject,
                    androidUnitTestConfiguration,
                )
            }
        }
    }

    private fun registerAndroidUnitTestLibraryToClassPaths(
        currentProject: Project,
        configuration: Configuration,
    ) {
        val variant = Variant(configuration.attributes.getAttribute(rustVariantAttribute)!!)
        val dependencyJars = currentProject.files(configuration.resolvedArtifactFiles(KIND_RUST))
        val composePreviewVariant = GradleUtils.getComposePreviewVariant(currentProject.gradle)
        PluginUtils.withKotlinPlugin(currentProject) { delegate ->
            if (delegate.androidTarget != null) {
                if (variant == composePreviewVariant) {
                    delegate.sourceSets.androidMainConfigurations(variant)?.let { configurations ->
                        addDependency(currentProject, configurations.runtimeOnly, dependencyJars)
                    }
                }
                for (configurations in delegate.sourceSets.androidUnitTestConfigurations(variant)) {
                    addDependency(currentProject, configurations.runtimeOnly, dependencyJars)
                }
            }
        }
    }

    fun addAndroidUnitTestRuntimeRustLibraryJar(
        currentProject: Project,
        rustTarget: RustTarget,
        variant: Variant,
        jarTaskProvider: Provider<Jar>
    ) {
        val configurationName = androidUnitTestConsumableRuntimeRustLibraryConfigurationName(
            rustTarget, variant
        )
        currentProject.artifacts.add(configurationName, jarTaskProvider)
    }

    private fun androidUnitTestRuntimeRustLibraryConfigurationName(
        rustTarget: RustTarget,
        variant: Variant,
    ): String {
        return StringBuilder().apply {
            append(rustTarget.friendlyName.replaceFirstChar { it.lowercase(Locale.US) })
            append("RustRuntimeAndroidUnitTest")
            append(variant.toString().uppercaseFirstChar())
        }.toString()
    }

    private fun androidUnitTestConsumableRuntimeRustLibraryConfigurationName(
        rustTarget: RustTarget,
        variant: Variant,
    ): String {
        return StringBuilder().apply {
            append(rustTarget.friendlyName.replaceFirstChar { it.lowercase(Locale.US) })
            append("RustRuntimeAndroidUnitTestConsumable")
            append(variant.toString().uppercaseFirstChar())
        }.toString()
    }

    private fun Configuration.addUniffiAttributes(superConfiguration: Configuration) {
        extendsFrom(superConfiguration)
        attributes.attribute(kindAttribute, KIND_UNIFFI)
    }

    fun createUniFfiConfigurations(currentProject: Project) {
        val uniFfiImplementationConfiguration =
            currentProject.configurations.dependencyScope("uniFfiImplementation")
        addDependencyEachCommonProjectDependencies(currentProject, "uniFfiImplementation")

        currentProject.configurations.resolvable("uniFfiConfiguration") { configuration ->
            configuration.addUniffiAttributes(
                superConfiguration = uniFfiImplementationConfiguration.get(),
            )
        }
        currentProject.configurations.consumable("uniFfiConfigurationConsumable") { configuration ->
            configuration.addUniffiAttributes(
                superConfiguration = uniFfiImplementationConfiguration.get(),
            )
        }
    }

    fun addMergedUniffiConfigArtifact(
        currentProject: Project,
        uniFfiConfigTask: TaskProvider<*>,
    ) {
        currentProject.artifacts.add("uniFfiConfigurationConsumable", uniFfiConfigTask)
    }

    fun getExternalPackageUniFfiConfigurations(currentProject: Project): Provider<List<File>>? {
        val configuration = currentProject.configurations.findByName("uniFfiConfiguration")
            ?: return null
        return configuration.resolvedArtifactFiles(KIND_UNIFFI)
    }

    // Every project dependency is added to the Rustine configurations, including projects without
    // Rustine plugins. As the variants of such projects don't have the Rustine attributes, Gradle may
    // fail to choose one of them, e.g., between the variants of different build types of plain
    // Android libraries. Resolve leniently to skip such projects, and keep the Rustine artifacts.
    private fun Configuration.resolvedArtifactFiles(kind: String): Provider<List<File>> {
        val artifacts = incoming.artifactView { it.lenient(true) }.artifacts
        return artifacts.resolvedArtifacts.map { resolvedArtifacts ->
            resolvedArtifacts.mapNotNull { artifact ->
                artifact.file.takeIf {
                    artifact.variant.attributes.getAttribute(kindAttribute) == kind
                }
            }
        }
    }

    fun resolveUniFfiDependencies(currentProject: Project) {
        val composePreviewVariant = GradleUtils.getComposePreviewVariant(currentProject.gradle)
        PluginUtils.withKotlinPlugin(currentProject) { delegate ->
            if (delegate.androidTarget != null) {
                if (composePreviewVariant != null) {
                    delegate.sourceSets.androidMainConfigurations(composePreviewVariant)?.let { configurations ->
                        addDependency(
                            currentProject,
                            configurations.runtimeOnly,
                            "net.java.dev.jna:jna",
                            preferredVersion = DependencyVersions.JNA,
                        )
                    }
                }
                for (configurations in delegate.sourceSets.androidUnitTestConfigurations()) {
                    addDependency(
                        currentProject,
                        configurations.runtimeOnly,
                        "net.java.dev.jna:jna",
                        preferredVersion = DependencyVersions.JNA,
                    )
                }
            }
        }
    }

    /**
     * Adds a dependency to the configuration named [configurationName] when the configuration
     * exists or once it is created. Dependencies are added this way when the configuration may not
     * exist, for example, configurations of Android build types or Android local unit tests.
     *
     * When [preferredVersion] is not `null`, [dependencyNotation] must denote an external module.
     */
    fun addDependency(
        currentProject: Project,
        configurationName: String,
        dependencyNotation: Any,
        preferredVersion: String? = null,
    ) {
        currentProject.configurations
            .matching { it.name == configurationName }
            .configureEach { configuration ->
                val dependency = currentProject.dependencies.create(dependencyNotation)
                if (preferredVersion != null) {
                    (dependency as ExternalModuleDependency).version {
                        it.prefer(preferredVersion)
                    }
                }
                configuration.dependencies.add(dependency)
            }
    }

    // Single-target projects, which are Kotlin/JVM projects and Android projects using the built-in
    // Kotlin support of the Android Gradle Plugin, declare dependencies in the configurations
    // without the source set prefix.
    private val commonDependencyConfigurationNames = setOf(
        "commonMainApi",
        "commonMainImplementation",
        "commonMainCompileOnly",
        "api",
        "implementation",
        "compileOnly",
    )

    fun configureEachCommonDependencies(
        configurations: ConfigurationContainer,
        action: (Dependency) -> Unit,
    ) {
        configurations.configureEach { configuration ->
            if (configuration.name in commonDependencyConfigurationNames) {
                configuration.dependencies.configureEach(action)
            }
        }
    }
}