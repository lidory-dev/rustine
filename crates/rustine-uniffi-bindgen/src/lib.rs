/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::{collections::HashMap, fs::File, io::Write, process::Command};

use anyhow::{bail, Context, Result};
use camino::{Utf8Path, Utf8PathBuf};
use fs_err as fs;
use uniffi_bindgen::{Component, ComponentInterface};

mod gen_kotlin_multiplatform;
use gen_kotlin_multiplatform::{generate_bindings, Config};

/// Settings for [KotlinBindingGenerator::generate].
#[derive(Debug, Default)]
pub struct GenerationSettings {
    /// The directory to write the bindings to.
    pub out_dir: Utf8PathBuf,
    /// Whether to format the generated code with ktlint.
    pub try_format_code: bool,
    /// The name of the cdylib file, used when a crate's config has no `cdylib_name`.
    pub cdylib: Option<String>,
    /// When set, only generate bindings for the crate with this name.
    pub crate_filter: Option<String>,
}

/// Generates Kotlin Multiplatform bindings from UniFFI components.
///
/// Load the components with [uniffi_bindgen::BindgenLoader] and parse each crate's config with
/// [KotlinBindingGenerator::new_config], then pass them to [KotlinBindingGenerator::generate].
pub struct KotlinBindingGenerator;

impl KotlinBindingGenerator {
    /// Parse a crate's config. Unlike UniFFI's built-in bindings, the Kotlin Multiplatform
    /// options are at the top level of the table, not under `[bindings.kotlin]`.
    pub fn new_config(&self, root_toml: toml::Value) -> Result<Config> {
        Ok(root_toml.try_into()?)
    }

    /// Prepare the components and write their bindings.
    ///
    /// This fills the config defaults that depend on other components, derives the FFI function
    /// signatures, and gives every component interface a copy of every other one, as UniFFI's
    /// own generators do.
    pub fn generate(
        &self,
        mut components: Vec<Component<Config>>,
        settings: &GenerationSettings,
    ) -> Result<()> {
        self.update_component_configs(settings, &mut components);

        // Derive the FFI functions after the configs had a chance to update any names.
        for c in &mut components {
            c.ci.derive_ffi_funcs()
                .context("Failed to derive FFI functions")?;
        }

        // Give every CI a cloned copy of every CI, including itself for simplicity. Do this last,
        // so the copies have the same contents as the CIs they are generated from.
        let all_cis = components.iter().map(|c| c.ci.clone()).collect::<Vec<_>>();
        for c in &mut components {
            c.ci.set_all_component_interfaces(all_cis.clone());
        }

        if let Some(crate_filter) = &settings.crate_filter {
            let mut matches: Vec<_> = components
                .drain(..)
                .filter(|c| c.ci.crate_name() == crate_filter)
                .collect();
            match matches.len() {
                0 => bail!("Crate {crate_filter} not found"),
                1 => components.push(matches.pop().unwrap()),
                n => bail!("{n} crates named {crate_filter} found"),
            }
        }

        fs::create_dir_all(&settings.out_dir)?;
        self.write_bindings(settings, &components)
    }

    fn update_component_configs(
        &self,
        settings: &GenerationSettings,
        components: &mut [Component<Config>],
    ) {
        for c in &mut *components {
            c.config
                .package_name
                .get_or_insert_with(|| format!("uniffi.{}", c.ci.namespace()));
            c.config.cdylib_name.get_or_insert_with(|| {
                settings
                    .cdylib
                    .clone()
                    .unwrap_or_else(|| format!("uniffi_{}", c.ci.namespace()))
            });
        }
        // We need to update package names
        let packages = HashMap::<String, String>::from_iter(
            components
                .iter()
                .map(|c| (c.ci.crate_name().to_string(), c.config.package_name())),
        );
        for c in components {
            for (ext_crate, ext_package) in &packages {
                if ext_crate != c.ci.crate_name()
                    && !c.config.external_packages.contains_key(ext_crate)
                {
                    c.config
                        .external_packages
                        .insert(ext_crate.to_string(), ext_package.clone());
                }
            }
        }
    }

    fn write_bindings(
        &self,
        settings: &GenerationSettings,
        components: &[Component<Config>],
    ) -> Result<()> {
        for Component { ci, config, .. } in components {
            let bindings = generate_bindings(config, ci)?;

            write_bindings_target(ci, settings, config, "common", bindings.common);

            if let Some(jvm) = bindings.jvm {
                write_bindings_target(ci, settings, config, "jvm", jvm);
            }
            if let Some(android) = bindings.android {
                write_bindings_target(ci, settings, config, "android", android);
            }
            if let Some(native) = bindings.native {
                write_bindings_target(ci, settings, config, "native", native);
            }
            if let Some(stub) = bindings.stub {
                write_bindings_target(ci, settings, config, "stub", stub);
            }

            if let Some(header) = bindings.header {
                write_cinterop(ci, &settings.out_dir, header);
            }
        }
        Ok(())
    }
}

fn write_bindings_target(
    ci: &ComponentInterface,
    settings: &GenerationSettings,
    config: &Config,
    target: &str,
    content: String,
) {
    let source_set_name = if config.kotlin_multiplatform {
        format!("{}Main", target)
    } else {
        String::from("main")
    };
    let package_path: Utf8PathBuf = config.package_name().split('.').collect();
    let file_name = format!("{}.{}.kt", ci.namespace(), target);

    let dest_dir = Utf8PathBuf::from(&settings.out_dir)
        .join(source_set_name)
        .join("kotlin")
        .join(package_path);
    let file_path = Utf8PathBuf::from(&dest_dir).join(file_name);

    fs::create_dir_all(dest_dir).unwrap();
    fs::write(&file_path, content).unwrap();

    if settings.try_format_code {
        println!("Code generation complete, formatting with ktlint (use --no-format to disable)");
        if let Err(e) = Command::new("ktlint").arg("-F").arg(&file_path).output() {
            println!(
                "Warning: Unable to auto-format {} using ktlint: {e:?}",
                file_path.file_name().unwrap(),
            );
        }
    }
}

fn write_cinterop(ci: &ComponentInterface, out_dir: &Utf8Path, content: String) {
    let dst_dir = Utf8PathBuf::from(out_dir)
        .join("nativeInterop")
        .join("cinterop")
        .join("headers")
        .join(ci.namespace());
    fs::create_dir_all(&dst_dir).unwrap();
    let file_path = dst_dir.join(format!("{}.h", ci.namespace()));
    let mut f = File::create(file_path).unwrap();
    write!(f, "{}", content).unwrap();
}
