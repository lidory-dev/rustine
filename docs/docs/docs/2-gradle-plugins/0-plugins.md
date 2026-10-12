---
slug: /gradle-plugins
---

# Using the Gradle plugins

This project contains three Gradle plugins:

- [The Cargo plugin (`dev.lidory.rustine.cargo`)](./1-cargo-plugin.md)
- [The UniFFI plugin (`dev.lidory.rustine.uniffi`)](./2-uniffi-plugin.md)
- [The Rust plugin (`dev.lidory.rustine.rust`)](./3-rust-plugin.md)

These plugins are published in Maven Central. In your `settings.gradle.kts`, put `mavenCentral()` in
the `pluginManagement {}` block.

```
pluginManagement {
    repositories {
        mavenCentral()
    }
}
```

If you're using multi-project builds, please
read [The Rust plugin](./3-rust-plugin.md).

## Requirements

The plugins require Gradle 8.11 or higher and JDK 17 or higher. To use the plugins in Android
projects, you need:

- Android Gradle Plugin (AGP) 9.1 or higher. AGP 8 is not supported anymore. If your project still
  uses AGP 8, please use [Gobley](https://github.com/gobley/gobley) 0.3.7 or older.
- Gradle 9.3.1 or higher, which is required by AGP 9.1.
- JDK 17 or higher.
- Kotlin 2.4.0 or higher is recommended, as it is the first version of the Kotlin Gradle plugin
  officially supporting AGP 9.1.

AGP 9.1 uses Android SDK Build-Tools 36.0.0 and NDK 28.2.13676358 (r28c) by default.

The plugins support the following two kinds of Android modules. The plugins can be applied before
or after the Android Gradle plugin.

- `com.android.application` or `com.android.library` modules using
  the [built-in Kotlin support](https://developer.android.com/build/migrate-to-built-in-kotlin) of
  AGP. Don't apply the `org.jetbrains.kotlin.android` plugin.

  ```kotlin
  plugins {
      id("com.android.application") // or id("com.android.library")
      id("dev.lidory.rustine.cargo")
      id("dev.lidory.rustine.uniffi")
      kotlin("plugin.atomicfu")
  }
  ```

- Kotlin Multiplatform modules using
  the [Android-KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
  (`com.android.kotlin.multiplatform.library`). The Android target is configured in the
  `kotlin { android {} }` block.

  ```kotlin
  plugins {
      kotlin("multiplatform")
      id("com.android.kotlin.multiplatform.library")
      id("dev.lidory.rustine.cargo")
      id("dev.lidory.rustine.uniffi")
      kotlin("plugin.atomicfu")
  }

  kotlin {
      android {
          namespace = "com.example.mylibrary"
          compileSdk = 36
          minSdk = 24
      }
  }
  ```

  The experimental `android.kmp.use.jvm.platform.type=true` Gradle property is not supported.

## Migrating to AGP 9

AGP 9 no longer supports some of the project setups that Gobley 0.3.7 and older supported. Please
read the official migration guides
from [Google](https://developer.android.com/build/migrate-to-built-in-kotlin)
and [JetBrains](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)
first. Rustine-specific changes are as follows.

- **Remove `org.jetbrains.kotlin.android`.** AGP 9 compiles Kotlin by itself and rejects the
  `org.jetbrains.kotlin.android` plugin. Rustine doesn't support the `android.builtInKotlin=false`
  and `android.newDsl=false` opt-out properties.
- **Use AGP configurations instead of Kotlin source sets in Android modules.** Built-in Kotlin
  doesn't provide `kotlin.sourceSets`. Declare dependencies in the top-level `dependencies {}` block
  using configurations like `testImplementation` or `androidTestImplementation`.

  ```kotlin
  // Before
  kotlin {
      sourceSets {
          getByName("test") {
              dependencies {
                  implementation(libs.junit)
              }
          }
      }
  }

  // After
  dependencies {
      testImplementation(libs.junit)
  }
  ```

- **Replace `androidTarget()` with `com.android.kotlin.multiplatform.library`.** A Kotlin
  Multiplatform module can't apply `com.android.application` or `com.android.library` anymore. Apply
  `com.android.kotlin.multiplatform.library` and move the settings in the `android {}` block into
  `kotlin { android {} }`. If the module was an Android application, move the application part
  (`applicationId`, `targetSdk`, the activities, signing configs, etc.) to a
  separate `com.android.application` module that depends on the Kotlin Multiplatform module.

  ```kotlin
  // Before
  plugins {
      kotlin("multiplatform")
      id("com.android.library")
      // ...
  }

  kotlin {
      androidTarget()
  }

  android {
      namespace = "com.example.mylibrary"
      compileSdk = 36
      defaultConfig {
          minSdk = 24
      }
  }

  // After
  plugins {
      kotlin("multiplatform")
      id("com.android.kotlin.multiplatform.library")
      // ...
  }

  kotlin {
      android {
          namespace = "com.example.mylibrary"
          compileSdk = 36
          minSdk = 24
      }
  }
  ```

- **Replace `androidUnitTest` with `androidHostTest`.** Local unit tests of Kotlin Multiplatform
  libraries are in the `androidHostTest` source set, which exists only when you enable host tests
  using `kotlin { android { withHostTest {} } }`.
- **Choose the Cargo profile of Kotlin Multiplatform libraries using `androidVariant`.**
  `com.android.kotlin.multiplatform.library` has no build types, so Rustine embeds the variant
  selected by `cargo.androidVariant`. It defaults to `Variant.Release` when the requested tasks look
  like release builds or publishing (e.g., `assembleRelease`, `bundleRelease`, or `publish`), and
  to `Variant.Debug` otherwise. Set it explicitly when you build a release version of your app in
  another way, e.g., with `./gradlew build`.
  See [Be careful of the build variant used during publishing](./1-cargo-plugin.md#be-careful-of-the-build-variant-used-during-publishing).
- **Don't call Rust functions from Compose previews in Kotlin Multiplatform libraries.** Rustine
  adds the Rust library for the host to the debug variant of `com.android.application` and
  `com.android.library` modules during IDE sync, so that Compose previews can call Rust functions.
  `com.android.kotlin.multiplatform.library` has no debug variant, so Compose previews in these
  libraries can't call Rust functions.
- **Use `embedRustLibrary` to exclude ABIs in Kotlin Multiplatform libraries.**
  `com.android.kotlin.multiplatform.library` has no `ndk.abiFilters`.
  See [Building for Android](./1-cargo-plugin.md#building-for-android).
- **Update references to the generated ProGuard rule file.** The file generated by the UniFFI
  plugin moved from `build/generated/uniffi/androidMain/generated-proguard-rules.txt` to
  `build/generated/uniffiProguardRules/generated-proguard-rules.txt`. It is added to your Android
  modules automatically, so you don't have to reference it unless you have your own build logic
  using that file.