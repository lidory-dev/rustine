---
slug: /tutorial
---

# Getting started (Android & iOS)

Welcome to Gobley! Gobley is a set of libraries and tools that help you mix Rust and Kotlin, so you
can focus on implementing your business logic. In this tutorial, you will learn how to embed Rust
code into your Kotlin Multiplatform project using Gobley. If you have trouble setting up your
project, please create a question
in [GitHub Discussions](https://github.com/gobley/gobley/discussions).

## Prerequisites

To develop with Kotlin Multiplatform, you need:

1. An IDE for Android development, such as [Android Studio](https://developer.android.com/studio)
   or [IntelliJ IDEA](https://www.jetbrains.com/idea/download).
2. (Optional) The IDE for iOS development: [Xcode](https://developer.apple.com/xcode).

You'll spend much time with Android Studio or IntelliJ IDEA to code in Kotlin. Xcode is required to
build iOS apps.

To develop in Rust, you need:

1. A [Rust toolchain](https://www.rust-lang.org/tools/install).
2. An IDE for Rust. Several options are available:
    - [Visual Studio Code](https://code.visualstudio.com/) with [
      `rust-analyzer`](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer).
    - [RustRover](https://www.jetbrains.com/rust).
    - [IntelliJ IDEA **Ultimate**](https://www.jetbrains.com/idea/download) with
      the [Rust plugin](https://www.jetbrains.com/help/idea/rust-plugin.html).
    - Other editors like Vim. Still, using `rust-analyzer` is recommended.

You'll use a separate IDE for Rust development. The only way to use a single IDE is IntelliJ IDEA
Ultimate, but it needs a paid subscription.

## Creating a Kotlin Multiplatform project

Let's first create a new Kotlin Multiplatform project. Please read
the [official documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-create-first-app.html)
for more details about how Kotlin Multiplatform works.

1. Visit the [Kotlin Multiplatform Wizard](https://kmp.jetbrains.com) website.
2. On the **New Project** tab, set the **Project Name** and **Project ID** to names you want. Let's
   use `MyFirstGobleyProject` and `dev.gobley.myfirstproject`.
3. Make sure you selected **Android** and **iOS**.
4. Select **Share UI (with Compose Multiplatform UI framework)**. You can use Gobley without Compose
   Multiplatform, but let's focus on how to mix Rust and Kotlin this time.
5. Click the **Download** button and extract the downloaded `.zip` file.
6. Launch Android Studio or IntelliJ IDEA, and open the extracted folder.

![The Android Studio screen after opening the downloaded project](./0-tutorial/img-1.png)

The generated project uses the Android Gradle plugin (AGP) 9. The code shared between Android and
iOS is located in the `shared` module, which is a Kotlin Multiplatform library using the
`com.android.kotlin.multiplatform.library` plugin. The Android app is located in a separate
`androidApp` module depending on `shared`. We'll add Rust to the `shared` module.

> :bulb: The screenshots in this tutorial were taken with an older version of the wizard, which
> generated a single `composeApp` module containing both the shared code and the Android app. If
> your project still has that layout, please migrate it to AGP 9 first. See
> [Migrating to AGP 9](../docs/2-gradle-plugins/0-plugins.md#migrating-to-agp-9) for details.

## Adding Rust to your Kotlin Multiplatform project

Let's add a Cargo package to the Kotlin Multiplatform project.

1. Make sure you installed `cargo` and `rustup`.
2. Open the **Terminal** menu in Android Studio or IntelliJ IDEA. Run:

   ```shell
   cargo init --lib --name shared --vcs none shared
   ```

   Let's see what this command does:

    - `cargo init` will create a new Cargo package in `shared`.
    - `--lib` means that Cargo will create a **library** crate.
    - `--name shared` means that the name of the resulting Cargo package will be `shared`. If your
      module has a camelCase name like `sharedUI`, use a kebab-case name like `shared-ui`. Cargo
      doesn't like camelCase.
    - `--vcs none` means you don't want to generate `.git` or `.gitignore`.
    - `shared` is the directory where we'll code both in Rust and Kotlin.

   After running this command, the following files should be generated:

    - `shared/Cargo.toml`: This file contains the definition of the Cargo package.
    - `shared/src/lib.rs`: The Rust source code file.

3. Add `**/target/` to `.gitignore`.

   > :bulb: This is the folder where Cargo stores the build intermediate files and the final Rust
   > library.

4. (Optional) Move `shared/src/lib.rs` to `shared/src/commonMain/rust/lib.rs`.

   ![The Android Studio screen after moving lib.rs](./0-tutorial/img-2.png)

   > :bulb: When you use CMake in an Android project, C++ source files are usually located in
   > `src/main/cpp`. This procedure imitates that behavior. It feels more organized, isn't it?

5. Modify `shared/Cargo.toml` like the following.

   ```toml
   # This part is already added by cargo init.
   [package]
   name = "shared"
   version = "0.1.0"
   edition = "2024"

   [dependencies]
   # We need to add this.
   uniffi = "0.29.4"

   # This as well.
   [lib]
   crate-type = ["cdylib", "staticlib"]
   # Put this only if you moved lib.rs.
   path = "src/commonMain/rust/lib.rs"
   ```

   Let's see what each part of the modification does:

    - `uniffi = "0.29.4"` downloads UniFFI, the library used to generate the Kotlin code (the "
      bindings") that calls the Rust library.
    - `crate-type = ["cdylib", "staticlib"]` will make Cargo generate a `.a` (static library) file
      and a `.so`/`.dylib` (dynamic library) file that can be used by Gobley. Gobley uses the static
      library file when building for iOS, and the dynamic library file for Android.
    - `path = "src/commonMain/rust/lib.rs"` designates the path to the Rust source code.

6. Modify `shared/build.gradle.kts` like the following.

   ```kotlin
   plugins {
       // Other plugins here, including com.android.kotlin.multiplatform.library
       id("dev.gobley.cargo") version "0.3.7"
       id("dev.gobley.uniffi") version "0.3.7"
       kotlin("plugin.atomicfu") version libs.versions.kotlin
   }
   ```

   > :exclamation: Apply the Gobley plugins to the `shared` module, not to the `androidApp`
   > module. The Rust library embedded into `shared` is included in the Android app through the
   > dependency on `shared`.
   >
   > :exclamation: AGP 9.1 support was added after Gobley 0.3.7. Please use a version of Gobley
   > that supports AGP 9.1. See [Requirements](../docs/2-gradle-plugins/0-plugins.md#requirements).
   >
   > :exclamation: Consider managing plugin dependencies using
   > [version catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html).
   >
   > :bulb: All Gradle plugins in Gobley are published in `mavenCentral()`.

   Let's see what each plugin does:

    - `dev.gobley.cargo` builds and links the Rust library to the Kotlin application.
    - `dev.gobley.uniffi` generates the bindings using UniFFI. You can change the package name of
      the bindings inside the `uniffi {}` block.
    - `org.jetbrains.kotlin.plugin.atomicfu` is to use atomic types used by the bindings.

   > :warning: As `com.android.kotlin.multiplatform.library` doesn't have build types, the Cargo
   > plugin embeds the debug build of the Rust library into `shared` by default, even when you
   > build a release version of the Android app. Before releasing your app,
   > read [Be careful of the build variant used during publishing](../docs/2-gradle-plugins/1-cargo-plugin.md#be-careful-of-the-build-variant-used-during-publishing).

We're now ready to code both in Rust and Kotlin!

> :question: If you hit the **Sync Now** button at this point, you'll get a
> `Crate shared not found in libshared.so` error. That's because you haven't used
> `uniffi::setup_scaffolding!();` inside the Rust code. If you encountered such an error, go
> straight to the next step. 

![The Android Studio screen after configuring the Gradle plugins](./0-tutorial/img-3.png)

## Defining and exposing Rust types and functions

Now is the time to code in Rust. Open `shared` in Visual Studio Code.

```shell
code ./shared
# or on macOS
open -a "Visual Studio Code" ./shared
```

Once `rust-analyzer` is ready, you can see highlightings and inlay hints in the code editor. Modify
`src/commonMain/rust/lib.rs` as follows.

```rust
/// This exports this Rust function to the Kotlin side.
#[uniffi::export]
fn add(lhs: i32, rhs: i32) -> i32 {
    lhs + rhs
}

/// This exports this Rust type to the Kotlin side.
#[derive(uniffi::Object)]
struct Greeter {
    greeting: String
}

#[uniffi::export]
impl Greeter {
   /// Define a constructor to be used on the Kotlin side.
   #[uniffi::constructor]
   fn new(greeting: String) -> Self {
      Self { greeting }
   }

   /// Define a method to be used on the Kotlin side.
   fn greet(&self, name: String) -> String {
      format!("{}, {name}!", self.greeting)
   }
}

// This generates extra Rust code required by UniFFI.
uniffi::setup_scaffolding!();
```

![The Visual Studio Code screen after modifying lib.rs](./0-tutorial/img-4.png)

By just applying `#[uniffi::export]` or similar macros, the functions and the types become available
on the Kotlin side. Go back to Android Studio and press the **Sync Now** button.

![The Android Studio screen before pressing the Sync Now button](./0-tutorial/img-5.png)

Cargo will start
building the Rust library inside Android Studio. After the build completes, open `App.kt` in the
`commonMain` source set of the `shared` module (e.g.,
`shared/src/commonMain/kotlin/dev/gobley/myfirstproject/App.kt`). Add the following lines to the
part you prefer:

```kotlin
Column {
    Text("Addition using Rust: 2 + 3 = ${uniffi.shared.add(2, 3)}")
    val greeting = remember { uniffi.shared.Greeter("Hello") }
    Text(greeting.greet("Rust"))
    DisposableEffect(greeting) {
        onDispose {
            greeting.close()
        }
    }
}
```

![The Android studio screen after modifying the composable function](./0-tutorial/img-6.png)

`Greeter` and `add` exported on the Rust side are accessible on the Kotlin side! Doc-comments are
also available, so you don't have to write the same description twice.

Let's run the Android app. Select the `androidApp` run configuration and hit the Run button on the
upper right corner of the screen. You can see the Rust library is included in the final app
automatically, and the app communicates with Rust without any issues.

Let's run the iOS app as well. Open `iosApp/iosApp.xcodeproj` in Xcode. Run:

```shell
open -a "Xcode" ./iosApp/iosApp.xcodeproj
```

![The Xcode screen after launching](./0-tutorial/img-7.png)

Since the part connecting Swift and Kotlin is handled by the Kotlin Multiplatform Wizard, we can
just hit the Run button as well on Xcode.

| Android                                                              | iOS                                                               |
|----------------------------------------------------------------------|-------------------------------------------------------------------|
| ![The app screen inside an Android emulator](./0-tutorial/img-8.png) | ![The app screen inside an iOS simulator](./0-tutorial/img-9.png) |

## Next step

And that's how you embed Rust into your Kotlin project. If you need more detailed information about
Gobley, please read the [documentation](../docs/0-overview.md).