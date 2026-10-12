import rustine.gradle.RustineHost
import rustine.gradle.cargo.dsl.jvm
import rustine.gradle.uniffi.tasks.InstallUniffiBindgenTask

plugins {
    id("host-jvm-native-tests")
    id("dev.lidory.rustine.cargo")
    id("dev.lidory.rustine.uniffi")
}

cargo {
    builds.jvm {
        embedRustLibrary.set(rustTarget == RustineHost.current.rustTarget)
    }
    wasmTransformerFromPath(
        rootProject.layout.projectDirectory.dir("crates/rustine-wasm-transformer")
    )
}

kotlin {
    explicitApi()
}

uniffi {
    bindgenFromPath(rootProject.layout.projectDirectory.dir("crates/rustine-uniffi-bindgen"))
}

tasks.withType<InstallUniffiBindgenTask> {
    quiet = false
}
