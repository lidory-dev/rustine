/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::collections::HashMap;
use std::error::Error;
use std::fs;

use anyhow::{bail, Context as _};
use camino::{Utf8Path, Utf8PathBuf};
use clap::Parser;
use rustine_uniffi_bindgen::{GenerationSettings, KotlinBindingGenerator};
use serde::Deserialize;
use uniffi_bindgen::interface::{Constructor, Function, Method};
use uniffi_bindgen::{BindgenLoader, BindgenPaths, BindgenPathsLayer, GlobalConfig};
use uniffi_meta::{create_metadata_groups, group_metadata, Metadata, MetadataGroup};

#[derive(Parser)]
#[clap(name = clap::crate_name!())]
#[clap(version = clap::crate_version!())]
#[clap(propagate_version = true)]
struct Cli {
    /// Directory in which to write generated files. Default is same folder as .udl file.
    #[clap(long, short)]
    out_dir: Option<Utf8PathBuf>,

    /// Path to the optional uniffi config file.
    /// If not provided, uniffi-bindgen will try to guess it from the UDL's file location.
    /// When `--library` is passed, its `exclude`, `rename` and `mutable_records` keys only apply
    /// to the crate selected with `--crate`, or to the only crate of the library.
    #[clap(long, short)]
    config: Option<Utf8PathBuf>,

    /// Path to the optional uniffi config file by a crate name.
    #[clap(long, value_parser = parse_key_val::<String, Utf8PathBuf>)]
    crate_configs: Vec<(String, Utf8PathBuf)>,

    /// Path of the package by a crate name.
    #[clap(long, value_parser = parse_key_val::<String, Utf8PathBuf>)]
    crate_paths: Vec<(String, Utf8PathBuf)>,

    /// Extract proc-macro metadata from a native lib (cdylib or staticlib) for this crate.
    #[clap(long, short)]
    lib_file: Option<Utf8PathBuf>,

    /// Pass in a cdylib path rather than a UDL file.
    #[clap(long = "library")]
    library_mode: bool,

    /// When `--library` is passed, only generate bindings for one crate.
    /// When `--library` is not passed, use this as the crate name instead of attempting to
    /// locate and parse Cargo.toml.
    #[clap(long = "crate")]
    crate_name: Option<String>,

    #[clap(long = "format", default_value_t = false)]
    try_format_code: bool,

    /// Path to the UDL file, or cdylib if `library-mode` is specified.
    source: Utf8PathBuf,
}

/// Parse a single key-value pair
fn parse_key_val<T, U>(s: &str) -> Result<(T, U), anyhow::Error>
where
    T: std::str::FromStr,
    T::Err: Error + Send + Sync + 'static,
    U: std::str::FromStr,
    U::Err: Error + Send + Sync + 'static,
{
    let pos = s
        .find('=')
        .ok_or_else(|| anyhow::Error::msg(format!("invalid key=value format: '{s}'")))?;
    Ok((s[..pos].parse()?, s[pos + 1..].parse()?))
}

/// The keys of the flat config that name the items of a single crate. In library mode, `--config`
/// only sets them for the main crate.
const CRATE_ITEM_KEYS: &[&str] = &["exclude", "rename", "mutable_records"];

/// Finds crate roots, configs and UDL files from the command line options.
///
/// `--crate-configs` takes precedence over the `uniffi.toml` file in the root given by
/// `--crate-paths`. UDL files are looked up in `{crate root}/src`.
struct CliPathsLayer {
    crate_configs: HashMap<String, Utf8PathBuf>,
    crate_paths: HashMap<String, Utf8PathBuf>,
}

impl BindgenPathsLayer for CliPathsLayer {
    fn get_crate_root(&self, crate_name: &str) -> Option<Utf8PathBuf> {
        self.crate_paths.get(crate_name).cloned()
    }

    fn get_config_path(&self, crate_name: &str) -> Option<Utf8PathBuf> {
        self.crate_configs.get(crate_name).cloned().or_else(|| {
            self.get_crate_root(crate_name)
                .map(|root| root.join("uniffi.toml"))
        })
    }
}

/// The root of the crate that contains the UDL file, when generating from a UDL file.
///
/// It takes precedence over [CliPathsLayer]: like `uniffi_bindgen::generate_external_bindings`
/// did, the base config of the UDL crate is the `uniffi.toml` file in its root, and `--config` is
/// merged over it. `--crate-paths` and `--crate-configs` do not apply to that crate.
struct UdlCrateRootLayer {
    crate_name: String,
    crate_root: Utf8PathBuf,
}

impl BindgenPathsLayer for UdlCrateRootLayer {
    fn get_crate_root(&self, crate_name: &str) -> Option<Utf8PathBuf> {
        (crate_name == self.crate_name).then(|| self.crate_root.clone())
    }
}

fn load_toml_file(path: &Utf8Path) -> anyhow::Result<toml::value::Table> {
    let contents = fs::read_to_string(path).with_context(|| format!("read file: {:?}", path))?;
    toml::de::from_str(&contents).with_context(|| format!("parse toml: {:?}", path))
}

