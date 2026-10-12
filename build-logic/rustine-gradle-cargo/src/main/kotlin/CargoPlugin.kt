/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo

import rustine.gradle.AppleSdk
import rustine.gradle.RustineHost
import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.PluginIds
import rustine.gradle.Variant
import rustine.gradle.android.RustineAndroidExtensionDelegate
import rustine.gradle.android.RustineAndroidVariant
import rustine.gradle.cargo.dsl.CargoAndroidBuild
import rustine.gradle.cargo.dsl.CargoAndroidBuildVariant
import rustine.gradle.cargo.dsl.CargoExtension
import rustine.gradle.cargo.dsl.CargoJvmBuild
import rustine.gradle.cargo.dsl.CargoJvmBuildVariant
import rustine.gradle.cargo.dsl.CargoNativeBuild
import rustine.gradle.cargo.dsl.CargoNativeBuildVariant
import rustine.gradle.cargo.dsl.CargoWasmBuild
import rustine.gradle.cargo.dsl.CargoWasmBuildVariant
import rustine.gradle.cargo.dsl.android
import rustine.gradle.cargo.dsl.jvm
import rustine.gradle.cargo.dsl.native
import rustine.gradle.cargo.dsl.wasm
import rustine.gradle.cargo.tasks.CargoCleanTask
import rustine.gradle.cargo.tasks.CargoTask
import rustine.gradle.cargo.tasks.CopyCargoJniLibsTask
import rustine.gradle.cargo.tasks.InstallWasmTransformerTask
import rustine.gradle.cargo.tasks.RustUpTargetAddTask
import rustine.gradle.cargo.tasks.RustUpTask
import rustine.gradle.cargo.utils.register
import rustine.gradle.kotlin.RustineKotlinExtensionDelegate
import rustine.gradle.rust.CrateType
import rustine.gradle.rust.targets.RustAndroidTarget
import rustine.gradle.rust.targets.RustJvmTarget
import rustine.gradle.rust.targets.RustTarget
import rustine.gradle.rust.targets.RustWasmTarget
import rustine.gradle.tasks.useGlobalLock
import rustine.gradle.utils.DependencyUtils
import rustine.gradle.utils.GradleUtils
import rustine.gradle.utils.PluginUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

class CargoPlugin : Plugin<Project> {
    companion object {
        internal const val TASK_GROUP = "cargo"
    }

    private lateinit var cargoExtension: CargoExtension

    @OptIn(InternalRustineGradleApi::class)
    private lateinit var kotlinExtensionDelegate: RustineKotlinExtensionDelegate

    @OptIn(InternalRustineGradleApi::class)
    private lateinit var androidDelegate: RustineAndroidExtensionDelegate

    override fun apply(target: Project) {
        @OptIn(InternalRustineGradleApi::class)
        if (!target.plugins.hasPlugin(PluginIds.RUSTINE_RUST)) {
            DependencyUtils.createCargoConfigurations(target)
        }
        cargoExtension = target.extensions.create<CargoExtension>(TASK_GROUP, target)
        cargoExtension.jvmVariant.convention(Variant.Debug)
        cargoExtension.jvmPublishingVariant.convention(Variant.Release)
        cargoExtension.nativeVariant.convention(Variant.Debug)
        cargoExtension.wasmVariant.convention(Variant.Debug)
        // Android modules without build types have a single variant, which is used by both debug
        // and release builds of the apps depending on them. Use the release build when release
        // builds or publishing are requested.
        @OptIn(InternalRustineGradleApi::class)
        cargoExtension.androidVariant.convention(
            if (GradleUtils.requestsReleaseBuild(target.gradle)) Variant.Release else Variant.Debug
        )
        readVariantsFromXcode()
        cargoExtension.builds.native {
            nativeVariant.convention(
                cargoExtension.nativeTargetVariantOverride.getting(rustTarget)
                    .orElse(cargoExtension.nativeVariant)
            )
        }
        cargoExtension.builds.jvm {
            jvmVariant.convention(cargoExtension.jvmVariant)
            jvmPublishingVariant.convention(cargoExtension.jvmPublishingVariant)
        }
        cargoExtension.builds.wasm {
            wasmVariant.convention(cargoExtension.wasmVariant)
        }
        cargoExtension.builds.android {
            androidVariant.convention(cargoExtension.androidVariant)
        }
        @OptIn(InternalRustineGradleApi::class)
        target.useGlobalLock()
        target.tasks.withType<CargoTask>().configureEach {
            additionalEnvironmentPath.add(cargoExtension.toolchainDirectory)
        }
        target.tasks.withType<RustUpTask>().configureEach {
            additionalEnvironmentPath.add(cargoExtension.toolchainDirectory)
        }
        target.watchPluginChanges()
        target.afterEvaluate {
            target.checkRequiredPlugins()
            target.checkKotlinTargets()
            applyAfterEvaluate(this)
        }
    }

