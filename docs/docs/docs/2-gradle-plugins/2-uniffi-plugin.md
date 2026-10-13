---
slug: /gradle-plugins/uniffi
---

# The UniFFI plugin

## Basic usage

> :bulb: We recommend to first read the [UniFFI user guide](https://mozilla.github.io/uniffi-rs/).

The UniFFI plugin is responsible for generating Kotlin bindings from your Rust package. Here is an
example of using the UniFFI plugin to build bindings from the resulting library binary.

```kotlin
import rustine.gradle.Variant
import rustine.gradle.rust.targets.RustAndroidTarget

plugins {
    kotlin("multiplatform")
    id("dev.lidory.rustine.cargo") version "1.0.0"
    id("dev.lidory.rustine.uniffi") version "1.0.0"
}

uniffi {
    // Generate the bindings using library mode.
    generateFromLibrary {
        // The UDL namespace as in the UDL file. Defaults to the library crate name.
        namespace = "my_crate"
        // The Rust target of the build to use to generate the bindings. If unspecified, one of the available builds
        // will be automatically selected.
        build = RustAndroidTarget.Arm64
        // The variant of the build that makes the library to use. If unspecified, the UniFFI plugin automatically picks
        // one.
        variant = Variant.Debug
    }
}
```

The `uniffi` dependency of your crate must have the UniFFI version that the bindgen of your Rustine
version targets, which is UniFFI 0.32.2 for Rustine 1.0.0. The UniFFI plugin installs the bindgen of
the same version as the plugin. See [Versioning](../3-bindgen.md#versioning).

If you want to generate bindings from a UDL file as well, you can specify the path using the
`generateFromUdl {}` block.

```kotlin
uniffi {
    generateFromUdl {
        namespace = "..."
        build = ...
        variant = Variant.Debug
        // The UDL file. Defaults to "${crateDirectory}/src/${crateName}.udl".
        udlFile = layout.projectDirectory.file("rust/src/my_crate.udl")
    }
}
```

If you want to run `ktlint` on the generated bindings set `formatCode` to `true`.

```kotlin
uniffi {
    formatCode = true
}
```

When you use Kotlin targets not supported by the UniFFI plugin like `js()`, `wasmJs()`, or
`wasmWasi()`, the UniFFI plugin generates stubs. This ensures that the Kotlin code is compiled
successfully for all platforms. However, all generated functions except for `RustObject(NoHandle)`
constructors will throw `kotlin.NotImplementedError`. We are trying to support as many platforms as
possible. If you need to target WASM/JS, please use these stubs until WASM/JS support is released.
For `js()` targets, the Cargo plugin still builds the Rust library for `wasm32-unknown-unknown`,
where the futures of async functions and methods must be `Send` since UniFFI 0.32. Futures that are
not `Send`, like the `JsFuture` of `wasm-bindgen-futures`, need the `wasm-unstable-single-threaded`
feature of `uniffi`.

## Using the UniFFI plugin in Android modules

The UniFFI plugin supports the same kinds of Android modules as the Cargo plugin.
See [Requirements](./0-plugins.md#requirements) for details.

In Kotlin Multiplatform modules using `com.android.kotlin.multiplatform.library`, the bindings and
the dependencies required by the bindings are added to Kotlin source sets like `commonMain` and
`androidMain`, as in the other Kotlin Multiplatform targets. As in the other Android modules, the
bindings are generated from the debug Android build by default, even when the release build is
embedded into the library because of
[`androidVariant`](./1-cargo-plugin.md#be-careful-of-the-build-variant-used-during-publishing).
This keeps bindings generation working when the release profile strips symbols, which UniFFI reads
the metadata from.

In `com.android.application` and `com.android.library` modules, which use the built-in Kotlin
support of the Android Gradle plugin, Kotlin source sets are not available. The UniFFI plugin adds
the generated bindings to every Android variant using
the [Variant API](https://developer.android.com/build/extend-agp) of the Android Gradle plugin, and
adds the dependencies required by the bindings, such as JNA, to the `implementation` configuration.
The bindings are copied to the Android variants by the `syncUniffiAndroidBindings` task.

## Configuring Bindgen settings using Gradle DSL

Instead of making `<manifest dir>/uniffi.toml`, you can change the bindgen settings directly inside
the `generateFromLibrary {}` block or the `generateFromUdl {}` block using Gradle DSL.

```kotlin
uniffi {
    generateFromLibrary {
        packageName = "com.example.foo"
        customType("Uuid") {
            typeName = "java.util.UUID"
            lift = "java.util.UUID.fromString({})"
            lower = "{}.toString()"
        }
        usePascalCaseEnumClass = true
        // Leave items out of the bindings, e.g., async primary constructors.
        exclude.add("MyObject.new")
        // Rename items, using their names in Rust or UDL as keys.
        rename.put("MyRecord", "KtRecord")
        rename.put("MyRecord.field", "kotlinField")
        // Keep `var` properties in some records, using their names in the bindings.
        generateImmutableRecords = true
        mutableRecords.add("KtRecord")
    }
}
```

The values in `uniffi.toml` take precedence over the ones set in the Gradle DSL. The items of
`exclude` and `mutableRecords` are added to the ones in `uniffi.toml`, and the entries of `rename`
are added to the `rename` table of `uniffi.toml`, whose entries take precedence. When a custom type
is renamed, pass its new name to `customType()`.

For details about each bindgen setting properties,
see [Bindgen configuration](../3-bindgen.md#bindgen-configuration).

## JNA ProGuard rules for Android

UniFFI on the Rust side generates C-compatible functions that can be called from other
languages. These functions serialize and deserialize the return values and the arguments, thus
acting as the bridge between Rust and other languages, including Kotlin. The functions and the
classes in generated Kotlin bindings internally call these UniFFI-generated functions. On
Kotlin/JVM, it uses [JNA](https://github.com/java-native-access/jna) to call the functions. On
Kotlin/Native, it uses [cinterop](https://kotlinlang.org/docs/native-c-interop.html#bindings).

JNA relies on Java reflection to interact with the Rust library. Some class and method names must be
preserved at runtime for JNA to function correctly. However, when building Android applications in
release mode, R8 is enabled by default for obfuscation, which renames these essential JNA classes
and methods, leading to runtime errors such as `UnsatisfiedLinkError`. While the official JNA
documentation provides the list of
[required ProGuard rules](https://github.com/java-native-access/jna/blob/master/www/FrequentlyAskedQuestions.md#jna-on-android)
to prevent the error, these rules are not included in the official AAR file.

To prevent such runtime issues, the UniFFI plugin generates the necessary ProGuard rules by default.
The rules are generated by the `generateUniffiProguardRules` task
to `build/generated/uniffiProguardRules/generated-proguard-rules.txt`, and added to every Android
variant using the Variant API.

- In `com.android.application` modules, the rules are added to the ProGuard files of each variant,
  which are used when R8 is enabled with `isMinifyEnabled = true`.
- In `com.android.library` modules, the rules are added to both the ProGuard files and the consumer
  ProGuard files of each variant. The consumer ProGuard files are packaged into the `.aar` file, so
  applications using your library keep the classes required by JNA as well.
- In `com.android.kotlin.multiplatform.library` modules, the rules are added in the same way as in
  `com.android.library` modules. As publishing consumer keep rules is opt-in in this plugin, the
  UniFFI plugin also turns on `kotlin { android { optimization { consumerKeepRules.publish } } }`.

If you prefer to manually manage all ProGuard rules and disable this behavior, you can set the
`generateProguardRules` property to `false` in the `uniffi {}` block.

```kotlin
uniffi {
    generateProguardRules = false
}
``` 