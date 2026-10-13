---
slug: /bindgen
---

# The Bindgen

The bindings generator (the "bindgen") is the program that generates Kotlin source codes connecting
your Kotlin code to your Rust code. In most cases,
[the UniFFI Gradle plugin](./2-gradle-plugins/2-uniffi-plugin.md) handles the bindings generation,
so you don't have to know all the details of the bindgen. Still, you can directly use this bindgen
if you have more complicated build system.

The minimum Rust version required to install `rustine-uniffi-bindgen` is `1.88`, which UniFFI 0.32
and its dependencies require. Newer Rust versions should also work fine. The source code of the
bindgen for Kotlin Multiplatform is in [
`crates/rustine-uniffi-bindgen`](https://github.com/lidory-dev/rustine/tree/main/crates/rustine-uniffi-bindgen).
See comments in [
`crates/rustine-uniffi-bindgen/src/main.rs`](https://github.com/lidory-dev/rustine/blob/main/crates/rustine-uniffi-bindgen/src/main.rs) or
[
`BuildUniffiBindingsTask.kt`](https://github.com/lidory-dev/rustine/blob/main/build-logic/rustine-gradle-uniffi/src/main/kotlin/tasks/BuildUniffiBindingsTask.kt)
to see how to use the bindgen from the command line.

To install the bindgen, run:

```shell
cargo install --locked --bin rustine-uniffi-bindgen rustine-uniffi-bindgen
```

`--locked` builds the bindgen with the dependency versions in its `Cargo.lock` file, which Rustine is
tested with and which support Rust 1.88. The UniFFI Gradle plugin installs the bindgen the same way.

To generate the bindings of the crates in a library, run:

```shell
rustine-uniffi-bindgen --library --out-dir <output-directory> <path-to-library-file>
```

Pass `--crate <crate-name>` to generate the bindings of a single crate. To generate the bindings from
a UDL file, run:

```shell
rustine-uniffi-bindgen --lib-file <path-to-library-file> --out-dir <output-directory> --crate <crate-name> <path-to-udl-file>
```

The `--config <path>` option takes a file with the same settings as `uniffi.toml`
(see [Bindgen configuration](#bindgen-configuration)), whose values take precedence over the config
of every crate. Unlike the `--config` option of `uniffi-bindgen` in UniFFI 0.32, it is not a global
config file. In library mode, its `exclude`, `rename`, and `mutable_records` settings only apply to
the crate selected with `--crate`, or to the only crate of the library, as they name the items of a
single crate. Use `--crate-configs <crate-name>=<path>` to give each crate its own config.

If you want to use the bindgen in your own Cargo build script, please read the
["Generating foreign-language bindings" part](https://mozilla.github.io/uniffi-rs/0.32/tutorial/foreign_language_bindings.html)
in the official UniFFI documentation.

When the bindings are generated correctly, it has a directory structure like the following.

```
<output directory>
├── androidMain
│   └── kotlin
│       └── <package directories>
│           └── <namespace name>.android.kt
├── commonMain
│   └── kotlin
│       └── <package directories>
│           └── <namespace name>.common.kt
├── jvmMain
│   └── kotlin
│       └── <package directories>
│           └── <namespace name>.jvm.kt
├── nativeInterop
│   └── cinterop
│       └── headers
│           └── <namespace name>
│               └── <namespace name>.h
├── nativeMain
│   └── kotlin
│       └── <package directories>
│           └── <namespace name>.native.kt
└── stubMain
    └── kotlin
        └── <package directories>
            └── <namespace name>.stub.kt
```

The package directories follow the package name, e.g., `uniffi/<namespace name>` for the default
package name `uniffi.<namespace name>`.

## Bindgen configuration

Various settings used by the bindgen can be configured in `<manifest dir>/uniffi.toml`, or the
`uniffi {}` block in Gradle scripts. To learn more about configuring UniFFI settings using Gradle,
see [Configuring Bindgen settings using Gradle DSL](./2-gradle-plugins/2-uniffi-plugin.md#configuring-bindgen-settings-using-gradle-dsl).
For more details about each configuration, see [
`crates/rustine-uniffi-bindgen/src/gen_kotlin_multiplatform/mod.rs`](https://github.com/lidory-dev/rustine/blob/main/crates/rustine-uniffi-bindgen/src/gen_kotlin_multiplatform/mod.rs)
or [
`Config.kt`](https://github.com/lidory-dev/rustine/tree/main/build-logic/rustine-gradle-uniffi/src/main/kotlin/Config.kt).

| Configuration Name                     | Type         | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
|----------------------------------------|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `package_name`                         | String       | The Kotlin package name to use. Defaults to `uniffi.<namespace name>`.                                                                                                                                                                                                                                                                                                                                                                                           |
| `cdylib_name`                          | String       | The name of the resulting dynamic library without the prefix (e.g. `lib`) and the file extension. When the bindings are generated from a dynamic library, the value of this property defaults to the library's name. When a static library or a UDL file is used, it is set to `uniffi_<namespace>`. When the `crate-type` field of the Cargo manifest contains `"cdylib"`, the UniFFI plugin will give priority to the dynamic library over the static library. |
| `kotlin_multiplatform`                 | Boolean      | When `false`, expect/actual declarations are not used.                                                                                                                                                                                                                                                                                                                                                                                                           |
| `kotlin_targets`                       | String Array | The list of names of Kotlin targets of the bindings to generate. Possible values are: `jvm`, `android`, `native`, and `stub`.                                                                                                                                                                                                                                                                                                                                    |
| `generate_immutable_records`           | Boolean      | When `true`, generated data classes has `val` properties instead of `var`.                                                                                                                                                                                                                                                                                                                                                                                       |
| `mutable_records`                      | String Array | The names of the records whose data classes keep `var` properties when `generate_immutable_records` is `true`. Use the names of the records in the bindings, after `rename`.                                                                                                                                                                                                                                                                                     |
| `omit_checksums`                       | Boolean      | When `true`, the library checksums are not checked during initialization, making the process slightly faster. This may be problematic if there is a mismatch between libraries used during binding generation and runtime.                                                                                                                                                                                                                                       |
| `custom_types`                         | Table        | See [the documentation](https://mozilla.github.io/uniffi-rs/0.32/types/custom_types.html#custom-types-in-the-bindings-code). The keys are the names of the custom types in the bindings: when a custom type is renamed with `rename`, use its new name.                                                                                                                                                                                                          |
| `rename`                               | Table        | New names for the items of the crate, such as `MyRecord = "KtRecord"` or `"MyRecord.field" = "kotlinField"`. See [Renaming and excluding items](#renaming-and-excluding-items).                                                                                                                                                                                                                                                                                  |
| `exclude`                              | String Array | The functions, types, constructors, and methods to leave out of the bindings, such as `"my_function"`, `"MyRecord"`, `"MyObject.new"`, or `"MyObject.my_method"`. See [Renaming and excluding items](#renaming-and-excluding-items).                                                                                                                                                                                                                             |
| `kotlin_target_version`                | String       | The Kotlin version used by your project. Newer syntax will be used (e.g. `data object` or `Enum.entries`) when the compiler of the specified version supports. This is automatically set to the Kotlin Gradle plugin version by the UniFFI Gradle plugin.                                                                                                                                                                                                        |
| `disable_java_cleaner`                 | Boolean      | When `true`, `com.sun.jna.internal.Cleaner` will be used instead of `android.system.SystemCleaner` or `java.lang.ref.Cleaner`. Defaults to `false`. Consider changing this option when your project targets JVM 1.8.                                                                                                                                                                                                                                             |
| `generate_serializable_types`          | Boolean      | When `true`, data classes will be annotated with `@kotlinx.serialization.Serializable` when possible. This is automatically set to `true` by the UniFFI Gradle plugin when your Kotlin project uses KotlinX Serialization.                                                                                                                                                                                                                                       |
| `use_pascal_case_enum_class`           | Boolean      | When `true`, enum classes will use PascalCase instead of UPPER_SNAKE_CASE.                                                                                                                                                                                                                                                                                                                                                                                       |
| `jvm_dynamic_library_dependencies`     | String Array | The list of dynamic libraries required by your Rust library on Desktop JVM targets without the prefix and the file extension. Use this if your project depends on an external dynamic library. Ensure the dependent dynamic libraries have the correct install names or SONAMEs on macOS and Linux.                                                                                                                                                              |
| `android_dynamic_library_dependencies` | String Array | The list of dynamic libraries required by your Rust library on Android without the prefix and the file extension.                                                                                                                                                                                                                                                                                                                                                |
| `dynamic_library_dependencies`         | String Array | The list of dynamic libraries required by your Rust library on both Desktop JVM targets and Android targets.                                                                                                                                                                                                                                                                                                                                                     |

## Renaming and excluding items

UniFFI 0.32 can rename items and leave them out of the bindings using `uniffi.toml`.
See [Renaming and excluding items](https://mozilla.github.io/uniffi-rs/0.32/renaming.html) in the
UniFFI user guide. Like the other settings of the bindgen, `rename`, `exclude`, and
`mutable_records` are at the top level of `uniffi.toml`, not in a `[bindings.kotlin]` table.

```toml
# The items to leave out of the bindings, with their names in Rust or UDL.
exclude = ["function_to_exclude", "MyObject.new", "MyObject.method_to_exclude"]

# The keys are the names of the items in Rust or UDL. Members are separated with dots.
[rename]
my_function = "ktFunction"
"my_function.arg" = "kotlinArg"
MyRecord = "KtRecord"
"MyRecord.field" = "kotlinField"
MyEnum = "KtEnum"
"MyEnum.Variant" = "KotlinVariant"
"MyEnum.Variant.field" = "kotlinField"
MyObject = "KtObject"
"MyObject.method" = "kotlinMethod"
"MyObject.method.arg" = "kotlinArg"
Url = "KtUrl"

# The configs of custom types use the new names.
[custom_types.KtUrl]
type_name = "Url"
imports = ["io.ktor.http.Url"]
lift = "Url({})"
lower = "{}.toString()"
```

The FFI functions and the checksums keep the Rust names, so renamed items keep passing the checksum
checks, also when they are defined in UDL. In library mode, the items of a crate are renamed with the
`rename` table of that crate's config, also where other crates use them. The UniFFI plugin passes
the config of a crate to the projects using its types with `--crate-configs`.

Renaming the following items is not supported, and the bindings generation fails with an error
naming the item:

- Callback interfaces and traits that Kotlin can implement.
- Records, enums, and errors that have methods or export uniffi traits (`Display`, `Debug`, `Eq`,
  `Hash`, or `Ord`), and objects that export `Eq` or `Ord`.
- The methods of records, enums, and errors, and their arguments.
- The other types used by the methods of records, enums, and errors, by the return values and errors
  of the methods of callback interfaces, and by the errors of constructors.
- Enum variants used by default values.

Kotlin constructors can't suspend, so the bindings generation fails for an async primary
constructor, as in UniFFI 0.32:

```
Async primary constructors not supported but MyObject has one. Add "MyObject.new" to the `exclude` list in uniffi.toml.
```

Add the constructor to `exclude`, using the names of the object and the constructor in Rust or UDL,
and export an async constructor with another name, which becomes a `suspend` function of the
companion object of the class.

## The generated bindings

The bindings follow the Kotlin bindings of UniFFI, and have the same API in common code and on every
platform. The following sections describe what differs from the Kotlin bindings of UniFFI, and the
changes in Rustine 1.0.0, which moved from UniFFI 0.29.4 to UniFFI 0.32.2.

### Objects

- Objects cross the FFI as handles. Use the `NoHandle` constructor, which was named `NoPointer`
  before Rustine 1.0.0, to create a fake object without a Rust object, for example to subclass it in
  tests. A fake object is never freed in Rust. Unlike in the Kotlin bindings of UniFFI, a fake object
  of a trait that Kotlin can implement can be passed to Rust, which calls its methods like those of
  a Kotlin implementation.
- The `uniffiIsDestroyed` property tells whether `destroy()` or `close()` has been called.
- Objects exporting the `Ord` trait implement `Comparable` and `compareTo()`. Objects exporting
  `Debug` but not `Display` use `Debug` for `toString()`.

### Records, enums, and errors

- Since UniFFI 0.31, the methods of records and enums can be exported with `#[uniffi::export]`.
  They are members of the Kotlin classes: the data class of a record, the `enum class` of an enum
  without fields, and the sealed class of an enum with fields or of an error. Records without fields,
  which are Kotlin `object`s, have methods too. Constructors of records and enums are ignored, like
  in UniFFI.
- Since UniFFI 0.30, records, enums, and errors can export the `Display`, `Debug`, `Eq`, `Hash`, and
  `Ord` traits like objects, for example with `#[uniffi::export(Debug, Eq, Hash, Ord)]`. The classes
  override `toString()` (from `Display`, or from `Debug` otherwise), `equals()`, `hashCode()`, and
  `compareTo()`, and implement `Comparable` for `Ord`. Enums with fields and errors implement them
  in their sealed class, as `final` members for enums. Records without fields only implement
  `toString()`.
- Enums without fields can only export `Display` and `Debug`, as Kotlin `enum class`es can't
  override the other members. Flat errors (`#[uniffi(flat_error)]`, or `[Error] enum`s without
  fields in UDL) can't be passed back to Rust, so they implement no traits. The bindings generation
  fails for a flat enum exporting `Eq`, `Hash`, or `Ord`, and for a flat error with methods or
  exporting `Ord`.
- Exporting `Eq` without `Hash` overrides `equals()` only, so `hashCode()` stays the one of Kotlin:
  computed from the fields for records and the variants of enums, and the identity hash code for
  errors and objects. Export `Hash` too when such values are used as keys of hash maps or in hash
  sets, unless the `Eq` implementation compares all fields of a record or an enum.
- Since UniFFI 0.30, a custom type over an enum or an object can be thrown as an error. Its Kotlin
  type is an alias of the class of its builtin type, so the builtin type must be used as an error
  too, for example by a function throwing it. The bindings generation fails otherwise.

### Byte slices

`&[u8]` arguments, and `[ByRef] bytes` arguments in UDL, which map to `&[u8]` in Rust since UniFFI
0.32, take a `ByteArray` in Kotlin. Since UniFFI 0.32, Rust borrows the bytes for the duration of the
call instead of reading them from a copy in a `RustBuffer`.

- On Kotlin/Native, the array is pinned and passed without copying.
- On the JVM and Android, the bytes are copied to native memory, which is freed after the call, as
  JNA can't pass a pointer to a JVM array inside a structure. Freeing the memory requires JNA 5.12
  or higher. The UniFFI plugin adds JNA 5.18.1 by default.

The Kotlin bindings of UniFFI take a direct `java.nio.ByteBuffer` instead, which doesn't exist in
common code. Byte slices are not supported by async functions, methods, and constructors, and by the
methods of callback interfaces and traits that Kotlin can implement. The bindings generation fails
for them. Take a `Vec<u8>` (`bytes` in UDL) instead.

### Checks during initialization

When the bindings are initialized, they check that the Rust library uses the same version of the
UniFFI contract, and that the checksums of its API match, unless `omit_checksums` is `true`. Since
Rustine 1.0.0, the Kotlin/Native bindings check them too, like the JVM and Android bindings. A
mismatch throws a `RuntimeException` like
`UniFFI contract version mismatch: try cleaning and rebuilding your project`.

### Limitations

- In UDL mode, which `generateFromUdl {}` uses, the bindgen only loads the crate of the UDL file,
  like UniFFI does. The bindings generation fails for a crate using the types of another UniFFI
  crate, e.g., with `unresolved module path other_crate`. Generate the bindings from the library
  instead, which loads every crate of the library: the types of other crates are renamed with the
  `rename` tables of their crates, and the errors of other crates that a crate only uses as fields
  or arguments are known as errors
  ([mozilla/uniffi-rs#2641](https://github.com/mozilla/uniffi-rs/pull/2641)).
- On `wasm32` targets, which the Cargo plugin builds for Kotlin/JS targets, the futures of async
  functions and methods must be `Send` since UniFFI 0.32, unless the crate enables the
  `wasm-unstable-single-threaded` feature of `uniffi`.

## Versioning

The Rustine bindgen is versioned separately from UniFFI. UniFFI follows the
[SemVer rules from the Cargo Book](https://doc.rust-lang.org/cargo/reference/resolver.html#semver-compatibility)
which states "Versions are considered compatible if their left-most non-zero major/minor/patch
component is the same". A breaking change is any modification to the Kotlin Multiplatform bindings
that demands the consumer of the bindings to make corresponding changes to their code to ensure that
the bindings continue to function properly. Gobley released the bindgen with major version 0, as it
was unclear how stable the generated bindings were going to be between versions, and most changes
bumped the minor version. Rustine's first release is 1.0.0.

To ensure consistent feature set across external binding generators, `rustine-uniffi-bindgen`
targets a specific `uniffi-rs` version. A consumer using these bindings or any other external
bindings (for example, [Go bindings](https://github.com/NordSecurity/uniffi-bindgen-go/) or
[C# bindings](https://github.com/NordSecurity/uniffi-bindgen-cs)) expects the same features to be
available across multiple bindings generators. This means that the consumer should choose external
binding generator versions such that each generator targets the same `uniffi-rs` version.

Here is how `rustine-uniffi-bindgen` versions are tied to `uniffi-rs`. Versions up to v0.3.7 were
released as `gobley-uniffi-bindgen` by [Gobley](https://github.com/gobley/gobley), which Rustine was
forked from.

| Version        | UniFFI version |
|----------------|----------------|
| v0.1.0         | v0.28.3        |
| v0.2.0         | v0.28.3        |
| v0.3.0         | v0.29.3        |
| v0.3.1         | v0.29.4        |
| v0.3.2         | v0.29.4        |
| v0.3.3         | v0.29.4        |
| v0.3.4         | v0.29.4        |
| v0.3.5         | v0.29.4        |
| v0.3.6         | v0.29.4        |
| v0.3.7         | v0.29.4        |
| v1.0.0         | v0.32.2        |

The `uniffi` dependency of your crates must have the UniFFI version that the bindgen targets.
Otherwise, the bindings generation fails, for example with `Unexpected metadata type code`, or the
bindings fail during initialization with a contract version or checksum mismatch.