    private fun applyAfterEvaluate(target: Project): Unit = with(target) {
        checkRequiredCrateTypes()
        if (cargoExtension.builds.isEmpty()) {
            logger.warn("No Kotlin targets detected.")
            return
        }

        configureBuildTasks()
        configureCleanTasks()

        @OptIn(InternalRustineGradleApi::class)
        DependencyUtils.resolveCargoDependencies(target)
    }

    @OptIn(InternalRustineGradleApi::class)
    private fun Project.watchPluginChanges() {
        PluginUtils.withKotlinPlugin(this) { delegate ->
            kotlinExtensionDelegate = delegate
            kotlinExtensionDelegate.targets.configureEach { planBuilds() }
        }
        // Android Gradle Plugin callbacks must be registered here, as the Android Gradle Plugin
        // may be applied before this plugin, in which case its afterEvaluate callback runs
        // before ours.
        PluginUtils.withAndroidPlugin(this) { delegate ->
            androidDelegate = delegate
            cargoExtension.androidTargetsToBuild.convention(project.provider {
                // Read the ABI filters after the build script is evaluated.
                val abiFilters = delegate.abiFilters
                if (abiFilters.isNotEmpty()) {
                    abiFilters.map(::RustAndroidTarget)
                } else {
                    RustAndroidTarget.entries
                }
            })
            delegate.onVariants { androidVariant ->
                registerAndroidJniLibsTask(androidVariant)
            }
        }
    }

    private fun Project.checkRequiredPlugins() {
        @OptIn(InternalRustineGradleApi::class)
        PluginUtils.ensureKotlinPluginIsApplied(this, ::kotlinExtensionDelegate.isInitialized)
    }

    private fun KotlinTarget.planBuilds() {
        for (rustTarget in requiredRustTargets()) {
            cargoExtension.createOrGetBuild(rustTarget).kotlinTargets.add(this)
        }
    }

    private fun KotlinTarget.requiredRustTargets(): List<RustTarget> {
        return when (platformType) {
            KotlinPlatformType.jvm -> {
                RustineHost.current.platform.supportedTargets.filterIsInstance<RustJvmTarget>()
            }

            KotlinPlatformType.androidJvm -> {
                // listOf(RustineHost.current.rustTarget) is for Android local unit tests.
                listOf(RustineHost.current.rustTarget) + RustAndroidTarget.values()
            }

            KotlinPlatformType.native -> {
                listOf(RustTarget((this as KotlinNativeTarget).konanTarget))
            }

            KotlinPlatformType.js -> {
                RustWasmTarget.values().toList()
            }

            else -> listOf()
        }
    }

    @OptIn(InternalRustineGradleApi::class)
    private fun Project.checkKotlinTargets() {
        val hasWasmTargets =
            kotlinExtensionDelegate.targets.any { it.platformType == KotlinPlatformType.wasm }
        if (hasWasmTargets) {
            project.logger.warn("WASM targets are added, but Rustine does not support WASM targets yet.")
        }

        val hasAndroidJvmTargets =
            kotlinExtensionDelegate.targets.any { it.platformType == KotlinPlatformType.androidJvm }
        if (hasAndroidJvmTargets && !::androidDelegate.isInitialized) {
            throw GradleException("Android JVM targets are added, but Android Gradle Plugin is not found.")
        }
    }

