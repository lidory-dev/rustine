---
slug: /gradle-plugins/rust
---

# The Rust plugin

## Configuring the Rust toolchain and the linker to use

The Rust plugin is for configuring the Rust toolchain you want to use or linking your Rust library
to your Kotlin project. By default, the plugins think `cargo` and `rustup` are installed in
`~/.cargo/bin` or a directory registered in the `PATH` environment variable, which is okay for
almost everyone.

However, if you have installed `cargo` or `rustup` in another directory, you can provide that
information to the plugin via the `rust {}` block. The information in the `rust {}` block is
automatically passed to the Cargo or the UniFFI plugins.

```kotlin
plugins {
    id("dev.lidory.rustine.rust") version "1.0.0"
}

rust {
    toolchainDirectory = File("/path/to/my/Rust/toolchain")
}
```

The Rust plugin also defines two extension functions `KotlinMultiplatformExtension.hostNativeTarget`
and `KotlinNativeCompilation.useRustUpLinker` and one extension property `Project.rustVersion`.

`hostNativeTarget` can be invoked in `kotlin {}` and adds the Kotlin Native target for the build
host; it invokes `mingwX64` on Windows, `macosX64` or `macosArm64` on macOS, and `linuxX64` or
`linuxArm64` on Linux, though Linux Arm64 build host is not supported by Kotlin/Native yet.

```kotlin
import rustine.gradle.rust.dsl.*

kotlin {
    hostNativeTarget()
}
```

`useRustUpLinker` is for Kotlin Native projects referencing a Rust library but not directly using
Rust. Since Kotlin Native is shipped with an LLVM older than the one shipped with the Rust
toolchain, you may encounter a linker error when building that Kotlin Native project.
`useRustUpLinker` automatically finds the LLVM linker distributed with `rustup`, so you can use this
when your Rust project emits a linker flag that is not supported by the Kotlin Native LLVM linker.

```kotlin
import rustine.gradle.rust.dsl.*

kotlin {
    iosArm64().compilations.getByName("main") {
        useRustUpLinker()
    }
}
```

`rustVersion` retrieves the current Rust version via `rustc --version`.

```kotlin
import rustine.gradle.rust.dsl.*

println(rustVersion.get()) // e.g. 1.90.0
```

## Use the Rust plugin to configure multi-project builds properly

To align with modern
Gradle's [isolated project](https://docs.gradle.org/8.13/userguide/isolated_projects.html)
feature, Rustine
uses [Gradle configurations](https://docs.gradle.org/8.13/userguide/declaring_configurations.html)
to propagate dynamic libraries built with Cargo for JVM targets and UniFFI configurations
for [external types](https://mozilla.github.io/uniffi-rs/0.32/types/remote_ext_types.html). When a project
that uses the Cargo or the UniFFI plugins are referenced by another project, the Rust plugin should
be also applied to that project. If you don't apply the Rust plugin, you might encounter a runtime
error during Android local unit tests. Simply apply the Rust plugin to resolve these issues.

```kotlin
plugins {
    id("dev.lidory.rustine.rust") version "1.0.0"
}
```

The Rust library built for the build host is added to the Android local unit tests of the Cargo
project and the projects depending on it as follows.

- In `com.android.application` and `com.android.library` modules, the library is added to the
  local unit tests of each build type, such as `testDebugUnitTest`. By default, AGP 9 enables local
  unit tests only for the `debug` build type.
- In Kotlin Multiplatform modules using `com.android.kotlin.multiplatform.library`, the library is
  added to host tests, whose source set is `androidHostTest`. Host tests are not enabled by default,
  so enable them using `withHostTest {}`.

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("dev.lidory.rustine.rust") version "1.0.0"
}

kotlin {
    android {
        // ...
        withHostTest {}
    }
    sourceSets {
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```