/// Guess the root directory of the crate from the path of its UDL file.
///
/// Like UniFFI, assume that the UDL file is in `./src/something.udl` relative to the crate root.
fn guess_crate_root(udl_file: &Utf8Path) -> anyhow::Result<&Utf8Path> {
    let path_guess = udl_file
        .parent()
        .context("UDL file has no parent folder!")?
        .parent()
        .context("UDL file has no grand-parent folder!")?;
    if !path_guess.join("Cargo.toml").is_file() {
        bail!("UDL file does not appear to be inside a crate")
    }
    Ok(path_guess)
}

/// Determine the library crate name from the Cargo.toml file of the crate containing the UDL file.
fn crate_name_from_cargo_toml(udl_file: &Utf8Path) -> anyhow::Result<String> {
    #[derive(Deserialize)]
    struct CargoToml {
        package: Package,
        #[serde(default)]
        lib: Lib,
    }

    #[derive(Deserialize)]
    struct Package {
        name: String,
    }

    #[derive(Default, Deserialize)]
    struct Lib {
        name: Option<String>,
    }

    let file = guess_crate_root(udl_file)?.join("Cargo.toml");
    let cargo_toml_str =
        fs::read_to_string(file).context("Can't find Cargo.toml to determine the crate name")?;
    let cargo_toml = toml::from_str::<CargoToml>(&cargo_toml_str)?;
    Ok(cargo_toml
        .lib
        .name
        .unwrap_or_else(|| cargo_toml.package.name.replace('-', "_")))
}

/// If `library_path` is a C dynamic library, return its name.
///
/// Static libraries are not cdylibs, so they do not give the bindings a default `cdylib_name`.
fn calc_cdylib_name(library_path: &Utf8Path) -> Option<&str> {
    let cdylib_extensions = [".so", ".dll", ".dylib"];
    let filename = library_path.file_name()?;
    let filename = filename.strip_prefix("lib").unwrap_or(filename);
    cdylib_extensions
        .iter()
        .find_map(|ext| filename.strip_suffix(ext))
}

/// Set the checksums of the functions, methods and constructors parsed from a UDL file.
///
/// The checksums must be computed from the metadata as the scaffolding sees it, before any
/// changes to the component interface. `BindgenLoader` does the same for UDL files referenced
/// by a library.
fn add_checksums_to_udl_group(group: &mut MetadataGroup) {
    let items = std::mem::take(&mut group.items);
    for mut meta in items {
        match &mut meta {
            Metadata::Func(func) if func.checksum.is_none() => {
                func.checksum = Some(Function::checksum_from_metadata(func.clone()))
            }
            Metadata::Method(meth) if meth.checksum.is_none() => {
                meth.checksum = Some(Method::checksum_from_metadata(meth.clone()))
            }
            Metadata::Constructor(cons) if cons.checksum.is_none() => {
                cons.checksum = Some(Constructor::checksum_from_metadata(cons.clone()))
            }
            // UDL-based callbacks don't have checksum functions.
            _ => (),
        }
        group.items.insert(meta);
    }
}

/// Load the metadata of a UDL file, plus the proc-macro metadata of `lib_file` if given.
fn load_udl_metadata(
    udl_file: &Utf8Path,
    crate_name: &str,
    lib_file: Option<&Utf8Path>,
) -> anyhow::Result<uniffi_meta::MetadataGroupMap> {
    let udl = fs::read_to_string(udl_file)
        .with_context(|| format!("Failed to read UDL from {udl_file}"))?;
    let mut group = uniffi_udl::parse_udl(&udl, crate_name)
        .with_context(|| format!("parsing udl file {udl_file}"))?;
    add_checksums_to_udl_group(&mut group);

    if let Some(lib_file) = lib_file {
        let items = uniffi_bindgen::macro_metadata::extract_from_library(lib_file)
            .context("Failed to extract proc-macro metadata")?;
        let mut lib_groups = create_metadata_groups(&items);
        group_metadata(&mut lib_groups, items)?;
        for lib_group in lib_groups.into_values() {
            if lib_group.items.is_empty() {
                continue;
            }
            if lib_group.namespace.crate_name != crate_name {
                bail!(
                    "Found metadata items from crate `{}`.  Use the `--library` to generate bindings for multiple crates",
                    lib_group.namespace.crate_name,
                );
            }
            if lib_group.namespace.name != group.namespace.name {
                bail!(
                    "Namespace mismatch: {} - {}",
                    lib_group.namespace.name,
                    group.namespace.name,
                );
            }
            group.items.extend(lib_group.items);
            if group.namespace_docstring.is_none() {
                group.namespace_docstring = lib_group.namespace_docstring;
            }
        }
    }

    Ok(HashMap::from([(crate_name.to_string(), group)]))
}

