/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.uniffi

import gobley.gradle.DependencyVersions
import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.PluginIds
import gobley.gradle.Variant
import gobley.gradle.android.GobleyAndroidExtensionDelegate
import gobley.gradle.android.GobleyAndroidPluginKind
import gobley.gradle.cargo.dsl.CargoAndroidBuild
import gobley.gradle.cargo.dsl.CargoBuild
import gobley.gradle.cargo.dsl.CargoExtension
import gobley.gradle.cargo.dsl.CargoJvmBuild
import gobley.gradle.cargo.dsl.CargoNativeBuild
import gobley.gradle.cargo.dsl.HasAndroidVariant
import gobley.gradle.kotlin.GobleyKotlinExtensionDelegate
import gobley.gradle.rust.CrateType
import gobley.gradle.rust.targets.RustTarget
import gobley.gradle.rust.targets.RustWasmTarget
import gobley.gradle.uniffi.dsl.BindingsGeneration
import gobley.gradle.uniffi.dsl.BindingsGenerationFromLibrary
import gobley.gradle.uniffi.dsl.BindingsGenerationFromUdl
import gobley.gradle.uniffi.dsl.UniFfiExtension
import gobley.gradle.uniffi.tasks.BuildUniffiBindingsTask
import gobley.gradle.uniffi.tasks.GenerateUniffiProguardRulesTask
import gobley.gradle.uniffi.tasks.InstallUniffiBindgenTask
import gobley.gradle.uniffi.tasks.MergeUniffiConfigTask
import gobley.gradle.uniffi.tasks.SyncUniffiAndroidBindingsTask
import gobley.gradle.utils.DependencyUtils
import gobley.gradle.utils.GradleUtils
import gobley.gradle.utils.PluginUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private const val TASK_GROUP = "uniffi"

class UniFfiPlugin : Plugin<Project> {
    private lateinit var uniFfiExtension: UniFfiExtension
    private lateinit var bindingsGeneration: BindingsGeneration
    private lateinit var cargoExtension: CargoExtension

    @OptIn(InternalGobleyGradleApi::class)
    private lateinit var kotlinExtensionDelegate: GobleyKotlinExtensionDelegate

    @OptIn(InternalGobleyGradleApi::class)
    private lateinit var androidDelegate: GobleyAndroidExtensionDelegate

    @OptIn(InternalGobleyGradleApi::class)
    private var syncAndroidBindingsTask: TaskProvider<SyncUniffiAndroidBindingsTask>? = null

    override fun apply(target: Project) {
        @OptIn(InternalGobleyGradleApi::class)
        if (!target.plugins.hasPlugin(PluginIds.GOBLEY_RUST)) {
            DependencyUtils.createUniFfiConfigurations(target)
        }
        uniFfiExtension = target.extensions.create<UniFfiExtension>(TASK_GROUP, target)
        target.watchPluginChanges()
        target.afterEvaluate {
            applyAfterEvaluate(this)
        }
    }