    private fun checkRequiredCrateTypes() {
        val requiredCrateTypes = cargoExtension
            .builds
            .flatMap { it.kotlinTargets }
            .map { it.platformType.requiredCrateType() }
            .distinct()
        val actualCrateTypes = cargoExtension.cargoPackage.get().libraryCrateTypes
        if (!actualCrateTypes.containsAll(requiredCrateTypes)) {
            throw GradleException(
                "Crate does not have required crate types. Required: $requiredCrateTypes, actual: $actualCrateTypes"
            )
        }
    }

    @OptIn(InternalRustineGradleApi::class)
    private fun readVariantsFromXcode() {
        val sdkName = System.getenv("SDK_NAME") ?: return
        val sdk = AppleSdk(sdkName)

        val configuration = System.getenv("CONFIGURATION") ?: return
        val variant = Variant(configuration)

        val archs = System.getenv("ARCHS")?.split(' ')?.map(AppleSdk::Arch) ?: return
        cargoExtension.nativeTargetVariantOverride.putAll(
            archs.mapNotNull(sdk::rustTarget).associateWith { variant })
    }

    private fun Project.configureBuildTasks() {
        val androidTarget = cargoExtension.builds.firstNotNullOfOrNull { build ->
            build.kotlinTargets.firstOrNull { it.platformType == KotlinPlatformType.androidJvm }
        }
        val jvmTarget = cargoExtension.builds.firstNotNullOfOrNull { build ->
            build.kotlinTargets.firstOrNull {
                it is KotlinJvmTarget || it is KotlinWithJavaTarget<*, *>
            }
        }
        val wasmBindgenInstallTask =
            tasks.register<InstallWasmTransformerTask>("installWasmTransformer") {
                group = TASK_GROUP
                binaryCrateSource.set(cargoExtension.wasmTransformerSource)
                installDirectory.set(layout.buildDirectory.dir("rustine-tools-install/wasm-transformer"))
            }
        for (cargoBuild in cargoExtension.builds) {
            val rustUpTargetAddTask =
                tasks.register<RustUpTargetAddTask>({ +cargoBuild.rustTarget }) {
                    group = TASK_GROUP
                    this.rustTarget.set(cargoBuild.rustTarget)
                    this.rustVersion.set(cargoExtension.rustVersion)
                }
            for (cargoBuildVariant in cargoBuild.variants) {
                val projectLayout = layout
                cargoBuildVariant.buildTaskProvider.configure {
                    nativeStaticLibsDefFile.set(
                        projectLayout.outputCacheFile(
                            this,
                            "nativeStaticLibsDefFile",
                        )
                    )
                    buildScriptOutputDirectoriesFile.set(
                        projectLayout.outputCacheFile(
                            this,
                            "buildScriptOutputDirectoriesFile",
                        )
                    )
                    if (cargoBuild.installTargetBeforeBuild.get()) {
                        dependsOn(rustUpTargetAddTask)
                    }
                    if (cargoBuildVariant is CargoAndroidBuildVariant) {
                        @OptIn(InternalRustineGradleApi::class)
                        val environmentVariables = cargoBuildVariant.rustTarget.ndkEnvVariables(
                            sdkRoot = androidDelegate.androidSdkRoot,
                            apiLevel = androidDelegate.androidMinSdk,
                            ndkVersion = androidDelegate.androidNdkVersion,
                            ndkRoot = androidDelegate.androidNdkRoot,
                        )
                        additionalEnvironment.putAll(environmentVariables)
                    }
                }
                cargoBuildVariant.checkTaskProvider.configure {
                    if (cargoBuild.installTargetBeforeBuild.get()) {
                        dependsOn(rustUpTargetAddTask)
                    }
                    if (cargoBuildVariant is CargoAndroidBuildVariant) {
                        @OptIn(InternalRustineGradleApi::class)
                        val environmentVariables = cargoBuildVariant.rustTarget.ndkEnvVariables(
                            sdkRoot = androidDelegate.androidSdkRoot,
                            apiLevel = androidDelegate.androidMinSdk,
                            ndkVersion = androidDelegate.androidNdkVersion,
                            ndkRoot = androidDelegate.androidNdkRoot,
                        )
                        additionalEnvironment.putAll(environmentVariables)
                    }
                }
            }
            for (kotlinTarget in cargoBuild.kotlinTargets) {
                when (kotlinTarget.platformType) {
                    KotlinPlatformType.jvm -> {
                        cargoBuild as CargoJvmBuild<*>
                        cargoBuild.variants {
                            configureJvmPostBuildTasks(
                                kotlinTarget,
                                // cargoBuild.jvmVariant is checked inside
                                this,
                                androidTarget,
                            )
                        }
                    }

                    KotlinPlatformType.androidJvm -> {
                        if (cargoBuild is CargoJvmBuild<*>) {
                            if (jvmTarget == null) {
                                cargoBuild.variants {
                                    configureJvmPostBuildTasks(
                                        kotlinTarget,
                                        // cargoBuild.jvmVariant is checked inside
                                        this,
                                        kotlinTarget,
                                    )
                                }
                            }
                        } else {
                            cargoBuild as CargoAndroidBuild
                            cargoBuild.dynamicLibrarySearchPaths.addAll(
                                @OptIn(InternalRustineGradleApi::class)
                                cargoBuild.rustTarget.ndkLibraryDirectories(
                                    sdkRoot = androidDelegate.androidSdkRoot,
                                    apiLevel = androidDelegate.androidMinSdk,
                                    ndkVersion = androidDelegate.androidNdkVersion,
                                    ndkRoot = androidDelegate.androidNdkRoot,
                                ),
                            )
                            Variant.entries.forEach {
                                configureAndroidPostBuildTasks(cargoBuild.variant(it))
                            }
                        }
                    }

                    KotlinPlatformType.native -> {
                        cargoBuild as CargoNativeBuild<*>
                        configureNativeCompilation(
                            kotlinTarget as KotlinNativeTarget,
                            cargoBuild.variant(cargoBuild.nativeVariant.get())
                        )
                    }

                    KotlinPlatformType.js -> {
                        cargoBuild as CargoWasmBuild
                        configureWasmCompilation(
                            kotlinTarget as KotlinJsIrTarget,
                            cargoBuild.variant(cargoBuild.wasmVariant.get()),
                            wasmBindgenInstallTask,
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    private fun Project.configureJvmPostBuildTasks(
        // kotlinTarget can be an Android target when the JVM target is not present. This is for
        // Android local unit tests.
        kotlinTarget: KotlinTarget,
        cargoBuildVariant: CargoJvmBuildVariant<*>,
        androidTarget: KotlinTarget?,
    ) {
        val buildTask = cargoBuildVariant.buildTaskProvider
        val checkTask = cargoBuildVariant.checkTaskProvider
        val findDynamicLibrariesTask = cargoBuildVariant.findDynamicLibrariesTaskProvider
        val jarTask = cargoBuildVariant.jarTaskProvider
        cargoBuildVariant.dynamicLibrarySearchPaths.add(
            cargoBuildVariant.profile.zip(cargoExtension.cargoPackage) { profile, cargoPackage ->
                cargoPackage.outputDirectory(profile, cargoBuildVariant.rustTarget).asFile
            }
        )
        cargoBuildVariant.dynamicLibrarySearchPaths.addAll(
            cargoBuildVariant.buildTaskProvider.flatMap { it.buildScriptOutputDirectories }
        )
        val projectLayout = layout
        findDynamicLibrariesTask.configure {
            libraryPathsCacheFile.set(projectLayout.outputCacheFile(this, "libraryPathsCacheFile"))
        }

        // For Kotlin/JVM projects without the application plugin or the Compose Multiplatform
        // plugin, the dynamic libraries must be copied in the resources directory to be loaded
        // during runtime. See #95.
        //
        // To avoid being included in the resources when class files are packaged into a JAR file,
        // `copyLibrariesTask` is invoked only when `GradleUtils.invokedByKotlinJvmBuild()`
        // returns `false`.
        val resourcePrefix = cargoBuildVariant.resourcePrefix.orNull?.takeIf(String::isNotEmpty)
        val resourceDirectory = layout.buildDirectory
            .dir("intermediates/rust/${cargoBuildVariant.rustTarget.rustTriple}/${cargoBuildVariant.variant}")
        val resourceCopyDestination = if (resourcePrefix == null) {
            resourceDirectory
        } else resourceDirectory.map {
            it.dir(resourcePrefix)
        }
        val copyLibrariesTask = tasks.register<Copy>({
            +"jvm"
            +cargoBuildVariant
        }) {
            from(cargoBuildVariant.libraryFiles)
            into(resourceCopyDestination)
            dependsOn(buildTask, findDynamicLibrariesTask)
        }

        @OptIn(InternalRustineGradleApi::class)
        if (
            kotlinTarget.platformType != KotlinPlatformType.androidJvm
            && cargoBuildVariant.embedRustLibrary.get()
            && cargoBuildVariant.variant == cargoBuildVariant.build.jvmVariant.get()
        ) {
            val invokedByKotlinJvmBuild = GradleUtils.invokedByKotlinJvmBuild(gradle)
            if (invokedByKotlinJvmBuild) {
                val expectedTaskName = when (kotlinExtensionDelegate.pluginId) {
                    PluginIds.KOTLIN_JVM -> "processResources"
                    else -> "${kotlinTarget.name}ProcessResources"
                }
                tasks.withType<ProcessResources> {
                    if (name == expectedTaskName) {
                        dependsOn(copyLibrariesTask)
                    }
                }
            }
            with(kotlinExtensionDelegate.sourceSets.jvmMain) {
                if (invokedByKotlinJvmBuild) {
                    resources.srcDir(resourceDirectory)
                }
                dependencies {
                    runtimeOnly(files(jarTask.flatMap { it.archiveFile }))
                }
            }
        }

        @OptIn(InternalRustineGradleApi::class)
        if (
            kotlinTarget.platformType != KotlinPlatformType.androidJvm
            && cargoBuildVariant.embedRustLibrary.get()
            && cargoBuildVariant.variant == cargoBuildVariant.build.jvmPublishingVariant.get()
            && cargoExtension.publishJvmArtifacts.get()
            && kotlinExtensionDelegate.pluginId == PluginIds.KOTLIN_MULTIPLATFORM
        ) {
            plugins.withId("maven-publish") {
                val publishing = extensions.getByType(PublishingExtension::class.java)
                val publication = publishing.publications.getByName(kotlinTarget.name)
                if (publication is MavenPublication) {
                    publication.artifact(jarTask)
                }
            }
        }

        if (cargoBuildVariant.embedRustLibrary.get()) {
            tasks.named("check") {
                dependsOn(checkTask)
            }
        }

        @OptIn(InternalRustineGradleApi::class)
        if (androidTarget != null && cargoBuildVariant.androidUnitTest.get()) {
            DependencyUtils.addAndroidUnitTestRuntimeRustLibraryJar(
                this,
                cargoBuildVariant.rustTarget,
                cargoBuildVariant.variant,
                jarTask,
            )
            val unitTestConfigurations = kotlinExtensionDelegate.sourceSets
                .androidUnitTestConfigurations(cargoBuildVariant.variant)
            for (configurations in unitTestConfigurations) {
                DependencyUtils.addDependency(
                    this,
                    configurations.runtimeOnly,
                    files(jarTask.flatMap { it.archiveFile }),
                )
            }
            // Only support debug mode Compose previews.
            // Since one of the dependencies of Compose previews, androidx.compose.ui:ui-tooling,
            // is referenced as debugImplementation in the default template generated from
            // Android Studio, and there is relatively small chance of users requiring to use
            // the Rust library from release mode Compose previews, let's just handle debug mode
            // Compose previews. See #94 for details.
            if (cargoBuildVariant.variant == Variant.Debug
                && cargoBuildVariant.variant == GradleUtils.getComposePreviewVariant(gradle)
            ) {
                kotlinExtensionDelegate.sourceSets
                    .androidMainConfigurations(Variant.Debug)
                    ?.let { configurations ->
                        DependencyUtils.addDependency(
                            this,
                            configurations.runtimeOnly,
                            files(jarTask.flatMap { it.archiveFile }),
                        )
                    }
            }
        }
    }

    // The Rust libraries are added to Android variants by the tasks registered in
    // registerAndroidJniLibsTask.
    private fun Project.configureAndroidPostBuildTasks(cargoBuildVariant: CargoAndroidBuildVariant) {
        val checkTask = cargoBuildVariant.checkTaskProvider
        val findDynamicLibrariesTask = cargoBuildVariant.findDynamicLibrariesTaskProvider
        cargoBuildVariant.dynamicLibrarySearchPaths.add(
            cargoBuildVariant.profile.zip(cargoExtension.cargoPackage) { profile, cargoPackage ->
                cargoPackage.outputDirectory(profile, cargoBuildVariant.rustTarget).asFile
            }
        )
        cargoBuildVariant.dynamicLibrarySearchPaths.addAll(
            cargoBuildVariant.buildTaskProvider.flatMap { it.buildScriptOutputDirectories }
        )
        val projectLayout = layout
        findDynamicLibrariesTask.configure {
            libraryPathsCacheFile.set(projectLayout.outputCacheFile(this, "libraryPathsCacheFile"))
        }

        @OptIn(InternalRustineGradleApi::class)
        if (!cargoExtension.androidTargetsToBuild.get().contains(cargoBuildVariant.rustTarget))
            return

        if (!cargoBuildVariant.embedRustLibrary.get())
            return

        tasks.named("check") {
            dependsOn(checkTask)
        }
    }

    /**
     * Registers a task copying the Rust libraries for each Android ABI to the `jniLibs` directory of
     * [androidVariant]. This is invoked by the Android Gradle Plugin, possibly before the
     * afterEvaluate callback of this plugin. Thus, the task must be configured lazily, and must not
     * refer to tasks registered in the afterEvaluate callback.
     */
    @OptIn(InternalRustineGradleApi::class)
    private fun Project.registerAndroidJniLibsTask(androidVariant: RustineAndroidVariant) {
        val androidVariantName = androidVariant.name
        // When the Android module has no build types, use the variant specified by the user.
        val variantFromBuildType = androidVariant.variant
        val jniLibsTask = tasks.register<CopyCargoJniLibsTask>(
            "copyCargoJniLibs${androidVariantName.uppercaseFirstChar()}"
        ) {
            group = TASK_GROUP
            description = "Copies the Rust libraries for the $androidVariantName Android variant."
            val androidTargetsToBuild = cargoExtension.androidTargetsToBuild.get()
            for (cargoBuild in cargoExtension.builds.withType(CargoAndroidBuild::class.java)) {
                if (!androidTargetsToBuild.contains(cargoBuild.rustTarget))
                    continue

                val cargoBuildVariant = cargoBuild.variant(
                    variantFromBuildType ?: cargoBuild.androidVariant.get()
                )
                if (!cargoBuildVariant.embedRustLibrary.get())
                    continue

                val buildTask = cargoBuildVariant.buildTaskProvider
                val findDynamicLibrariesTask = cargoBuildVariant.findDynamicLibrariesTaskProvider
                abi(cargoBuild.rustTarget.androidAbiName) {
                    libraries.from(
                        buildTask.flatMap { task ->
                            task.libraryFileByCrateType.map { it[CrateType.SystemDynamicLibrary]!! }
                        },
                        findDynamicLibrariesTask.flatMap { it.libraryPaths },
                    )
                }
                // libraryFileByCrateType does not carry the dependency on the build task.
                dependsOn(buildTask, findDynamicLibrariesTask)
            }
        }
        androidVariant.addGeneratedJniLibsDirectory(jniLibsTask) { it.outputDirectory }
    }

    private fun Project.configureNativeCompilation(
        kotlinTarget: KotlinNativeTarget,
        cargoBuildVariant: CargoNativeBuildVariant<*>,
    ) {
        val buildTask = cargoBuildVariant.buildTaskProvider
        val checkTask = cargoBuildVariant.checkTaskProvider

        val buildOutputFile = buildTask
            .flatMap { it.libraryFileByCrateType }
            .map { it[CrateType.SystemStaticLibrary]!! }

        kotlinTarget.compilations.getByName("main") {
            cinterops.register("rust") {
                defFile(buildTask.flatMap { it.nativeStaticLibsDefFile })
                extraOpts(
                    "-libraryPath",
                    cargoExtension.cargoPackage.zip(cargoBuildVariant.profile) { cargoPackage, profile ->
                        cargoPackage.outputDirectory(profile, cargoBuildVariant.rustTarget)
                    }.get()
                )
                project.tasks.named(interopProcessingTaskName) {
                    inputs.file(buildOutputFile)
                    dependsOn(buildTask)
                }
            }
            compileTaskProvider.configure {
                compilerOptions.optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        tasks.named("check") {
            dependsOn(checkTask)
        }
    }

    private fun Project.configureWasmCompilation(
        kotlinTarget: KotlinJsIrTarget,
        cargoBuildVariant: CargoWasmBuildVariant,
        wasmBindgenInstallTask: TaskProvider<InstallWasmTransformerTask>,
    ) {
        val buildTask = cargoBuildVariant.buildTaskProvider
        val checkTask = cargoBuildVariant.checkTaskProvider

        cargoBuildVariant.transformWasmProvider.configure {
            wasmTransformer.set(wasmBindgenInstallTask.get().wasmTransformer)
        }

        if (!cargoBuildVariant.embedRustLibrary.get())
            return

        @OptIn(InternalRustineGradleApi::class)
        kotlinExtensionDelegate.sourceSets.run {
            jsMain.kotlin.srcDir(
                cargoBuildVariant.transformWasmProvider.flatMap { it.outputDirectory }
            )
        }

        kotlinTarget.compilations.getByName("main") {
            compileTaskProvider.configure { dependsOn(buildTask) }
        }

        tasks.named("check") {
            dependsOn(checkTask)
        }
    }

    private fun Project.configureCleanTasks() {
        val cleanCrate = tasks.register<CargoCleanTask>("cargoClean") {
            group = TASK_GROUP
            cargoPackage.set(cargoExtension.cargoPackage)
        }

        tasks.named<Delete>("clean") {
            dependsOn(cleanCrate)
        }
    }
}

private fun KotlinPlatformType.requiredCrateType(): CrateType? = when (this) {
    // TODO: properly handle JS and WASM targets
    KotlinPlatformType.common -> null
    KotlinPlatformType.jvm -> CrateType.SystemDynamicLibrary
    KotlinPlatformType.js -> CrateType.SystemDynamicLibrary
    KotlinPlatformType.androidJvm -> CrateType.SystemDynamicLibrary
    KotlinPlatformType.native -> CrateType.SystemStaticLibrary
    KotlinPlatformType.wasm -> CrateType.SystemStaticLibrary
}

private fun ProjectLayout.outputCacheFile(task: Task, propertyName: String): Provider<RegularFile> {
    val trimmedPropertyName = propertyName
        .substringBeforeLast("File")
        .substringBeforeLast("Cache")
    return buildDirectory.file("taskOutputCache/${task.name}/$trimmedPropertyName")
}