fn main() -> anyhow::Result<()> {
    let Cli {
        out_dir,
        config,
        crate_configs,
        crate_paths,
        lib_file,
        library_mode,
        crate_name,
        source,
        try_format_code,
    } = Cli::parse();

    let crate_configs: HashMap<_, _> = crate_configs.into_iter().collect();
    let crate_paths: HashMap<_, _> = crate_paths.into_iter().collect();

    // When generating from a UDL file, `--crate` names the crate of the UDL file.
    let udl_crate_name = match (library_mode, crate_name.clone()) {
        (true, _) => None,
        (false, Some(crate_name)) => Some(crate_name),
        (false, None) => Some(crate_name_from_cargo_toml(&source)?),
    };

    let mut paths = BindgenPaths::default();
    // The crate whose config comes from its root instead of `--crate-configs`.
    let mut crate_with_root_config = None;
    if let Some(udl_crate_name) = &udl_crate_name {
        if let Ok(crate_root) = guess_crate_root(&source) {
            paths.add_layer(UdlCrateRootLayer {
                crate_name: udl_crate_name.clone(),
                crate_root: crate_root.to_owned(),
            });
            crate_with_root_config = Some(udl_crate_name.clone());
        }
    }
    paths.add_layer(CliPathsLayer {
        crate_configs: crate_configs.clone(),
        crate_paths,
    });
    let loader = BindgenLoader::new(paths, GlobalConfig::default());

    // `main_crate` is the crate that the keys of `--config` naming the items of a single crate
    // apply to (see `CRATE_ITEM_KEYS`).
    let (metadata, settings, main_crate) = match udl_crate_name {
        None => {
            if lib_file.is_some() {
                bail!("--lib-file is not compatible with --library.");
            }
            let Some(out_dir) = out_dir else {
                bail!("--out-dir is required when using --library");
            };
            let metadata = loader
                .load_metadata(&source)
                .with_context(|| format!("finding components in '{source}'"))?;
            if let Some(crate_name) = &crate_name {
                if !metadata.contains_key(crate_name) {
                    bail!("Crate {crate_name} not found in {source}");
                }
            }
            // The crate selected with `--crate`, or the only crate of the library.
            let main_crate = crate_name.clone().or_else(|| match metadata.len() {
                1 => metadata.keys().next().cloned(),
                _ => None,
            });
            let settings = GenerationSettings {
                out_dir,
                try_format_code,
                cdylib: calc_cdylib_name(&source).map(ToOwned::to_owned),
                crate_filter: crate_name,
            };
            (metadata, settings, main_crate)
        }
        Some(udl_crate_name) => {
            let metadata = load_udl_metadata(&source, &udl_crate_name, lib_file.as_deref())?;
            let out_dir = match out_dir {
                Some(out_dir) => {
                    fs::create_dir_all(&out_dir)?;
                    out_dir
                        .canonicalize_utf8()
                        .context("Unable to find out-dir")?
                }
                None => source
                    .parent()
                    .context("File has no parent directory")?
                    .to_owned(),
            };
            let settings = GenerationSettings {
                out_dir,
                try_format_code,
                cdylib: lib_file
                    .as_deref()
                    .and_then(calc_cdylib_name)
                    .map(ToOwned::to_owned),
                crate_filter: None,
            };
            (metadata, settings, Some(udl_crate_name))
        }
    };

    let cis = loader.load_cis(metadata)?;

    // The `--config` file is a flat Rustine config. It is merged over the config of every crate.
    // Like UniFFI's deprecated entry points did, ignore it when it does not exist.
    let config_override = config
        .as_deref()
        .filter(|path| path.exists())
        .map(load_toml_file)
        .transpose()?;
    // The keys naming the items of a single crate are only merged over the config of the main
    // crate. Other crates of a library take them from their own config, which the Gradle plugin
    // passes with `--crate-configs`, so that they don't rename or exclude items of the wrong crate.
    let other_crates_override = config_override.clone().map(|mut config_override| {
        config_override.retain(|key, _| !CRATE_ITEM_KEYS.contains(&key));
        config_override
    });
    if main_crate.is_none() {
        if let Some(key) = config_override
            .iter()
            .flat_map(|config_override| config_override.keys())
            .find(|key| CRATE_ITEM_KEYS.contains(&key.as_str()))
        {
            bail!(
                "`{key}` in --config names the items of a single crate, but {source} contains \
                 several crates. Pass --crate to choose the crate, or set `{key}` in the config \
                 of the crate with --crate-configs."
            );
        }
    }
    let binding_generator = KotlinBindingGenerator;
    let components = loader.load_components(cis, |ci, toml| {
        // A missing config file is an error when it was given explicitly and is used.
        if crate_with_root_config.as_deref() != Some(ci.crate_name()) {
            if let Some(path) = crate_configs.get(ci.crate_name()) {
                if !path.exists() {
                    bail!("read file: {path:?}: file not found");
                }
            }
        }
        let toml::Value::Table(mut table) = toml else {
            bail!("the config of {} is not a table", ci.crate_name());
        };
        let config_override = if main_crate.as_deref() == Some(ci.crate_name()) {
            &config_override
        } else {
            &other_crates_override
        };
        if let Some(config_override) = config_override {
            uniffi_bindgen::merge_toml(&mut table, config_override.clone())
                .context("override config")?;
        }
        binding_generator
            .new_config(toml::Value::Table(table))
            .context("loading toml")
    })?;

    binding_generator.generate(components, &settings)
}
