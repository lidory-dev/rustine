/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.uniffi.dsl

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.Variant
import rustine.gradle.rust.targets.RustTarget
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

sealed class BindingsGeneration(internal val project: Project) {
    /**
     * The UDL namespace. Defaults to `"$libraryCrateName"`.
     */
    abstract val namespace: Property<String>

    /**
     * The Rust target of the build to use to generate bindings. If unspecified, one of the available builds will be
     * automatically selected.
     */
    abstract val build: Property<RustTarget>

    /**
     * The variant of the build to use to generate bindings. If unspecified, one of the available variants will be
     * automatically selected.
     */
    abstract val variant: Property<Variant>

    /**
     * Path to the optional uniffi config file.
     * If not provided, uniffi-bindgen will try to guess it.
     */
    abstract val config: RegularFileProperty

    /**
     * The package name used in the generated bindings. Defaults to `"uniffi.$namespace"`.
     */
    abstract val packageName: Property<String>

    /**
     * The name of the resulting dynamic library without the prefix (e.g. `lib`) and the file
     * extension. When the bindings are generated from a dynamic library, the value of this property
     * defaults to the library's name. When a static library or a UDL file is used, it is set to
     * `uniffi_<namespace>`. When the `crate-type` field of the Cargo manifest contains `"cdylib"`,
     * the UniFFI plugin will give priority to the dynamic library over the static library.
     */
    abstract val cdylibName: Property<String>

    internal abstract val customTypes: MapProperty<String, CustomType>

    /**
     * Defines a new custom type. See [the documentation](https://mozilla.github.io/uniffi-rs/0.32/types/custom_types.html#custom-types-in-the-bindings-code).
     * When the custom type is renamed with [rename], [name] is its new name.
     */
    fun customType(name: String, configure: Action<CustomType> = Action { }) {
        customTypes.put(
            name,
            project.objects.newInstance<CustomType>().apply { configure(this) }
        )
    }

    /**
     * When `true`, generated data classes has `val` properties instead of `var`.
     */
    abstract val generateImmutableRecords: Property<Boolean>

    /**
     * The records whose data classes keep `var` properties when [generateImmutableRecords] is
     * `true`, by their names in the bindings (after [rename]). Added to the `mutable_records` list
     * of the config file.
     */
    abstract val mutableRecords: SetProperty<String>

    /**
     * The functions, types, constructors and methods to leave out of the bindings, such as
     * `"my_function"`, `"MyRecord"`, `"MyObject.new"` or `"MyObject.my_method"`. Async primary
     * constructors must be excluded, as Kotlin constructors can't suspend. Added to the `exclude`
     * list of the config file.
     */
    abstract val exclude: ListProperty<String>

    /**
     * New names for the items of the crate, keyed by their UniFFI names: `"MyRecord"`,
     * `"MyRecord.field"`, `"MyEnum.Variant"`, `"MyEnum.Variant.field"`, `"my_function"`,
     * `"my_function.arg"`, `"MyObject.my_method"`, `"MyObject.my_method.arg"`, and so on. Added
     * to the `rename` table of the config file, whose entries take precedence.
     */
    abstract val rename: MapProperty<String, String>

    /**
     * When `true`, the library checksums are not checked during initialization, making the process
     * slightly faster. This may be problematic when you mix multiple libraries with varying UniFFI
     * versions.
     */
    abstract val omitChecksums: Property<Boolean>

    /**
     * When `true`, `com.sun.jna.internal.Cleaner` will be used instead of
     * `android.system.SystemCleaner` or `java.lang.ref.Cleaner`. Defaults to `false`. Consider
     * changing this option when your project targets JVM 1.8.
     */
    abstract val disableJavaCleaner: Property<Boolean>

    /**
     * When `true`, enum classes will use PascalCase instead of UPPER_SNAKE_CASE.
     */
    abstract val usePascalCaseEnumClass: Property<Boolean>

    /**
     * When `true`, the generated bindings will use JNA interface mapping instead of
     * [direct mapping](https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md).
     */
    // TODO: Remove InternalRustineGradleApi in 0.4.0
    @InternalRustineGradleApi
    abstract val enableJnaInterfaceMapping: Property<Boolean>
}

abstract class BindingsGenerationFromLibrary @Inject internal constructor(project: Project) :
    BindingsGeneration(project)

abstract class BindingsGenerationFromUdl @Inject internal constructor(project: Project) :
    BindingsGeneration(project) {
    /**
     * The UDL file. Defaults to `"${crateDirectory}/src/${crateName}.udl"`.
     */
    abstract val udlFile: RegularFileProperty
}