    // Android Gradle Plugin callbacks must be registered here, as the Android Gradle Plugin may be
    // applied before this plugin, in which case its afterEvaluate callback runs before ours.
    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.watchPluginChanges() {
        PluginUtils.withKotlinPlugin(this) { delegate ->
            kotlinExtensionDelegate = delegate
        }
        PluginUtils.withAndroidPlugin(this) { delegate ->
            androidDelegate = delegate
            configureAndroidProguardRules(delegate)
            if (delegate.kind != GobleyAndroidPluginKind.KotlinMultiplatformLibrary) {
                configureAndroidBindingsSources(delegate)
            } else {
                configureKotlinMultiplatformAndroidBindingsSources(delegate)
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureAndroidProguardRules(delegate: GobleyAndroidExtensionDelegate) {
        val generateUniffiProguardRulesTask =
            tasks.register<GenerateUniffiProguardRulesTask>("generateUniffiProguardRules") {
                outputFile.set(androidGeneratedProguardFile)
            }
        delegate.addProguardFile(
            generateUniffiProguardRulesTask.flatMap { it.outputFile },
            uniFfiExtension.generateProguardRules,
        )
    }

    // With the built-in Kotlin support of the Android Gradle Plugin, Kotlin source sets must not
    // be used to add sources. Copy the bindings to a directory owned by a separate task and add it
    // to each Android variant using the Variant API. The bindings task is referred by its name, as
    // it is registered in afterEvaluate, which may run after the Android Gradle Plugin invokes
    // the onVariants callback.
    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureAndroidBindingsSources(delegate: GobleyAndroidExtensionDelegate) {
        val syncTask = tasks.register<SyncUniffiAndroidBindingsTask>("syncUniffiAndroidBindings") {
            group = TASK_GROUP
            bindingsDirectory.set(mainBindingsDirectory)
            dependsOn("buildUniffiBindings")
        }
        syncAndroidBindingsTask = syncTask
        delegate.onVariants { androidVariant ->
            androidVariant.addGeneratedKotlinDirectory(syncTask) { it.outputDirectory }
        }
    }

    // In Kotlin Multiplatform projects, the bindings are added to Kotlin source sets in
    // configureKotlin. The `com.android.kotlin.multiplatform.library` plugin reads the Kotlin
    // source directories of the Android compilation without their task dependencies, and also
    // looks for other sources like baseline profiles and keep rules next to each Kotlin source
    // directory, i.e., inside the output directory of the bindings task. Make the tasks of the
    // Android variant run after the bindings task, which is referred by its name as in
    // configureAndroidBindingsSources.
    @OptIn(InternalGobleyGradleApi::class)
    private fun configureKotlinMultiplatformAndroidBindingsSources(
        delegate: GobleyAndroidExtensionDelegate,
    ) {
        delegate.onVariants { androidVariant ->
            androidVariant.registerPreBuildDependencies("buildUniffiBindings")
        }
    }

    private fun applyAfterEvaluate(target: Project): Unit = with(target) {
        findRequiredExtensions()
        checkKotlinTargets()
        configureBindingTasks()
        configureKotlin()
        configureCleanTasks()

        @OptIn(InternalGobleyGradleApi::class)
        DependencyUtils.resolveUniFfiDependencies(target)
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.findRequiredExtensions() {
        bindingsGeneration = uniFfiExtension.bindingsGeneration.get()

        PluginUtils.ensureKotlinPluginIsApplied(this, ::kotlinExtensionDelegate.isInitialized)
        PluginUtils.ensurePluginIsApplied(project, "Kotlin AtomicFU", PluginIds.KOTLIN_ATOMIC_FU)
        PluginUtils.ensurePluginIsApplied(
            project,
            "Cargo Kotlin Multiplatform",
            PluginIds.GOBLEY_CARGO
        )

        // Since the Cargo Kotlin Multiplatform plugin is present, `CargoExtension` must be present.
        cargoExtension = extensions.getByType()

        bindingsGeneration.namespace.convention(cargoExtension.cargoPackage.map { it.libraryCrateName })
        (bindingsGeneration as? BindingsGenerationFromUdl)?.udlFile?.convention(
            cargoExtension.cargoPackage.map {
                it.root.file("src/${it.libraryCrateName}.udl")
            }
        )
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.checkKotlinTargets() {
        val hasJsTargets =
            kotlinExtensionDelegate.targets.any { it.platformType == KotlinPlatformType.js }
        if (hasJsTargets) {
            project.logger.warn("JS targets are added, but the UniFFI plugin does not support JS targets yet.")
        }

        val hasWasmTargets =
            kotlinExtensionDelegate.targets.any { it.platformType == KotlinPlatformType.wasm }
        if (hasWasmTargets) {
            project.logger.warn("WASM targets are added, but the UniFFI plugin does not support WASM targets yet.")
        }
    }

    private fun Project.configureBindingTasks() {
        val bindingsGeneration = bindingsGeneration

        val buildRustTarget = bindingsGeneration.build.orNull ?: run {
            @OptIn(InternalGobleyGradleApi::class)
            val androidTargetsToBuild = cargoExtension.androidTargetsToBuild.get().toList().let { targets ->
                // Prefer the ABIs embedded into the Android module. ABIs may be excluded using
                // embedRustLibrary, e.g., in modules without ABI filters.
                targets.filter { rustTarget ->
                    val build = cargoExtension.builds.findByRustTarget(rustTarget)
                    build is CargoAndroidBuild && build.variants.any { variant ->
                        variant.embedRustLibrary.get()
                    }
                }.ifEmpty { targets }
            }

            @OptIn(InternalGobleyGradleApi::class)
            val hasJvmTarget = kotlinExtensionDelegate.targets.any {
                it is KotlinJvmTarget || it is KotlinWithJavaTarget<*, *>
            }

            val jvmTargetsToBuild = when {
                hasJvmTarget -> cargoExtension.builds.mapNotNull { build ->
                    build.rustTarget.takeIf {
                        build is CargoJvmBuild<*> && build.variants.any { variant ->
                            variant.embedRustLibrary.get()
                        }
                    }
                }

                else -> emptyList()
            }

            @OptIn(InternalGobleyGradleApi::class)
            val nativeTargetsToBuild = kotlinExtensionDelegate.targets.mapNotNull { target ->
                val nativeTarget = target as? KotlinNativeTarget ?: return@mapNotNull null
                RustTarget(nativeTarget.konanTarget)
            }

            (androidTargetsToBuild + jvmTargetsToBuild + nativeTargetsToBuild).first()
        }

        if (buildRustTarget is RustWasmTarget) {
            throw GradleException("$buildRustTarget not available for UniFFI. Try building with other targets.")
        }

        val build = cargoExtension.builds.findByRustTarget(buildRustTarget)
            ?: throw GradleException("Cargo build for $buildRustTarget not available")

        val availableVariants = build.kotlinTargets.flatMap {
            when {
                it is KotlinJvmTarget || it is KotlinWithJavaTarget<*, *> -> listOf((build as CargoJvmBuild<*>).jvmVariant.get())
                it.platformType == KotlinPlatformType.androidJvm -> availableAndroidVariants(build)
                it is KotlinNativeTarget -> listOf((build as CargoNativeBuild<*>).nativeVariant.get())
                else -> emptyList<Variant>()
            }
        }.distinct()

        val variant = bindingsGeneration.variant.orNull
            ?: availableVariants.firstOrNull()
            ?: throw GradleException("Cargo build $buildRustTarget has no available variants")

        if (!availableVariants.contains(variant))
            throw GradleException("Variant $variant is not available in Cargo build $buildRustTarget")

        val buildVariantForBindings = build.variant(variant)
        val cargoBuildTaskForBindings = buildVariantForBindings.buildTaskProvider
        val bindingsOutputFile = cargoBuildTaskForBindings.flatMap { task ->
            task.libraryFileByCrateType.map {
                it[CrateType.SystemDynamicLibrary] ?: it.values.first()
            }
        }

        val installBindgen = tasks.register<InstallUniffiBindgenTask>("installUniffiBindgen") {
            group = TASK_GROUP
            binaryCrateSource.set(uniFfiExtension.bindgenSource)
            installDirectory.set(layout.buildDirectory.dir("gobley-tools-install/uniffi-bindgen"))
        }

        @OptIn(InternalGobleyGradleApi::class)
        val externalPackageUniFfiConfigurations =
            DependencyUtils.getExternalPackageUniFfiConfigurations(this)

        val mergeUniffiConfig = tasks.register<MergeUniffiConfigTask>("mergeUniffiConfig") {
            group = TASK_GROUP
            originalConfig.set(
                bindingsGeneration.config.orElse(
                    cargoExtension.packageDirectory.file("uniffi.toml"),
                ).map { regularFile ->
                    // TODO: This compiles well, but Android Studio shows an error. See #86.
                    regularFile.takeIf { it.asFile.exists() }
                }
            )

            crateName.set(cargoExtension.cargoPackage.map { it.libraryCrateName })
            packageRoot.set(cargoExtension.cargoPackage.map { it.root.asFile.path })
            packageName.set(bindingsGeneration.packageName)
            cdylibName.set(bindingsGeneration.cdylibName)
            generateImmutableRecords.set(bindingsGeneration.generateImmutableRecords)
            omitChecksums.set(bindingsGeneration.omitChecksums)
            customTypes.set(bindingsGeneration.customTypes)
            disableJavaCleaner.set(bindingsGeneration.disableJavaCleaner)
            usePascalCaseEnumClass.set(bindingsGeneration.usePascalCaseEnumClass)
            @OptIn(InternalGobleyGradleApi::class)
            enableJnaInterfaceMapping.set(bindingsGeneration.enableJnaInterfaceMapping)

            @OptIn(InternalGobleyGradleApi::class)
            kotlinMultiplatform.set(kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_MULTIPLATFORM)

            @OptIn(InternalGobleyGradleApi::class)
            kotlinTargets.set(
                kotlinExtensionDelegate.targets.mapNotNull {
                    when {
                        it is KotlinMetadataTarget -> null
                        it is KotlinJvmTarget || it is KotlinWithJavaTarget<*, *> -> "jvm"
                        it.platformType == KotlinPlatformType.androidJvm -> "android"
                        it is KotlinNativeTarget -> "native"
                        else -> "stub"
                    }
                }
            )

            if (externalPackageUniFfiConfigurations != null) {
                externalPackageConfigs.addAll(externalPackageUniFfiConfigurations)
            }

            @OptIn(InternalGobleyGradleApi::class)
            val kotlinVersionFromExtension = kotlinExtensionDelegate.implementationVersion
            if (kotlinVersionFromExtension != null) {
                kotlinVersion.set(kotlinVersionFromExtension)
            }

            // If the serialization plugin is applied and the runtime is added to the dependency
            // list, set `generateSerializableTypes` to true so the bindgen renders @Serialization
            // where applicable.
            @OptIn(InternalGobleyGradleApi::class)
            if (plugins.hasPlugin(PluginIds.KOTLIN_SERIALIZATION)) {
                @OptIn(InternalGobleyGradleApi::class)
                DependencyUtils.configureEachCommonDependencies(configurations) { dependency ->
                    if (dependency.group == "org.jetbrains.kotlinx"
                        && dependency.name.startsWith("kotlinx-serialization-")
                    ) {
                        useKotlinXSerialization.set(true)
                    }
                }
            }
            outputConfig.set(mergedConfig)
        }

        @OptIn(InternalGobleyGradleApi::class)
        DependencyUtils.addMergedUniffiConfigArtifact(this, mergeUniffiConfig)

        val buildBindings = tasks.register<BuildUniffiBindingsTask>("buildUniffiBindings") {
            group = TASK_GROUP

            cargoPackage.set(cargoExtension.cargoPackage)
            bindgen.set(installBindgen.get().bindgen)
            outputDirectory.set(bindingsDirectory)
            if (uniFfiExtension.formatCode.isPresent)
                formatCode.set(uniFfiExtension.formatCode.get())

            config.set(mergeUniffiConfig.flatMap { it.outputConfig })

            if (externalPackageUniFfiConfigurations != null) {
                externalPackageConfigs.addAll(externalPackageUniFfiConfigurations)
            }

            when (bindingsGeneration) {
                is BindingsGenerationFromUdl -> {
                    libraryMode.set(false)
                    source.set(bindingsGeneration.udlFile)
                }

                is BindingsGenerationFromLibrary -> {
                    libraryMode.set(true)
                    source.set(bindingsOutputFile)
                }
            }
            dependsOn(cargoBuildTaskForBindings, installBindgen, mergeUniffiConfig)
        }

        if (uniFfiExtension.generateDuringSync.get()) {
            @OptIn(InternalGobleyGradleApi::class)
            GradleUtils.runTaskDuringSync(this, *listOfNotNull(buildBindings, syncAndroidBindingsTask).toTypedArray())
        }

        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            dependsOn(buildBindings)
        }

        tasks.withType<Jar>().configureEach {
            dependsOn(buildBindings)
        }

        tasks.withType<CInteropProcess>().configureEach {
            dependsOn(buildBindings)
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun availableAndroidVariants(build: CargoBuild<*>): List<Variant> {
        return when {
            // Android modules without build types embed the variant specified by the user.
            ::androidDelegate.isInitialized && !androidDelegate.hasBuildTypes ->
                listOf(((build as? HasAndroidVariant) ?: cargoExtension).androidVariant.get())

            else -> Variant.entries
        }
    }

    private fun Project.configureCleanTasks() {
        val cleanBindings = tasks.register<Delete>("cleanBindings") {
            group = TASK_GROUP
            delete(bindingsDirectory)
        }

        tasks.named<Delete>("clean") {
            dependsOn(cleanBindings)
        }
    }

    private fun Project.configureKotlin() {
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }

        val dummyDefFile = nativeBindingsCInteropDef("dummy")
        val generateDummyDefFileTask = tasks.register("generateDummyDefFile") {
            doLast {
                dummyDefFile.get().asFile.run {
                    parentFile.mkdirs()
                    writeBytes(byteArrayOf())
                }
            }
            mustRunAfter(tasks.named("buildUniffiBindings"))
        }

        @OptIn(InternalGobleyGradleApi::class)
        kotlinExtensionDelegate.targets.configureEach {
            when {
                this is KotlinMetadataTarget -> configureKotlinCommonTarget()
                this is KotlinJvmTarget || this is KotlinWithJavaTarget<*, *> -> {
                    if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_JVM) {
                        configureKotlinCommonTarget()
                    }
                    configureKotlinJvmTarget()
                }

                // Both the single-target Android target and the target of the
                // `com.android.kotlin.multiplatform.library` plugin.
                platformType == KotlinPlatformType.androidJvm -> {
                    if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_ANDROID) {
                        configureKotlinCommonTarget()
                    }
                    configureKotlinAndroidTarget()
                }

                this is KotlinNativeTarget -> configureKotlinNativeTarget(
                    this,
                    dummyDefFile,
                    generateDummyDefFileTask,
                )

                else -> configureUnsupportedTarget(this)
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinCommonTarget() {
        if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_ANDROID) {
            // Single-target Android projects have no Kotlin source sets. The bindings are added
            // in configureAndroidBindingsSources.
            if (uniFfiExtension.addDependencies.get()) {
                val configurationName =
                    kotlinExtensionDelegate.sourceSets.androidMainConfigurations()!!.implementation
                for ((dependencyNotation, preferredVersion) in commonDependencies) {
                    DependencyUtils.addDependency(
                        this,
                        configurationName,
                        dependencyNotation,
                        preferredVersion,
                    )
                }
            }
            return
        }
        with(kotlinExtensionDelegate.sourceSets.commonMain) {
            kotlin.srcDir(
                when (kotlinExtensionDelegate.pluginId) {
                    PluginIds.KOTLIN_JVM -> mainBindingsDirectory
                    else -> commonBindingsDirectory
                }
            )
            if (uniFfiExtension.addDependencies.get()) {
                dependencies {
                    for ((dependencyNotation, preferredVersion) in commonDependencies) {
                        implementation(dependencyNotation) {
                            version { prefer(preferredVersion) }
                        }
                    }
                }
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinJvmTarget() {
        with(kotlinExtensionDelegate.sourceSets.jvmMain) {
            if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_MULTIPLATFORM) {
                kotlin.srcDir(jvmBindingsDirectory)
            }
            if (uniFfiExtension.addDependencies.get()) {
                dependencies {
                    implementation("net.java.dev.jna:jna") {
                        version { prefer(DependencyVersions.JNA) }
                    }
                }
            }
        }
    }

    @OptIn(InternalGobleyGradleApi::class)
    private fun Project.configureKotlinAndroidTarget() {
        val sourceSets = kotlinExtensionDelegate.sourceSets
        if (kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_MULTIPLATFORM) {
            sourceSets.androidMain?.kotlin?.srcDir(androidBindingsDirectory)
        }
        val mainConfigurations = sourceSets.androidMainConfigurations() ?: return
        if (uniFfiExtension.addDependencies.get()) {
            DependencyUtils.addDependency(
                this,
                mainConfigurations.implementation,
                "net.java.dev.jna:jna@aar",
                DependencyVersions.JNA,
            )
            DependencyUtils.addDependency(
                this,
                mainConfigurations.implementation,
                "androidx.annotation:annotation",
                DependencyVersions.ANDROIDX_ANNOTATION,
            )
        }
        val jnaDependency = getConflictingDependency(
            mainConfigurations.implementation,
            "net.java.dev.jna:jna:${DependencyVersions.JNA}",
        )
        val jnaVersion = jnaDependency?.version ?: DependencyVersions.JNA
        val composePreviewVariant = GradleUtils.getComposePreviewVariant(gradle)
        if (composePreviewVariant != null) {
            val previewConfigurations = sourceSets.androidMainConfigurations(composePreviewVariant)
            if (previewConfigurations != null && uniFfiExtension.addDependencies.get()) {
                DependencyUtils.addDependency(
                    this,
                    previewConfigurations.implementation,
                    "net.java.dev.jna:jna",
                    jnaVersion,
                )
            }
        }
        if (uniFfiExtension.addDependencies.get()) {
            for (unitTestConfigurations in sourceSets.androidUnitTestConfigurations()) {
                DependencyUtils.addDependency(
                    this,
                    unitTestConfigurations.implementation,
                    "net.java.dev.jna:jna",
                    jnaVersion,
                )
            }
        }
    }

    private fun Project.configureKotlinNativeTarget(
        kotlinNativeTarget: KotlinNativeTarget,
        dummyDefFile: Provider<RegularFile>,
        generateDummyDefFileTask: TaskProvider<Task>,
    ) {
        val namespace = bindingsGeneration.namespace.get()
        kotlinNativeTarget.compilations.getByName("main") {
            cinterops.register(TASK_GROUP) {
                packageName("$namespace.cinterop")
                header(project.nativeBindingsCInteropHeader(namespace))
                // Since linking is handled by CargoPlugin and header is fed above, we don't need the defFile.
                defFile(dummyDefFile)
                tasks.named(interopProcessingTaskName) {
                    inputs.file(dummyDefFile)
                    dependsOn(generateDummyDefFileTask)
                }
            }
            defaultSourceSet {
                kotlin.srcDir(nativeBindingsDirectory)
            }
            compileTaskProvider.configure {
                compilerOptions.optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
    }

    private fun Project.configureUnsupportedTarget(kotlinTarget: KotlinTarget) {
        kotlinTarget.compilations.getByName("main").defaultSourceSet {
            kotlin.srcDir(stubBindingsDirectory)
        }
    }
}

// The dependencies of the generated bindings in common code, with their preferred versions.
@OptIn(InternalGobleyGradleApi::class)
private val commonDependencies = listOf(
    "org.jetbrains.kotlinx:atomicfu" to DependencyVersions.KOTLINX_ATOMICFU,
    "org.jetbrains.kotlinx:kotlinx-datetime" to DependencyVersions.KOTLINX_DATETIME,
    "org.jetbrains.kotlinx:kotlinx-coroutines-core" to DependencyVersions.KOTLINX_COROUTINES,
)

private val Project.bindingsDirectory: Provider<Directory>
    get() = layout.buildDirectory.dir("generated/uniffi")

private val Project.mainBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("main/kotlin") }

private val Project.commonBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("commonMain/kotlin") }

private val Project.jvmBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("jvmMain/kotlin") }

private val Project.androidBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("androidMain/kotlin") }

private val Project.nativeBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("nativeMain/kotlin") }

private val Project.stubBindingsDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("stubMain/kotlin") }

// Must not be in bindingsDirectory, which is the output directory of BuildUniffiBindingsTask.
private val Project.androidGeneratedProguardFile: Provider<RegularFile>
    get() = layout.buildDirectory.file("generated/uniffiProguardRules/generated-proguard-rules.txt")

private val Project.nativeBindingsCInteropDirectory: Provider<Directory>
    get() = bindingsDirectory.map { it.dir("nativeInterop/cinterop") }

private val Project.mergedConfig: Provider<RegularFile>
    get() = layout.buildDirectory.file("intermediates/merged_uniffi_config/uniffi.toml")

private fun Project.nativeBindingsCInteropDef(libraryCrateName: String): Provider<RegularFile> =
    nativeBindingsCInteropDirectory.map { it.file("$libraryCrateName.def") }

private fun Project.nativeBindingsCInteropHeader(namespace: String): Provider<RegularFile> =
    nativeBindingsCInteropDirectory.map { it.file("headers/$namespace/$namespace.h") }

private fun Project.getConflictingDependency(
    configurationName: String,
    dependencyNotation: String,
): ExternalModuleDependency? {
    val dependencyToAdd =
        dependencies.create(dependencyNotation) as ExternalModuleDependency
    val configuration = configurations.findByName(configurationName) ?: return null
    return configuration.dependencies.firstOrNull { dependency ->
        dependency is ExternalModuleDependency
                && dependency.module.group == dependencyToAdd.module.group
                && dependency.module.name == dependencyToAdd.module.name
    } as? ExternalModuleDependency
}
