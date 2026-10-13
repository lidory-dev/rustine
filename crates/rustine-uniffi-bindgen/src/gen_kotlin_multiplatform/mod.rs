/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::borrow::Borrow;
use std::cell::RefCell;
use std::cmp::Ordering;
use std::collections::{BTreeSet, HashMap, HashSet};
use std::fmt::Debug;

use anyhow::{bail, Context, Result};
use askama::Template;
use filters::header_escape_name;
use heck::{ToLowerCamelCase, ToShoutySnakeCase, ToUpperCamelCase};
use serde::{Deserialize, Serialize};
use uniffi_bindgen::interface::*;

mod callback_interface;
mod compounds;
mod custom;
mod enum_;
mod miscellany;
mod object;
mod primitives;
mod record;
mod variant;

#[rustfmt::skip]
const CPP_KEYWORDS: &[&str] = &[
    "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor", "bool",
    "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t", "class",
    "compl", "concept", "const", "const_cast", "consteval", "constexpr", "constinit",
    "continue", "co_await", "co_return", "co_yield", "decltype", "default", "delete",
    "do", "double", "dynamic_cast", "else", "enum", "explicit", "export", "extern",
    "false", "float", "for", "friend", "goto", "if", "inline", "int", "long",
    "mutable", "namespace", "new", "noexcept", "not", "not_eq", "nullptr",
    "operator", "or", "or_eq", "private", "protected", "public", "register",
    "reinterpret_cast", "requires", "return", "short", "signed", "sizeof",
    "static", "static_assert", "static_cast", "struct", "switch", "template",
    "this", "thread_local", "throw", "true", "try", "typedef", "typeid", "typename",
    "union", "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while",
    "xor", "xor_eq"
];

trait CodeType: Debug {
    /// The language specific label used to reference this type. This will be used in
    /// method signatures and property declarations.
    fn type_label(&self, ci: &ComponentInterface) -> String;

    /// A representation of this type label that can be used as part of another
    /// identifier. e.g. `read_foo()`, or `FooInternals`.
    ///
    /// This is especially useful when creating specialized objects or methods to deal
    /// with this type only.
    fn canonical_name(&self) -> String;

    // default for named types is to assume a ctor exists.
    fn default(
        &self,
        default: &DefaultValue,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String> {
        let _ = config;
        match default {
            DefaultValue::Default => Ok(format!("{}()", self.type_label(ci))),
            DefaultValue::Literal(_) => bail!("Literals for named types are not supported"),
        }
    }

    /// Name of the FfiConverter
    ///
    /// This is the object that contains the lower, write, lift, and read methods for this type.
    /// Depending on the binding this will either be a singleton or a class with static methods.
    ///
    /// This is the newer way of handling these methods and replaces the lower, write, lift, and
    /// read CodeType methods.  Currently only used by Kotlin, but the plan is to move other
    /// backends to using this.
    fn ffi_converter_name(&self) -> String {
        format!("FfiConverter{}", self.canonical_name())
    }

    /// Function to run at startup
    fn initialization_fn(&self) -> Option<String> {
        None
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub enum ConfigKotlinTarget {
    #[serde(rename = "jvm")]
    Jvm,
    #[serde(rename = "android")]
    Android,
    #[serde(rename = "native")]
    Native,
    #[serde(rename = "stub")]
    Stub,
}

// config options to customize the generated Kotlin.
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct Config {
    pub(super) package_name: Option<String>,
    pub(super) cdylib_name: Option<String>,
    #[serde(default)]
    pub(super) kotlin_multiplatform: bool,
    #[serde(default)]
    kotlin_targets: Vec<ConfigKotlinTarget>,
    generate_immutable_records: Option<bool>,
    #[serde(default)]
    omit_checksums: bool,
    #[serde(default)]
    custom_types: HashMap<String, CustomTypeConfig>,
    #[serde(default)]
    pub(super) external_packages: HashMap<String, String>,
    #[serde(default)]
    kotlin_target_version: Option<String>,
    #[serde(default)]
    disable_java_cleaner: bool,
    generate_serializable_types: Option<bool>,
    #[serde(default)]
    use_pascal_case_enum_class: Option<bool>,
    #[serde(default)]
    jvm_dynamic_library_dependencies: Vec<String>,
    #[serde(default)]
    android_dynamic_library_dependencies: Vec<String>,
    #[serde(default)]
    dynamic_library_dependencies: Vec<String>,
    #[serde(default, rename = "__enable_jna_interface_mapping")]
    enable_jna_interface_mapping: Option<bool>,
    /// Functions, types, constructors and methods to leave out of the bindings, such as
    /// `"MyObject.new"`.
    #[serde(default)]
    pub(super) exclude: Vec<String>,
    /// New names for the items of the crate, keyed by their UniFFI names, such as `"MyRecord"`,
    /// `"MyRecord.field"`, `"MyEnum.Variant"`, `"my_function.arg"` or `"MyObject.method"`.
    #[serde(default)]
    pub(super) rename: toml::Table,
    /// Records whose fields stay `var` when `generate_immutable_records` is `true`, by their names
    /// in the bindings, after `rename`.
    #[serde(default)]
    mutable_records: HashSet<String>,
}

// TODO: Make this public in 0.4.0
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
pub(crate) enum Visibility {
    #[serde(rename = "public")]
    Public,
    #[serde(rename = "internal")]
    Internal,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct CustomTypeConfig {
    imports: Option<Vec<String>>,
    type_name: Option<String>,
    into_custom: String, // b/w compat alias for lift
    lift: String,
    from_custom: String, // b/w compat alias for lower
    lower: String,
}

// functions replace literal "{}" in strings with a specified value.
impl CustomTypeConfig {
    fn lift(&self, name: &str) -> String {
        let converter = if self.lift.is_empty() {
            &self.into_custom
        } else {
            &self.lift
        };
        converter.replace("{}", name)
    }

    fn lower(&self, name: &str) -> String {
        let converter = if self.lower.is_empty() {
            &self.from_custom
        } else {
            &self.lower
        };
        converter.replace("{}", name)
    }
}

impl Config {
    // We insist someone has already configured us - any defaults we supply would be wrong.
    pub fn package_name(&self) -> String {
        self.package_name
            .as_ref()
            .expect("package name should have been set in update_component_configs")
            .clone()
    }

    pub fn cdylib_name(&self) -> String {
        self.cdylib_name
            .as_ref()
            .expect("cdylib name should have been set in update_component_configs")
            .clone()
    }

    /// Whether to generate immutable records (`val` instead of `var`)
    pub fn generate_immutable_records(&self) -> bool {
        self.generate_immutable_records.unwrap_or(false)
    }

    /// Whether the fields of the record named `name` are `val` instead of `var`.
    pub fn is_record_immutable(&self, name: &str) -> bool {
        self.generate_immutable_records() && !self.mutable_records.contains(name)
    }

    fn kotlin_version_is_at_least(&self, major: usize, minor: usize, patch: usize) -> bool {
        let Some(kotlin_target_version) = &self.kotlin_target_version else {
            return false;
        };
        let mut kotlin_target_version = kotlin_target_version.split(|c: char| !c.is_numeric());

        for required_version in [major, minor, patch] {
            let Some(current_version) = kotlin_target_version
                .next()
                .and_then(|v| v.parse::<usize>().ok())
            else {
                return required_version == 0;
            };
            match required_version.cmp(&current_version) {
                Ordering::Equal => continue,
                Ordering::Greater => return false,
                Ordering::Less => return true,
            }
        }

        true
    }

    pub fn use_enum_entries(&self) -> bool {
        // Enum.entries became stable in Kotlin 1.9.0 (introduced in 1.8.20)
        self.kotlin_version_is_at_least(1, 9, 0)
    }

    pub fn use_data_objects(&self) -> bool {
        // data objects became stable in Kotlin 1.9.0 (introduced in 1.8.20)
        self.kotlin_version_is_at_least(1, 9, 0)
    }

    pub fn generate_serializable(&self) -> bool {
        self.generate_serializable_types.unwrap_or(false)
    }

    pub fn jvm_dynamic_library_dependencies(&self) -> Vec<String> {
        let mut libraries = self.jvm_dynamic_library_dependencies.clone();
        libraries.extend_from_slice(&self.dynamic_library_dependencies);
        libraries
    }

    pub fn android_dynamic_library_dependencies(&self) -> Vec<String> {
        let mut libraries = self.android_dynamic_library_dependencies.clone();
        libraries.extend_from_slice(&self.dynamic_library_dependencies);
        libraries
    }

    pub fn dynamic_library_dependencies(&self, module_name: &str) -> Vec<String> {
        match module_name {
            "jvm" => self.jvm_dynamic_library_dependencies(),
            "android" => self.android_dynamic_library_dependencies(),
            _ => vec![],
        }
    }

    // Get the package name for an external type
    pub fn external_package_name(&self, module_path: &str, namespace: Option<&str>) -> String {
        // config overrides are keyed by the crate name, default fallback is the namespace.
        let crate_name = module_path.split("::").next().unwrap();
        match self.external_packages.get(crate_name) {
            Some(name) => name.clone(),
            // If the module path is not in `external_packages`, we need to fall back to a default
            // with the namespace, which we hopefully have.  This is quite fragile, but it's
            // unreachable in library mode - all deps get an entry in `external_packages` with the
            // correct namespace.
            None => format!("uniffi.{}", namespace.unwrap_or(crate_name)),
        }
    }

    pub fn enable_jna_interface_mapping(&self) -> bool {
        self.enable_jna_interface_mapping.unwrap_or(false)
    }
}

pub struct MultiplatformBindings {
    pub common: String,
    pub jvm: Option<String>,
    pub android: Option<String>,
    pub native: Option<String>,
    pub stub: Option<String>,
    pub header: Option<String>,
}

// Generate kotlin bindings for the given ComponentInterface, as a string.
pub fn generate_bindings(
    config: &Config,
    ci: &ComponentInterface,
) -> Result<MultiplatformBindings> {
    check_borrowed_bytes_arguments(ci)?;
    check_value_type_members(ci)?;
    check_custom_error_types(ci)?;

    let common = CommonKotlinWrapper::new("common", Some(Visibility::Public), config.clone(), ci)
        .context("failed to create a common binding generator")?
        .render()
        .context("failed to render common Kotlin bindings")?;

    fn run_with_target(
        config: &Config,
        target: ConfigKotlinTarget,
        f: impl FnOnce() -> Result<String>,
    ) -> Result<Option<String>> {
        config
            .kotlin_targets
            .contains(&target)
            .then(f)
            .map_or(Ok(None), |v| v.map(Some))
    }

    let jvm = run_with_target(config, ConfigKotlinTarget::Jvm, || {
        AndroidJvmKotlinWrapper::new("jvm", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a JVM binding generator")?
            .render()
            .context("failed to render Kotlin/JVM bindings")
    })?;

    let android = run_with_target(config, ConfigKotlinTarget::Android, || {
        AndroidJvmKotlinWrapper::new("android", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a Android binding generator")?
            .render()
            .context("failed to render Android Kotlin/JVM bindings")
    })?;

    let native = run_with_target(config, ConfigKotlinTarget::Native, || {
        NativeKotlinWrapper::new("native", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a native binding generator")?
            .render()
            .context("failed to render Kotlin/Native bindings")
    })?;

    let stub = run_with_target(config, ConfigKotlinTarget::Stub, || {
        StubKotlinWrapper::new("stub", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a stub binding generator")?
            .render()
            .context("failed to render stub bindings")
    })?;

    let header = run_with_target(config, ConfigKotlinTarget::Native, || {
        HeadersKotlinWrapper::new("headers", Some(Visibility::Public), config.clone(), ci)
            .context("failed to create a native header binding generator")?
            .render()
            .context("failed to render Kotlin/Native headers")
    })?;

    Ok(MultiplatformBindings {
        common,
        jvm,
        android,
        native,
        stub,
        header,
    })
}

/// Fail for borrowed byte slices (`&[u8]` or `[ByRef] bytes`) that the bindings can't pass.
///
/// Such arguments cross the FFI as `ForeignBytes`, which borrows foreign memory for the duration
/// of a call. Kotlin can pass them to synchronous calls into Rust only: a Rust future would
/// outlive the borrow, and Kotlin implementations of callback interfaces and traits would have to
/// receive them, which neither Kotlin nor the Rust scaffolding supports.
fn check_borrowed_bytes_arguments(ci: &ComponentInterface) -> Result<()> {
    fn borrowed_bytes_argument(callable: &impl Callable) -> Option<&Argument> {
        callable
            .arguments()
            .into_iter()
            .find(|arg| arg.is_borrowed_bytes())
    }

    fn check(callable: &impl Callable, name: &str, implemented_in_kotlin: bool) -> Result<()> {
        let Some(arg) = borrowed_bytes_argument(callable) else {
            return Ok(());
        };
        let reason = if callable.is_async() {
            "async functions, methods and constructors"
        } else if implemented_in_kotlin {
            "methods of callback interfaces or traits that Kotlin can implement"
        } else {
            return Ok(());
        };
        bail!(
            "`{name}` takes the borrowed byte slice `{}` (`&[u8]` or `[ByRef] bytes`), which is not \
             supported in {reason}. Take a `Vec<u8>` (`bytes`) instead.",
            arg.name(),
        )
    }

    for func in ci.function_definitions() {
        check(func, func.name(), false)?;
    }
    for obj in ci.object_definitions() {
        for cons in obj.constructors() {
            check(cons, &format!("{}::{}", obj.name(), cons.name()), false)?;
        }
        for meth in obj.methods() {
            let name = format!("{}::{}", obj.name(), meth.name());
            check(meth, &name, obj.has_callback_interface())?;
        }
    }
    for cbi in ci.callback_interface_definitions() {
        for meth in cbi.methods() {
            check(meth, &format!("{}::{}", cbi.name(), meth.name()), true)?;
        }
    }
    for rec in ci.record_definitions() {
        for meth in rec.methods() {
            check(meth, &format!("{}::{}", rec.name(), meth.name()), false)?;
        }
    }
    for e in ci.enum_definitions() {
        for meth in e.methods() {
            check(meth, &format!("{}::{}", e.name(), meth.name()), false)?;
        }
    }
    Ok(())
}

/// A struct to record a Kotlin import statement.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum ImportRequirement {
    /// The name we are importing.
    Import { name: String },
    /// Import the name with the specified local name.
    ImportAs { name: String, as_name: String },
}

impl ImportRequirement {
    /// Render the Kotlin import statement.
    fn render(&self) -> String {
        match &self {
            ImportRequirement::Import { name } => format!("import {name}"),
            ImportRequirement::ImportAs { name, as_name } => {
                format!("import {name} as {as_name}")
            }
        }
    }
}

macro_rules! kotlin_type_renderer {
    ($TypeRenderer:ident, $source_file:literal) => {
        /// Renders Kotlin helper code for all types
        ///
        /// This template is a bit different than others in that it stores internal state from the render
        /// process.  Make sure to only call `render()` once.
        #[derive(Template)]
        #[template(syntax = "kt", escape = "none", path = $source_file)]
        #[allow(dead_code)]
        pub struct $TypeRenderer<'a> {
            module_name: &'a str,
            visibility: Option<Visibility>,
            config: &'a Config,
            ci: &'a ComponentInterface,
            // Track imports added with the `add_import()` macro
            imports: RefCell<BTreeSet<ImportRequirement>>,
        }

        #[allow(dead_code)]
        impl<'a> $TypeRenderer<'a> {
            fn new(
                module_name: &'a str,
                visibility: Option<Visibility>,
                config: &'a Config,
                ci: &'a ComponentInterface,
            ) -> Self {
                Self {
                    module_name,
                    visibility,
                    config,
                    ci,
                    imports: RefCell::new(BTreeSet::new()),
                }
            }

            // Get the package name for an external type
            fn external_type_package_name(&self, module_path: &str, namespace: &str) -> String {
                self.config
                    .external_package_name(module_path, Some(namespace))
            }

            // The following methods are used by the `Types.kt` macros.

            // Helper to add an import statement
            //
            // Call this inside your template to cause an import statement to be added at the top of the
            // file.  Imports will be sorted and de-deuped.
            //
            // Returns an empty string so that it can be used inside an askama `{{ }}` block.
            fn add_import(&self, name: &str) -> &str {
                self.imports.borrow_mut().insert(ImportRequirement::Import {
                    name: name.to_owned(),
                });
                ""
            }

            // Like add_import, but arranges for `import name as as_name`
            fn add_import_as(&self, name: &str, as_name: &str) -> &str {
                self.imports
                    .borrow_mut()
                    .insert(ImportRequirement::ImportAs {
                        name: name.to_owned(),
                        as_name: as_name.to_owned(),
                    });
                ""
            }

            fn visibility(&self) -> &str {
                match self.visibility {
                    None => "",
                    Some(Visibility::Public) => "public ",
                    Some(Visibility::Internal) => "internal ",
                }
            }
        }
    };
}

macro_rules! kotlin_wrapper {
    ($KotlinWrapper:ident, $TypeRenderer:ident, $source_file:literal) => {
        #[derive(Template)]
        #[template(syntax = "kt", escape = "none", path = $source_file)]
        #[allow(dead_code)]
        pub struct $KotlinWrapper<'a> {
            module_name: &'a str,
            visibility: Option<Visibility>,
            config: Config,
            ci: &'a ComponentInterface,
            type_helper_code: String,
            type_imports: BTreeSet<ImportRequirement>,
        }

        #[allow(dead_code)]
        impl<'a> $KotlinWrapper<'a> {
            pub fn new(
                module_name: &'a str,
                visibility: Option<Visibility>,
                config: Config,
                ci: &'a ComponentInterface,
            ) -> Result<Self> {
                let type_renderer = $TypeRenderer::new(module_name, visibility, &config, ci);
                let type_helper_code = type_renderer.render()?;
                let type_imports = type_renderer.imports.into_inner();
                Ok(Self {
                    module_name,
                    visibility,
                    config,
                    ci,
                    type_helper_code,
                    type_imports,
                })
            }

            fn initialization_fns(&self, ci: &ComponentInterface) -> Vec<String> {
                let init_fns = self
                    .ci
                    .iter_local_types()
                    .map(|t| KotlinCodeOracle.find(t))
                    .filter_map(|ct| ct.initialization_fn())
                    .map(|fn_name| format!("{fn_name}(this)"));

                // Also call global initialization function for any external type we use.
                // For example, we need to make sure that all callback interface vtables are registered
                // (#2343).
                let extern_module_init_fns = self
                    .ci
                    .iter_external_types()
                    .filter_map(|ty| ty.crate_name())
                    .map(|crate_name| {
                        let namespace = ci.namespace_for_module_path(crate_name).unwrap();
                        let package_name = self
                            .config
                            .external_package_name(crate_name, Some(namespace));
                        format!("{package_name}.uniffiEnsureInitialized()")
                    })
                    // Collect into a btree set to de-dup and order
                    .collect::<BTreeSet<_>>();

                init_fns.chain(extern_module_init_fns).collect()
            }

            fn imports(&self) -> Vec<ImportRequirement> {
                self.type_imports.iter().cloned().collect()
            }

            fn visibility(&self) -> &str {
                match self.visibility {
                    None => "",
                    Some(Visibility::Public) => "public ",
                    Some(Visibility::Internal) => "internal ",
                }
            }
        }
    };
}

kotlin_type_renderer!(CommonTypeRenderer, "common/Types.kt");
kotlin_wrapper!(CommonKotlinWrapper, CommonTypeRenderer, "common/wrapper.kt");

kotlin_type_renderer!(AndroidJvmTypeRenderer, "android+jvm/Types.kt");
kotlin_wrapper!(
    AndroidJvmKotlinWrapper,
    AndroidJvmTypeRenderer,
    "android+jvm/wrapper.kt"
);

kotlin_type_renderer!(NativeTypeRenderer, "native/Types.kt");
kotlin_wrapper!(NativeKotlinWrapper, NativeTypeRenderer, "native/wrapper.kt");

kotlin_type_renderer!(StubTypeRenderer, "stub/Types.kt");
kotlin_wrapper!(StubKotlinWrapper, StubTypeRenderer, "stub/wrapper.kt");

kotlin_type_renderer!(HeadersTypeRenderer, "headers/Types.h");
kotlin_wrapper!(
    HeadersKotlinWrapper,
    HeadersTypeRenderer,
    "headers/wrapper.h"
);

/// Get the name of the interface and class name for a trait.
///
/// For a regular `struct Foo` or `trait Foo`, there's `FooInterface` with `Foo` as
/// the name of the (Rust implemented) object. But if it's a foreign trait:
/// * The name `Foo` is the name of the interface used by a the Kotlin implementation of the trait.
/// * The Rust implemented object is `FooImpl`.
///
/// This all impacts what types `FfiConverter.lower()` inputs.  If it's a "foreign trait"
/// `lower` must lower anything that implements the interface (ie, a kotlin implementation).
/// If not, then lower only lowers the concrete class (ie, our simple instance with the pointer).
fn object_interface_name(ci: &ComponentInterface, obj: &Object) -> String {
    let class_name = KotlinCodeOracle.class_name(ci, obj.name());
    if obj.has_callback_interface() {
        class_name
    } else {
        format!("{class_name}Interface")
    }
}

// *sigh* - same thing for a trait, which might be either Object or CallbackInterface.
// (we should either fold it into object or kill it!)
//
// The name of a trait from another crate is fully qualified. Upstream relies on an import, but
// trait implementations are not part of `Object::iter_types()`, so nothing imports a trait that
// the crate doesn't use otherwise.
fn trait_interface_name(
    ci: &ComponentInterface,
    config: &Config,
    trait_ty: &Type,
) -> Result<String> {
    let Some(crate_name) = trait_ty.crate_name() else {
        bail!("Invalid trait_type: {trait_ty:?}");
    };
    // The trait may be defined by another crate. Upstream bails when that crate's interface is
    // unknown; fall back to the type metadata instead, which records whether the trait supports
    // foreign implementations. Do not look the trait up in `ci` then, where a type of the same
    // name would belong to another crate.
    let ci_look = ci
        .find_component_interface(crate_name)
        .or((crate_name == ci.crate_name()).then_some(ci));

    let (obj_name, has_callback_interface) = match trait_ty {
        Type::Object { name, imp, .. } => (
            name,
            ci_look
                .and_then(|ci_look| ci_look.get_object_definition(name))
                .map_or_else(
                    || imp.has_callback_interface(),
                    |obj| obj.has_callback_interface(),
                ),
        ),
        Type::CallbackInterface { name, .. } => (name, true),
        _ => bail!("Invalid trait_type: {trait_ty:?}"),
    };

    let class_name = match ci_look {
        Some(ci_look) => KotlinCodeOracle.class_name(ci_look, obj_name),
        None => obj_name.to_upper_camel_case(),
    };
    let interface_name = if has_callback_interface {
        class_name
    } else {
        format!("{class_name}Interface")
    };
    if crate_name == ci.crate_name() {
        Ok(interface_name)
    } else {
        let namespace = ci.namespace_for_module_path(crate_name).ok();
        let package_name = config.external_package_name(crate_name, namespace);
        Ok(format!("{package_name}.{interface_name}"))
    }
}

// The name of the object exposing a Rust implementation.
fn object_impl_name(ci: &ComponentInterface, obj: &Object) -> String {
    let class_name = KotlinCodeOracle.class_name(ci, obj.name());
    if obj.has_callback_interface() {
        format!("{class_name}Impl")
    } else {
        class_name
    }
}

/// The Kotlin members that implement the uniffi traits exported for a type.
///
/// Kotlin has a single `toString()` for the `Display` and `Debug` traits. It uses `Display`, and
/// `Debug` otherwise. Only `eq` of the `Eq` trait is used, for `equals()`.
#[derive(Debug, Clone, Default)]
pub struct TraitImpls {
    to_string: Option<Method>,
    equals: Option<Method>,
    hash_code: Option<Method>,
    compare_to: Option<Method>,
}

impl TraitImpls {
    fn new(methods: UniffiTraitMethods) -> Self {
        Self {
            to_string: methods.display_fmt.or(methods.debug_fmt),
            equals: methods.eq_eq,
            hash_code: methods.hash_hash,
            compare_to: methods.ord_cmp,
        }
    }

    /// Keep `toString()` only, for types whose Kotlin class can't override the others.
    fn only_to_string(self) -> Self {
        Self {
            to_string: self.to_string,
            ..Self::default()
        }
    }

    fn to_string(&self) -> Option<&Method> {
        self.to_string.as_ref()
    }

    fn equals(&self) -> Option<&Method> {
        self.equals.as_ref()
    }

    fn hash_code(&self) -> Option<&Method> {
        self.hash_code.as_ref()
    }

    fn compare_to(&self) -> Option<&Method> {
        self.compare_to.as_ref()
    }

    /// The methods the Kotlin members call.
    fn methods(&self) -> Vec<&Method> {
        [
            &self.to_string,
            &self.equals,
            &self.hash_code,
            &self.compare_to,
        ]
        .into_iter()
        .flatten()
        .collect()
    }
}

fn object_trait_impls(obj: &Object) -> TraitImpls {
    TraitImpls::new(obj.uniffi_trait_methods())
}

/// A constructor of an object, which returns the type of that object.
///
/// `Constructor::return_type()` is a copy of the object's type made from the metadata, which the
/// `rename` table doesn't rename: neither the renamer nor `VisitMut` visit it, and it can't be
/// changed from outside `uniffi_bindgen`. Upstream renders it, so the alternate constructors of a
/// renamed object return the old type, which doesn't exist. The constructors return the type of
/// the object they belong to instead, which is the same type when nothing is renamed.
pub struct ObjectConstructor<'a> {
    cons: &'a Constructor,
    return_type: Type,
}

impl<'a> ObjectConstructor<'a> {
    fn new(obj: &Object, cons: &'a Constructor) -> Self {
        Self {
            cons,
            return_type: obj.as_type(),
        }
    }

    fn name(&self) -> &str {
        self.cons.name()
    }
}

impl Callable for ObjectConstructor<'_> {
    fn arguments(&self) -> Vec<&Argument> {
        self.cons.arguments()
    }

    fn return_type(&self) -> Option<&Type> {
        Some(&self.return_type)
    }

    fn throws_type(&self) -> Option<&Type> {
        self.cons.throws_type()
    }

    fn is_async(&self) -> bool {
        self.cons.is_async()
    }

    fn docstring(&self) -> Option<&str> {
        self.cons.docstring()
    }

    fn ffi_func(&self) -> &FfiFunction {
        self.cons.ffi_func()
    }
}

/// The alternate constructors of an object, which the companion object exposes.
fn object_alternate_constructors(obj: &Object) -> Vec<ObjectConstructor<'_>> {
    obj.alternate_constructors()
        .into_iter()
        .map(|cons| ObjectConstructor::new(obj, cons))
        .collect()
}

/// The uniffi traits implemented by the class of a record.
///
/// A record without fields is a Kotlin `object`, which has a single value, so only its
/// `toString()` comes from Rust, like upstream generates no trait methods for it at all.
fn record_trait_impls(rec: &Record) -> TraitImpls {
    let trait_impls = TraitImpls::new(rec.uniffi_trait_methods());
    if rec.has_fields() {
        trait_impls
    } else {
        trait_impls.only_to_string()
    }
}

/// The uniffi traits implemented by the class of an enum or an error.
///
/// Kotlin `enum class`es can only override `toString()`; `check_value_type_members()` rejects
/// the other traits on flat enums. Flat errors can't be lowered back to Rust, so like upstream,
/// they implement no trait at all.
fn enum_trait_impls(ci: &ComponentInterface, e: &Enum) -> TraitImpls {
    let trait_impls = TraitImpls::new(e.uniffi_trait_methods());
    match (e.is_flat(), ci.is_name_used_as_error(e.name())) {
        (false, _) => trait_impls,
        (true, false) => trait_impls.only_to_string(),
        (true, true) => TraitImpls::default(),
    }
}

/// Fail for methods and uniffi traits of records, enums and errors that the bindings can't
/// render.
///
/// - Kotlin `enum class`es can't override `equals()`, `hashCode()` or `compareTo()`, so flat
///   enums can only export `Display` and `Debug`, like upstream.
/// - Flat errors can't be lowered back to Rust, so they can't be the receiver of a method or of
///   `Ord`. Upstream drops their methods silently, and emits a `Comparable` supertype without
///   `compareTo()` for `Ord`, which does not compile.
fn check_value_type_members(ci: &ComponentInterface) -> Result<()> {
    for e in ci.enum_definitions() {
        if !e.is_flat() {
            continue;
        }
        let trait_methods = e.uniffi_trait_methods();
        if ci.is_name_used_as_error(e.name()) {
            if let Some(meth) = e.methods().first() {
                bail!(
                    "Kotlin bindings do not support methods on flat error `{}`, such as `{}`, \
                     since flat errors can't be passed back to Rust. Remove `flat_error`, or add \
                     \"{}.{}\" to the `exclude` list in uniffi.toml.",
                    e.name(),
                    meth.name(),
                    e.name(),
                    meth.name(),
                );
            }
            if trait_methods.ord_cmp.is_some() {
                bail!(
                    "Kotlin bindings do not support exporting Ord for flat error `{}`, since flat \
                     errors can't be passed back to Rust",
                    e.name(),
                );
            }
        } else if trait_methods.eq_eq.is_some()
            || trait_methods.hash_hash.is_some()
            || trait_methods.ord_cmp.is_some()
        {
            bail!(
                "Kotlin bindings do not support exporting Eq/Ord/Hash for flat enum `{}`",
                e.name()
            );
        }
    }
    Ok(())
}

/// Fail for custom types that are thrown while their builtin type is not.
///
/// Since UniFFI 0.30, a custom type over an enum or an object can be thrown (#2658). Its Kotlin
/// type is an alias of the class of the builtin type, which is only an exception when the builtin
/// type is used as an error too. Upstream generates code that does not compile otherwise.
fn check_custom_error_types(ci: &ComponentInterface) -> Result<()> {
    for ty in ci.iter_local_types() {
        let Type::Custom { name, builtin, .. } = ty else {
            continue;
        };
        if !ci.is_name_used_as_error(name) {
            continue;
        }
        if let Some(builtin_name) = builtin.name() {
            if !ci.is_name_used_as_error(builtin_name) {
                bail!(
                    "The custom type `{name}` is used as an error, but its builtin type \
                     `{builtin_name}` is not. Kotlin bindings need `{builtin_name}` to be used as \
                     an error too, for example by a function that throws it."
                );
            }
        }
    }
    Ok(())
}

#[derive(Clone)]
pub struct KotlinCodeOracle;

impl KotlinCodeOracle {
    fn find(&self, type_: &Type) -> Box<dyn CodeType> {
        type_.clone().as_type().as_codetype()
    }

    /// Get the idiomatic Kotlin rendering of a class name (for enums, records, errors, etc).
    fn class_name(&self, ci: &ComponentInterface, nm: &str) -> String {
        let name = nm.to_string().to_upper_camel_case();
        // fixup errors.
        if ci.is_name_used_as_error(nm) {
            self.convert_error_suffix(&name)
        } else {
            name
        }
    }

    fn convert_error_suffix(&self, nm: &str) -> String {
        match nm.strip_suffix("Error") {
            None => nm.to_string(),
            Some(stripped) => format!("{stripped}Exception"),
        }
    }

    /// Get the idiomatic Kotlin rendering of a function name.
    fn fn_name(&self, nm: &str) -> String {
        format!("`{}`", nm.to_string().to_lower_camel_case())
    }

    /// Get the idiomatic Kotlin rendering of a variable name.
    fn var_name(&self, nm: &str) -> String {
        format!("`{}`", self.var_name_raw(nm))
    }

    /// `var_name` without the backticks.  Useful for using in `@Structure.FieldOrder`.
    pub fn var_name_raw(&self, nm: &str) -> String {
        header_escape_name(&nm.to_lower_camel_case()).unwrap()
    }

    /// Get the idiomatic Kotlin rendering of an individual enum variant.
    fn enum_variant_name(&self, nm: &str, config: &Config) -> String {
        if config.use_pascal_case_enum_class == Some(true) {
            nm.to_upper_camel_case()
        } else {
            nm.to_shouty_snake_case()
        }
    }

    /// Get the idiomatic Kotlin rendering of an FFI callback function name
    fn ffi_callback_name(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    fn ffi_callback_name_header(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    /// Get the idiomatic Kotlin rendering of an FFI struct name
    fn ffi_struct_name(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    fn ffi_struct_name_header(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    fn ffi_type_label_by_value(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            FfiType::RustBuffer(_) => format!("{}ByValue", self.ffi_type_label(ffi_type, ci)),
            FfiType::Struct(name) => format!("{}UniffiByValue", self.ffi_struct_name(name)),
            FfiType::Callback(name) => self.ffi_callback_name(name),
            _ => self.ffi_type_label(ffi_type, ci),
        }
    }

    /// FFI type name for the return type of a JNA function declaration.
    ///
    /// JNA direct mapping returns `u8` and `u16` values with the wrong signedness on some
    /// platforms, such as Android ARM32 (mozilla/uniffi-rs#2897), so they are returned as `Int`
    /// and narrowed at the call site. The upper bits of the `Int` are undefined on some ABIs, such
    /// as aarch64 (mozilla/uniffi-rs#2935).
    fn ffi_type_label_for_direct_return(
        &self,
        ffi_type: &FfiType,
        ci: &ComponentInterface,
    ) -> String {
        match ffi_type {
            FfiType::UInt8 | FfiType::UInt16 => "Int".to_string(),
            _ => self.ffi_type_label_by_value(ffi_type, ci),
        }
    }

    /// The conversion that narrows a value returned by a JNA function declared with
    /// [`Self::ffi_type_label_for_direct_return`] back to its FFI type.
    fn ffi_direct_return_narrowing(&self, ffi_type: Option<&FfiType>) -> &'static str {
        match ffi_type {
            Some(FfiType::UInt8) => ".toByte()",
            Some(FfiType::UInt16) => ".toShort()",
            _ => "",
        }
    }

    /// FFI type name to use inside structs
    ///
    /// The main requirement here is that all types must have default values or else the struct
    /// won't work in some JNA contexts.
    fn ffi_type_label_for_ffi_struct(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            // Make callbacks function pointers nullable. This matches the semantics of a C
            // function pointer better and allows for `null` as a default value.
            // NOTE: Type any used here, as native and jvm types differ.
            FfiType::Callback(name) => format!("{}?", self.ffi_callback_name(name)),
            _ => self.ffi_type_label_by_value(ffi_type, ci),
        }
    }

    /// Default values for FFI
    ///
    /// This is used to:
    ///   - Set a default return value for error results
    ///   - Set a default for structs, which JNA sometimes requires
    fn ffi_default_value(&self, ffi_type: &FfiType) -> String {
        match ffi_type {
            FfiType::UInt8 | FfiType::Int8 => "0.toByte()".to_owned(),
            FfiType::UInt16 | FfiType::Int16 => "0.toShort()".to_owned(),
            FfiType::UInt32 | FfiType::Int32 => "0".to_owned(),
            FfiType::UInt64 | FfiType::Int64 => "0.toLong()".to_owned(),
            FfiType::Float32 => "0.0f".to_owned(),
            FfiType::Float64 => "0.0".to_owned(),
            FfiType::Handle => "0.toLong()".to_owned(),
            FfiType::RustBuffer(_) => "RustBufferHelper.allocValue()".to_owned(),
            FfiType::Callback(_) => "null".to_owned(),
            FfiType::RustCallStatus => "UniffiRustCallStatusHelper.allocValue()".to_owned(),
            _ => unimplemented!("ffi_default_value: {ffi_type:?}"),
        }
    }

    fn ffi_type_label_by_reference(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            FfiType::Int8
            | FfiType::UInt8
            | FfiType::Int16
            | FfiType::UInt16
            | FfiType::Int32
            | FfiType::UInt32
            | FfiType::Int64
            | FfiType::UInt64
            | FfiType::Float32
            | FfiType::Float64
            | FfiType::Handle => format!("{}ByReference", self.ffi_type_label(ffi_type, ci)),
            // JNA structs default to ByReference
            FfiType::RustBuffer(_) | FfiType::Struct(_) => self.ffi_type_label(ffi_type, ci),
            _ => panic!("{ffi_type:?} by reference is not implemented"),
        }
    }

    fn ffi_type_label_by_mut_reference(
        &self,
        ffi_type: &FfiType,
        ci: &ComponentInterface,
    ) -> String {
        match ffi_type {
            FfiType::Int8
            | FfiType::UInt8
            | FfiType::Int16
            | FfiType::UInt16
            | FfiType::Int32
            | FfiType::UInt32
            | FfiType::Int64
            | FfiType::UInt64
            | FfiType::Float32
            | FfiType::Float64
            | FfiType::Handle => format!("{}ByReference", self.ffi_type_label(ffi_type, ci)),
            // JNA structs default to ByReference
            FfiType::RustBuffer(_) | FfiType::Struct(_) => self.ffi_type_label(ffi_type, ci),
            _ => panic!("{ffi_type:?} by reference is not implemented"),
        }
    }

    fn ffi_type_label_by_reference_header(
        &self,
        ffi_type: &FfiType,
        ci: &ComponentInterface,
    ) -> String {
        match ffi_type {
            FfiType::Int8
            | FfiType::UInt8
            | FfiType::Int16
            | FfiType::UInt16
            | FfiType::Int32
            | FfiType::UInt32
            | FfiType::Int64
            | FfiType::UInt64
            | FfiType::Float32
            | FfiType::Float64
            | FfiType::Handle => format!("{} const *", self.ffi_type_label_header(ffi_type, ci)),
            // JNA structs default to ByReference
            FfiType::RustBuffer(_) | FfiType::Struct(_) => {
                format!("{} const *", self.ffi_type_label_header(ffi_type, ci))
            }
            _ => panic!("{ffi_type:?} by reference is not implemented"),
        }
    }

    fn ffi_type_label_by_mut_reference_header(
        &self,
        ffi_type: &FfiType,
        ci: &ComponentInterface,
    ) -> String {
        match ffi_type {
            FfiType::Int8
            | FfiType::UInt8
            | FfiType::Int16
            | FfiType::UInt16
            | FfiType::Int32
            | FfiType::UInt32
            | FfiType::Int64
            | FfiType::UInt64
            | FfiType::Float32
            | FfiType::Float64
            | FfiType::Handle => format!("{} *", self.ffi_type_label_header(ffi_type, ci)),
            // JNA structs default to ByReference
            FfiType::RustBuffer(_) | FfiType::Struct(_) => {
                format!("{} *", self.ffi_type_label_header(ffi_type, ci))
            }
            _ => panic!("{ffi_type:?} by reference is not implemented"),
        }
    }

    fn ffi_type_label(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            // Note that unsigned integers in Kotlin are currently experimental, but java.nio.ByteBuffer does not
            // support them yet. Thus, we use the signed variants to represent both signed and unsigned
            // types from the component API.
            FfiType::Int8 | FfiType::UInt8 => "Byte".to_string(),
            FfiType::Int16 | FfiType::UInt16 => "Short".to_string(),
            FfiType::Int32 | FfiType::UInt32 => "Int".to_string(),
            FfiType::Int64 | FfiType::UInt64 => "Long".to_string(),
            FfiType::Float32 => "Float".to_string(),
            FfiType::Float64 => "Double".to_string(),
            FfiType::Handle => "Long".to_string(),
            FfiType::RustBuffer(maybe_external) => match maybe_external {
                Some(external_meta) if external_meta.crate_name() != ci.crate_name() => {
                    format!("RustBuffer{}", external_meta.name)
                }
                _ => "RustBuffer".to_string(),
            },
            FfiType::RustCallStatus => "UniffiRustCallStatusByValue".to_string(),
            FfiType::ForeignBytes => "ForeignBytesByValue".to_string(),
            FfiType::Callback(callback) => self.ffi_callback_name(callback),
            FfiType::Struct(name) => self.ffi_struct_name(name),
            FfiType::Reference(inner) => self.ffi_type_label_by_reference(inner, ci),
            FfiType::MutReference(inner) => self.ffi_type_label_by_mut_reference(inner, ci),
            FfiType::VoidPointer => "Pointer".to_string(),
        }
    }

    fn ffi_type_label_header(&self, ffi_type: &FfiType, ci: &ComponentInterface) -> String {
        match ffi_type {
            // Note that unsigned integers in Kotlin are currently experimental, but java.nio.ByteBuffer does not
            // support them yet. Thus, we use the signed variants to represent both signed and unsigned
            // types from the component API.
            FfiType::Int8 | FfiType::UInt8 => "int8_t".to_string(),
            FfiType::Int16 | FfiType::UInt16 => "int16_t".to_string(),
            FfiType::Int32 | FfiType::UInt32 => "int32_t".to_string(),
            FfiType::Int64 | FfiType::UInt64 => "int64_t".to_string(),
            FfiType::Float32 => "float".to_string(),
            FfiType::Float64 => "double".to_string(),
            FfiType::Handle => "int64_t".to_string(),
            FfiType::RustBuffer(maybe_external) => match maybe_external {
                Some(external_meta) if external_meta.crate_name() != ci.crate_name() => {
                    format!("RustBuffer{}", external_meta.name)
                }
                _ => "RustBuffer".to_string(),
            },
            FfiType::RustCallStatus => "UniffiRustCallStatus".to_string(),
            FfiType::ForeignBytes => "ForeignBytes".to_string(),
            FfiType::Callback(name) => self.ffi_callback_name_header(name),
            FfiType::Struct(name) => self.ffi_struct_name_header(name),
            FfiType::Reference(inner) => self.ffi_type_label_by_reference_header(inner, ci),
            FfiType::MutReference(inner) => self.ffi_type_label_by_mut_reference_header(inner, ci),
            FfiType::VoidPointer => "void *".to_string(),
        }
    }

    #[allow(clippy::only_used_in_recursion)]
    fn as_external_type(&self, ci: &ComponentInterface, as_ct: &impl AsType) -> Option<String> {
        let ty = as_ct.as_type();
        if let (true, Some(name)) = (ci.is_external(&ty), ty.name()) {
            return Some(name.to_string());
        }
        match as_ct.as_type() {
            Type::Custom { builtin, .. } => self.as_external_type(ci, &builtin),
            // `Box<T>` is transparent at the FFI level, but it is not normalized away in field
            // and argument types, so `ci.is_external()` does not see an external `T` through it.
            Type::Box { inner_type } => self.as_external_type(ci, &inner_type),
            _ => None,
        }
    }
}

trait AsCodeType {
    fn as_codetype(&self) -> Box<dyn CodeType>;
}

impl<T: AsType> AsCodeType for T {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        // Map `Type` instances to a `Box<dyn CodeType>` for that type.
        //
        // There is a companion match in `templates/Types.kt` which performs a similar function for the
        // template code.
        //
        //   - When adding additional types here, make sure to also add a match arm to the `Types.kt` template.
        //   - To keep things manageable, let's try to limit ourselves to these 2 mega-matches
        match self.as_type() {
            Type::UInt8 => Box::new(primitives::UInt8CodeType),
            Type::Int8 => Box::new(primitives::Int8CodeType),
            Type::UInt16 => Box::new(primitives::UInt16CodeType),
            Type::Int16 => Box::new(primitives::Int16CodeType),
            Type::UInt32 => Box::new(primitives::UInt32CodeType),
            Type::Int32 => Box::new(primitives::Int32CodeType),
            Type::UInt64 => Box::new(primitives::UInt64CodeType),
            Type::Int64 => Box::new(primitives::Int64CodeType),
            Type::Float32 => Box::new(primitives::Float32CodeType),
            Type::Float64 => Box::new(primitives::Float64CodeType),
            Type::Boolean => Box::new(primitives::BooleanCodeType),
            Type::String => Box::new(primitives::StringCodeType),
            Type::Bytes => Box::new(primitives::BytesCodeType),

            Type::Timestamp => Box::new(miscellany::TimestampCodeType),
            Type::Duration => Box::new(miscellany::DurationCodeType),

            Type::Enum { name, .. } => Box::new(enum_::EnumCodeType::new(name)),
            Type::Object { name, imp, .. } => Box::new(object::ObjectCodeType::new(name, imp)),
            Type::Record { name, .. } => Box::new(record::RecordCodeType::new(name)),
            Type::CallbackInterface { name, .. } => {
                Box::new(callback_interface::CallbackInterfaceCodeType::new(name))
            }
            Type::Optional { inner_type } => {
                Box::new(compounds::OptionalCodeType::new(*inner_type))
            }
            Type::Sequence { inner_type } => {
                Box::new(compounds::SequenceCodeType::new(*inner_type))
            }
            Type::Map {
                key_type,
                value_type,
            } => Box::new(compounds::MapCodeType::new(*key_type, *value_type)),
            Type::Set { inner_type } => Box::new(compounds::SetCodeType::new(*inner_type)),
            Type::Custom { name, builtin, .. } => {
                Box::new(custom::CustomCodeType::new(name, builtin.as_codetype()))
            }
            Type::Box { inner_type } => inner_type.as_codetype(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DataClassFieldType {
    Bytes,
    NullableBytes,
    NonNullableNonBytes,
    NullableNonBytes,
}

mod filters {
    use uniffi_bindgen::interface::ffi::ExternalFfiMetadata;
    use uniffi_meta::LiteralMetadata;
    use variant::VariantCodeType;

    use super::*;

    /// Convert `anyhow::Error`, `&str`, etc. to askama errors.
    ///
    /// UniFFI 0.30 removed `uniffi_bindgen::backend`. `uniffi_bindgen::to_askama_error` replaces
    /// the old helper, but it returns an error of the askama version UniFFI uses, not ours.
    pub(super) fn to_askama_error<T: ToString + ?Sized>(t: &T) -> askama::Error {
        askama::Error::Custom(Box::new(BindingsTemplateError(t.to_string())))
    }

    // Need a struct to define an error that implements std::error::Error, which neither String nor
    // anyhow::Error do.
    #[derive(Debug)]
    struct BindingsTemplateError(String);

    impl std::fmt::Display for BindingsTemplateError {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            f.write_str(&self.0)
        }
    }

    impl std::error::Error for BindingsTemplateError {}

    /// Get the FFI type of a type. This used to come from `uniffi_bindgen::backend::filters`.
    pub(super) fn ffi_type(type_: &impl AsType) -> Result<FfiType, askama::Error> {
        Ok(type_.as_type().into())
    }

    pub(super) fn type_name(
        as_ct: &impl AsCodeType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().type_label(ci))
    }

    /// The Kotlin type of `as_type`, with the types defined by crates qualified by their package.
    ///
    /// Inside a sealed class, a variant shadows a type of the same name, as in
    /// `enum Value { Amount(Amount) }` (mozilla/uniffi-rs#2698).
    pub(super) fn qualified_type_name(
        as_type: &impl AsType,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        fully_qualified_type_label(&as_type.as_type(), ci, config)
            .map_err(|err| to_askama_error(&err))
    }

    fn fully_qualified_type_label(
        ty: &Type,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String> {
        match ty {
            Type::Optional { inner_type } => Ok(format!(
                "{}?",
                fully_qualified_type_label(inner_type, ci, config)?
            )),
            // Unlike upstream, also qualify the collections, which a variant can shadow too.
            Type::Sequence { inner_type } => Ok(format!(
                "kotlin.collections.List<{}>",
                fully_qualified_type_label(inner_type, ci, config)?
            )),
            Type::Map {
                key_type,
                value_type,
            } => Ok(format!(
                "kotlin.collections.Map<{}, {}>",
                fully_qualified_type_label(key_type, ci, config)?,
                fully_qualified_type_label(value_type, ci, config)?
            )),
            Type::Set { inner_type } => Ok(format!(
                "kotlin.collections.Set<{}>",
                fully_qualified_type_label(inner_type, ci, config)?
            )),
            Type::Box { inner_type } => fully_qualified_type_label(inner_type, ci, config),
            // A local custom type configured without `type_name` names a type that the config
            // imports, so this package has no alias for it.
            Type::Custom { name, .. }
                if !ci.is_external(ty)
                    && config
                        .custom_types
                        .get(name)
                        .is_some_and(|custom| custom.type_name.is_none()) =>
            {
                Ok(KotlinCodeOracle.find(ty).type_label(ci))
            }
            Type::Enum { .. }
            | Type::Record { .. }
            | Type::Object { .. }
            | Type::CallbackInterface { .. }
            | Type::Custom { .. } => {
                let class_name = KotlinCodeOracle.find(ty).type_label(ci);
                let package_name = package_for_type(ty, ci, config)?;
                Ok(format!("{package_name}.{class_name}"))
            }
            _ => Ok(KotlinCodeOracle.find(ty).type_label(ci)),
        }
    }

    fn package_for_type(ty: &Type, ci: &ComponentInterface, config: &Config) -> Result<String> {
        if ci.is_external(ty) {
            let module_path = ty
                .module_path()
                .ok_or_else(|| anyhow::anyhow!("external type {ty:?} missing module path"))?;
            let namespace = ci.namespace_for_module_path(module_path)?;
            Ok(config.external_package_name(module_path, Some(namespace)))
        } else {
            Ok(config.package_name())
        }
    }

    // Workaround problem with impl AsCodeType for &Variant (see variant.rs).
    pub fn variant_type_name(
        v: &Variant,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(VariantCodeType { v: v.clone() }.type_label(ci))
    }

    pub(super) fn canonical_name(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().canonical_name())
    }

    pub(super) fn ffi_converter_name(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().ffi_converter_name())
    }

    pub(super) fn lower_fn(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.lower",
            as_ct.as_codetype().ffi_converter_name()
        ))
    }

    pub(super) fn allocation_size_fn(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.allocationSize",
            as_ct.as_codetype().ffi_converter_name()
        ))
    }

    pub(super) fn write_fn(
        as_ct: &impl AsType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        if let Some(external_type_name) = KotlinCodeOracle.as_external_type(ci, as_ct) {
            Ok(format!(
                "{}.write{external_type_name}",
                as_ct.as_codetype().ffi_converter_name()
            ))
        } else {
            Ok(format!(
                "{}.write",
                as_ct.as_codetype().ffi_converter_name()
            ))
        }
    }

    /// The name of the `ForeignBytes` value that `borrowed_bytes_scope_begin` creates for a
    /// borrowed byte slice argument.
    pub(super) fn borrowed_bytes_var_name(arg: &Argument) -> Result<String, askama::Error> {
        Ok(format!(
            "uniffiForeignBytes{}",
            arg.name().to_upper_camel_case()
        ))
    }

    /// Open a `uniffiWithForeignBytes` scope for each borrowed byte slice argument of a call.
    pub(super) fn borrowed_bytes_scope_begin(
        callable: impl Callable,
    ) -> Result<String, askama::Error> {
        let mut scopes = String::new();
        for arg in callable.arguments() {
            if arg.is_borrowed_bytes() {
                scopes += &format!(
                    "{}.uniffiWithForeignBytes {{ {} -> ",
                    KotlinCodeOracle.var_name(arg.name()),
                    borrowed_bytes_var_name(arg)?,
                );
            }
        }
        Ok(scopes)
    }

    /// Close the scopes opened by `borrowed_bytes_scope_begin`.
    pub(super) fn borrowed_bytes_scope_end(
        callable: impl Callable,
    ) -> Result<String, askama::Error> {
        let count = callable
            .arguments()
            .iter()
            .filter(|arg| arg.is_borrowed_bytes())
            .count();
        Ok(" }".repeat(count))
    }

    /// The name of the internal extension function that calls a method of a record, enum or
    /// error into Rust.
    ///
    /// These types are plain Kotlin classes in common code, but the FFI calls exist only in the
    /// platform code. Their members call these helpers instead, which are `expect` functions in
    /// common code. The name comes from the FFI function, so it is unique in the package.
    pub(super) fn value_method_helper(meth: &Method) -> Result<String, askama::Error> {
        Ok(meth.ffi_func().name().to_lower_camel_case())
    }

    pub(super) fn lift_fn(as_ct: &impl AsCodeType) -> Result<String, askama::Error> {
        Ok(format!("{}.lift", as_ct.as_codetype().ffi_converter_name()))
    }

    pub(super) fn read_fn(
        as_ct: &impl AsType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        if let Some(external_type_name) = KotlinCodeOracle.as_external_type(ci, as_ct) {
            Ok(format!(
                "{}.read{external_type_name}",
                as_ct.as_codetype().ffi_converter_name()
            ))
        } else {
            Ok(format!("{}.read", as_ct.as_codetype().ffi_converter_name()))
        }
    }

    pub fn render_default(
        default: &DefaultValue,
        as_ct: &impl AsType,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        as_ct
            .as_codetype()
            .default(default, ci, config)
            .map_err(|e| to_askama_error(&e))
    }

    /// Like [render_default], with the types defined by crates qualified by their package, for the
    /// default values in a sealed class, where a variant can shadow the type of the value, as in
    /// `enum Value { Amount { amount: Amount } }`.
    pub fn qualified_default(
        default: &DefaultValue,
        as_type: &impl AsType,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        qualified_default_for_type(default, &as_type.as_type(), ci, config)
            .map_err(|e| to_askama_error(&e))
    }

    fn qualified_default_for_type(
        default: &DefaultValue,
        ty: &Type,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String> {
        match (ty, default) {
            (Type::Record { .. } | Type::Object { .. }, DefaultValue::Default) => {
                Ok(format!("{}()", fully_qualified_type_label(ty, ci, config)?))
            }
            (Type::Enum { .. }, DefaultValue::Literal(Literal::Enum(variant, _))) => Ok(format!(
                "{}.{}",
                fully_qualified_type_label(ty, ci, config)?,
                KotlinCodeOracle.enum_variant_name(variant, config)
            )),
            (Type::Optional { inner_type }, DefaultValue::Literal(Literal::Some { inner })) => {
                qualified_default_for_type(inner, inner_type, ci, config)
            }
            (Type::Box { inner_type }, _) => {
                qualified_default_for_type(default, inner_type, ci, config)
            }
            // Like `CustomCodeType::default`.
            (Type::Custom { name, builtin, .. }, _) => {
                let builtin_default = qualified_default_for_type(default, builtin, ci, config)
                    .map_err(|_| {
                        anyhow::anyhow!(
                            "Unsupported default value for {}",
                            KotlinCodeOracle.find(ty).type_label(ci)
                        )
                    })?;
                Ok(match config.custom_types.get(name) {
                    Some(custom_type_config) => custom_type_config.lift(&builtin_default),
                    None => builtin_default,
                })
            }
            _ => KotlinCodeOracle.find(ty).default(default, ci, config),
        }
    }

    // Get the idiomatic Kotlin rendering of an integer.
    fn int_literal(t: &Option<Type>, base10: String) -> Result<String, askama::Error> {
        if let Some(t) = t {
            match t {
                Type::Int8 | Type::Int16 | Type::Int32 | Type::Int64 => Ok(base10),
                Type::UInt8 | Type::UInt16 | Type::UInt32 | Type::UInt64 => Ok(base10 + "u"),
                _ => Err(to_askama_error("Only ints are supported.")),
            }
        } else {
            Err(to_askama_error("Enum hasn't defined a repr"))
        }
    }

    // Get the idiomatic Kotlin rendering of an individual enum variant's discriminant
    pub fn variant_discr_literal(e: &Enum, index: &usize) -> Result<String, askama::Error> {
        let literal = e.variant_discr(*index).expect("invalid index");
        match literal {
            // Kotlin doesn't convert between signed and unsigned by default
            // so we'll need to make sure we define the type as appropriately
            LiteralMetadata::UInt(v, _, _) => int_literal(e.variant_discr_type(), v.to_string()),
            LiteralMetadata::Int(v, _, _) => int_literal(e.variant_discr_type(), v.to_string()),
            _ => Err(to_askama_error("Only ints are supported.")),
        }
    }

    pub fn should_generate_equals_hash_code_record(record: &Record) -> Result<bool, askama::Error> {
        Ok(record.fields().iter().any(|f| {
            matches!(
                as_data_class_field_type(f),
                Ok(DataClassFieldType::Bytes | DataClassFieldType::NullableBytes)
            )
        }))
    }

    pub fn should_generate_equals_hash_code_enum_variant(
        variant: &Variant,
    ) -> Result<bool, askama::Error> {
        Ok(variant.fields().iter().any(|f| {
            matches!(
                as_data_class_field_type(f),
                Ok(DataClassFieldType::Bytes | DataClassFieldType::NullableBytes)
            )
        }))
    }

    pub fn as_data_class_field_type(
        as_ct: &impl AsType,
    ) -> Result<DataClassFieldType, askama::Error> {
        fn as_bytes_field_type_inner(type_: &Type) -> DataClassFieldType {
            match type_ {
                Type::Bytes => DataClassFieldType::Bytes,
                Type::Box { inner_type } => as_bytes_field_type_inner(inner_type),
                Type::Optional { inner_type } => match as_bytes_field_type_inner(inner_type) {
                    DataClassFieldType::Bytes | DataClassFieldType::NullableBytes => {
                        DataClassFieldType::NullableBytes
                    }
                    DataClassFieldType::NonNullableNonBytes
                    | DataClassFieldType::NullableNonBytes => DataClassFieldType::NullableNonBytes,
                },
                _ => DataClassFieldType::NonNullableNonBytes,
            }
        }
        Ok(as_bytes_field_type_inner(&as_ct.as_type()))
    }

    // `in_progress` holds the records and enums being checked further up the stack. Since UniFFI
    // 0.32, types can be recursive (through `Box`, `Vec`, `Option`, ...), and a reference back to
    // a type in progress does not decide anything: the outer check covers its other fields.
    fn serializable_type(
        type_: &Type,
        ci: &ComponentInterface,
        in_progress: &mut HashSet<String>,
    ) -> Result<bool, askama::Error> {
        Ok(match type_ {
            Type::Object { .. } | Type::CallbackInterface { .. } => false,
            Type::Record { name, .. } => serializable_record_inner(
                ci.get_record_definition(name)
                    .ok_or_else(|| to_askama_error(&format!("could not find record '{name}'")))?,
                ci,
                in_progress,
            )?,
            Type::Enum { name, .. } => serializable_enum_inner(
                ci.get_enum_definition(name)
                    .ok_or_else(|| to_askama_error(&format!("could not find enum '{name}'")))?,
                ci,
                in_progress,
            )?,
            Type::Optional { inner_type }
            | Type::Sequence { inner_type }
            | Type::Set { inner_type }
            | Type::Box { inner_type } => serializable_type(inner_type, ci, in_progress)?,
            Type::Map {
                key_type,
                value_type,
            } => {
                serializable_type(key_type, ci, in_progress)?
                    && serializable_type(value_type, ci, in_progress)?
            }
            // Assume a custom type using a serializable type is also serializable.
            Type::Custom { builtin, .. } => serializable_type(builtin, ci, in_progress)?,
            _ => true,
        })
    }

    /// Run `check` for the type named `name`, unless that type is already being checked.
    fn check_serializable_once(
        name: &str,
        in_progress: &mut HashSet<String>,
        check: impl FnOnce(&mut HashSet<String>) -> Result<bool, askama::Error>,
    ) -> Result<bool, askama::Error> {
        if !in_progress.insert(name.to_string()) {
            return Ok(true);
        }
        let result = check(in_progress);
        in_progress.remove(name);
        result
    }

    pub fn serializable_record(
        record: &Record,
        ci: &ComponentInterface,
    ) -> Result<bool, askama::Error> {
        serializable_record_inner(record, ci, &mut HashSet::new())
    }

    fn serializable_record_inner(
        record: &Record,
        ci: &ComponentInterface,
        in_progress: &mut HashSet<String>,
    ) -> Result<bool, askama::Error> {
        check_serializable_once(record.name(), in_progress, |in_progress| {
            for field in record.fields() {
                if !serializable_type(&field.as_type(), ci, in_progress)? {
                    return Ok(false);
                }
            }
            Ok(true)
        })
    }

    pub fn serializable_enum(enum_: &Enum, ci: &ComponentInterface) -> Result<bool, askama::Error> {
        serializable_enum_inner(enum_, ci, &mut HashSet::new())
    }

    fn serializable_enum_inner(
        enum_: &Enum,
        ci: &ComponentInterface,
        in_progress: &mut HashSet<String>,
    ) -> Result<bool, askama::Error> {
        if ci.is_name_used_as_error(enum_.name()) {
            return Ok(false);
        }

        check_serializable_once(enum_.name(), in_progress, |in_progress| {
            if enum_.is_flat() {
                let Some(variant_discr_type) = enum_.variant_discr_type() else {
                    return Ok(true);
                };
                return serializable_type(variant_discr_type, ci, in_progress);
            }

            // Unlike records or enum variants, if any of the variants are serializable, the
            // enum can be marked as serializable.
            for variant in enum_.variants() {
                if serializable_enum_variant_inner(variant, ci, in_progress)? {
                    return Ok(true);
                }
            }
            Ok(false)
        })
    }

    pub fn serializable_enum_variant(
        variant: &Variant,
        ci: &ComponentInterface,
    ) -> Result<bool, askama::Error> {
        serializable_enum_variant_inner(variant, ci, &mut HashSet::new())
    }

    fn serializable_enum_variant_inner(
        variant: &Variant,
        ci: &ComponentInterface,
        in_progress: &mut HashSet<String>,
    ) -> Result<bool, askama::Error> {
        for field in variant.fields() {
            if !serializable_type(&field.as_type(), ci, in_progress)? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    pub fn ffi_type_name_by_value(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label_by_value(type_, ci))
    }

    pub fn ffi_type_name(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label(type_, ci))
    }

    pub fn ffi_type_name_for_direct_return(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label_for_direct_return(type_, ci))
    }

    /// Narrow the result of a call to `func` on JVM and Android, where `UniffiLib` declares the
    /// return type with `ffi_type_name_for_direct_return`.
    pub fn ffi_direct_return_narrowing(
        func: &FfiFunction,
        module_name: &str,
    ) -> Result<String, askama::Error> {
        Ok(direct_return_narrowing(func.return_type(), module_name).to_string())
    }

    fn direct_return_narrowing(ffi_type: Option<&FfiType>, module_name: &str) -> &'static str {
        match module_name {
            "jvm" | "android" => KotlinCodeOracle.ffi_direct_return_narrowing(ffi_type),
            _ => "",
        }
    }

    pub fn ffi_as_callback(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<Option<FfiCallbackFunction>, askama::Error> {
        let FfiType::Callback(callback_name) = type_ else {
            return Ok(None);
        };

        for def in ci.ffi_definitions() {
            let FfiDefinition::CallbackFunction(callback) = def else {
                continue;
            };
            if callback.name() != callback_name {
                continue;
            }
            return Ok(Some(callback));
        }

        Err(to_askama_error(&format!(
            "could not find FFI callback '{callback_name}'"
        )))
    }

    /// Kotlin/Native's cinterop ignores nullability of parameters of callback definitions in
    /// headers. We need to determine whether a callback pointer needs to be casted before being
    /// passed to a FFI function.
    pub fn ffi_callback_needs_casting_native(
        ffi_callback: &FfiCallbackFunction,
    ) -> Result<bool, askama::Error> {
        Ok(ffi_callback.has_rust_call_status_arg()
            || ffi_callback.arguments().iter().any(|a| {
                matches!(
                    a.type_(),
                    FfiType::RustBuffer(_)
                        | FfiType::ForeignBytes
                        | FfiType::Callback(_)
                        | FfiType::Struct(_)
                )
            }))
    }

    /// Convert a local RustBuffer to an external RustBuffer.
    pub fn ffi_cast_to_external_rust_buffer_if_needed(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let FfiType::RustBuffer(Some(metadata)) = type_ else {
            return Ok(String::new());
        };
        if metadata.crate_name() == ci.crate_name() {
            return Ok(String::new());
        }
        Ok(format!(".as{}()", metadata.name))
    }

    /// Convert an external RustBuffer to a local RustBuffer.
    pub fn ffi_cast_to_local_rust_buffer_if_needed(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let FfiType::RustBuffer(Some(metadata)) = type_ else {
            return Ok(String::new());
        };
        if metadata.crate_name() == ci.crate_name() {
            return Ok(String::new());
        }
        Ok(format!(".from{}ToLocal()", metadata.name))
    }

    /// Append a `_` if the name is a valid c/c++ keyword
    pub fn header_escape_name(nm: &str) -> Result<String, askama::Error> {
        if CPP_KEYWORDS.contains(&nm) {
            Ok(format!("{nm}_"))
        } else {
            Ok(nm.to_owned())
        }
    }

    pub fn header_ffi_type_name(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label_header(type_, ci))
    }

    pub fn ffi_type_name_for_ffi_struct(
        type_: &FfiType,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_type_label_for_ffi_struct(type_, ci))
    }

    pub fn ffi_default_value(type_: FfiType) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_default_value(&type_))
    }

    /// Get the idiomatic Kotlin rendering of a function name.
    pub fn class_name<S: AsRef<str>>(
        nm: S,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.class_name(ci, nm.as_ref()))
    }

    /// Get the idiomatic Kotlin rendering of a function name.
    pub fn fn_name<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.fn_name(nm.as_ref()))
    }

    /// Get the idiomatic Kotlin rendering of a variable name.
    pub fn var_name<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.var_name(nm.as_ref()))
    }

    /// Get the idiomatic Kotlin rendering of a variable name.
    pub fn var_name_raw<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.var_name_raw(nm.as_ref()))
    }

    /// Get a String representing the name used for an individual enum variant.
    pub fn variant_name(v: &Variant, config: &Config) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.enum_variant_name(v.name(), config))
    }

    pub fn error_variant_name(v: &Variant) -> Result<String, askama::Error> {
        let name = v.name().to_string().to_upper_camel_case();
        Ok(KotlinCodeOracle.convert_error_suffix(&name))
    }

    /// Get the idiomatic Kotlin rendering of an FFI callback function name
    pub fn ffi_callback_name<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_callback_name(nm.as_ref()))
    }

    /// Get the idiomatic Kotlin rendering of an FFI struct name
    pub fn ffi_struct_name<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(KotlinCodeOracle.ffi_struct_name(nm.as_ref()))
    }

    pub fn async_poll(
        callable: impl Callable,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_poll(ci);
        Ok(format!(
            "{{ future, callback, continuation -> UniffiLib.{ffi_func}(future, callback, continuation) }}"
        ))
    }

    pub fn async_complete(
        callable: impl Callable,
        ci: &ComponentInterface,
        module_name: &str,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_complete(ci);
        let narrowing = direct_return_narrowing(
            callable.return_type().map(FfiType::from).as_ref(),
            module_name,
        );
        let call = format!("UniffiLib.{ffi_func}(future, continuation){narrowing}");
        // May need to convert the RustBuffer from our package to the RustBuffer of the external package.
        let call = match callable.return_type() {
            Some(return_type) => {
                match FfiType::from(return_type) {
                    FfiType::RustBuffer(Some(external_meta))
                        if external_meta.crate_name() != ci.crate_name() =>
                    {
                        let ExternalFfiMetadata { name, .. } = external_meta;
                        let suffix = KotlinCodeOracle.class_name(ci, &name);
                        format!("{call}.let {{ RustBuffer{suffix}ByValue(it.capacity, it.len, it.data) }}")
                    }
                    _ => call,
                }
            }
            _ => call,
        };
        Ok(format!("{{ future, continuation -> {call} }}"))
    }

    pub fn async_free(
        callable: impl Callable,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_free(ci);
        Ok(format!("{{ future -> UniffiLib.{ffi_func}(future) }}"))
    }

    pub fn async_cancel(
        callable: impl Callable,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_cancel(ci);
        Ok(format!("{{ future -> UniffiLib.{ffi_func}(future) }}"))
    }

    /// Remove the "`" chars we put around function/variable names
    ///
    /// These are used to avoid name clashes with kotlin identifiers, but sometimes you want to
    /// render the name unquoted.  One example is the message property for errors where we want to
    /// display the name for the user.
    pub fn unquote<S: AsRef<str>>(nm: S) -> Result<String, askama::Error> {
        Ok(nm.as_ref().trim_matches('`').to_string())
    }

    /// Get the idiomatic Kotlin rendering of docstring
    pub fn docstring<S: AsRef<str>>(docstring: S, spaces: &i32) -> Result<String, askama::Error> {
        let middle = textwrap::indent(&textwrap::dedent(docstring.as_ref()), " * ");
        let wrapped = format!("/**\n{middle}\n */");

        let spaces = usize::try_from(*spaces).unwrap_or_default();
        Ok(textwrap::indent(&wrapped, &" ".repeat(spaces)))
    }

    pub fn repeat(string: &str, n: &i32) -> Result<String, askama::Error> {
        let n = usize::try_from(*n).unwrap_or_default();
        Ok(string.repeat(n))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uniffi_meta::{
        CustomTypeMetadata, DefaultValueMetadata, EnumMetadata, EnumShape, FieldMetadata,
        FnMetadata, FnParamMetadata, LiteralMetadata, Metadata, MethodMetadata, ObjectImpl,
        ObjectTraitImplMetadata, TraitKind, VariantMetadata,
    };

    const TEST_CONFIG: &str = r#"
        package_name = "rustine_test"
        cdylib_name = "rustine_test"
        kotlin_multiplatform = true
        kotlin_targets = ["jvm", "native", "stub"]
    "#;

    /// Parse a UDL file. UDL can't declare flat errors with uniffi traits, so the errors named in
    /// `flat_errors` are made flat. `extra` adds metadata that UDL can't declare.
    fn ci_from_udl(
        udl: &str,
        flat_errors: &[&str],
        extra: Vec<Metadata>,
    ) -> Result<ComponentInterface> {
        let mut group = uniffi_udl::parse_udl(udl, "rustine_test")?;
        group.items = group
            .items
            .into_iter()
            .map(|item| match item {
                Metadata::Enum(mut e) if flat_errors.contains(&e.name.as_str()) => {
                    e.shape = EnumShape::Error { flat: true };
                    Metadata::Enum(e)
                }
                item => item,
            })
            .chain(extra)
            .collect();
        ComponentInterface::from_metadata(group)
    }

    /// Generate the bindings of `ci` with the test config, followed by `extra_config`.
    ///
    /// Like UDL mode, this doesn't give `ci` the component interfaces of other crates.
    fn generate(mut ci: ComponentInterface, extra_config: &str) -> Result<MultiplatformBindings> {
        ci.derive_ffi_funcs()?;
        let config: Config = toml::from_str(&format!("{TEST_CONFIG}{extra_config}"))?;
        generate_bindings(&config, &ci)
    }

    fn generate_from_udl(udl: &str, flat_errors: &[&str]) -> Result<MultiplatformBindings> {
        generate(ci_from_udl(udl, flat_errors, vec![])?, "")
    }

    fn function(name: &str, throws: Option<Type>) -> Metadata {
        Metadata::Func(FnMetadata {
            module_path: "rustine_test".to_string(),
            name: name.to_string(),
            orig_name: None,
            is_async: false,
            inputs: vec![],
            return_type: None,
            throws,
            checksum: Some(0),
            docstring: None,
        })
    }

    /// Port of upstream's test of mozilla/uniffi-rs#2897 and #2935.
    ///
    /// JNA returns u8 and u16 values with the wrong signedness or undefined upper bits on some
    /// platforms, so they are returned as `Int`, and the checksum checks mask the result.
    #[test]
    fn checksum_functions_use_direct_return_carriers() {
        let ci = ComponentInterface::from_webidl(
            r#"
            namespace rustine_test {
                u16 get_value();
            };
            "#,
            "rustine_test",
        )
        .unwrap();
        let checksum = ci.get_function_definition("get_value").unwrap().checksum();
        let bindings = generate(ci, "").unwrap();

        let jvm = bindings.jvm.unwrap();
        assert!(
            jvm.contains("external fun uniffi_rustine_test_checksum_func_get_value(\n    ): Int\n"),
            "checksum functions should use Int as the JNA direct return carrier"
        );
        assert!(
            jvm.contains(&format!(
                "if ((uniffi_rustine_test_checksum_func_get_value() and 0xFFFF) != {checksum}) {{"
            )),
            "checksum comparisons should mask the widened Int carrier"
        );
        // Rustine narrows u8 and u16 results at the call site, where upstream lifts an `Int`.
        assert!(jvm.contains(
            "external fun uniffi_rustine_test_fn_func_get_value(\n        \
             uniffiCallStatus: UniffiRustCallStatus,\n    ): Int\n"
        ));
        assert!(jvm.contains(
            "UniffiLib.uniffi_rustine_test_fn_func_get_value(\n            \
             uniffiRustCallStatus,\n        ).toShort()\n"
        ));

        // Kotlin/Native returns the checksum as a `Short`, so the check masks its sign extension.
        let native = bindings.native.unwrap();
        assert!(
            native.contains("fun uniffi_rustine_test_checksum_func_get_value(\n    ): Short = ")
        );
        assert!(native.contains(&format!(
            "if ((uniffi_rustine_test_checksum_func_get_value().toInt() and 0xFFFF) != {checksum}) {{"
        )));
    }

    #[test]
    fn omitted_checksums_are_not_checked() {
        let udl = r#"
            namespace rustine_test {
                u16 get_value();
            };
        "#;
        let bindings = generate(
            ci_from_udl(udl, &[], vec![]).unwrap(),
            "omit_checksums = true\n",
        )
        .unwrap();
        for platform in [bindings.jvm.unwrap(), bindings.native.unwrap()] {
            assert!(!platform.contains("uniffiCheckApiChecksums"));
            assert!(platform.contains("uniffiCheckContractApiVersion()"));
        }
    }

    #[test]
    fn rejects_borrowed_bytes_that_kotlin_cannot_pass() {
        let message = |name: &str, reason: &str| {
            format!(
                "`{name}` takes the borrowed byte slice `buf` (`&[u8]` or `[ByRef] bytes`), which \
                 is not supported in {reason}. Take a `Vec<u8>` (`bytes`) instead."
            )
        };

        // Synchronous calls into Rust can borrow them.
        let bindings = generate_from_udl(
            r#"
            namespace rustine_test {
                u32 sum([ByRef] bytes buf);
            };
            interface Summer {
                constructor([ByRef] bytes buf);
                u32 sum([ByRef] bytes buf);
            };
            "#,
            &[],
        )
        .unwrap();
        assert!(bindings
            .common
            .contains("public expect fun `sum`(`buf`: kotlin.ByteArray): kotlin.UInt"));

        let err = generate_from_udl(
            r#"
            namespace rustine_test {
                [Async]
                u32 sum([ByRef] bytes buf);
            };
            "#,
            &[],
        )
        .err()
        .unwrap();
        assert!(
            err.to_string()
                .contains(&message("sum", "async functions, methods and constructors")),
            "{err}"
        );

        let err = generate_from_udl(
            r#"
            namespace rustine_test {};
            interface Summer {
                [Async]
                u32 sum([ByRef] bytes buf);
            };
            "#,
            &[],
        )
        .err()
        .unwrap();
        assert!(
            err.to_string().contains(&message(
                "Summer::sum",
                "async functions, methods and constructors"
            )),
            "{err}"
        );

        let kotlin_implementations = "methods of callback interfaces or traits that Kotlin can \
                                      implement";
        let err = generate_from_udl(
            r#"
            namespace rustine_test {};
            callback interface Summer {
                u32 sum([ByRef] bytes buf);
            };
            "#,
            &[],
        )
        .err()
        .unwrap();
        assert!(
            err.to_string()
                .contains(&message("Summer::sum", kotlin_implementations)),
            "{err}"
        );

        let err = generate_from_udl(
            r#"
            namespace rustine_test {};
            [Trait, WithForeign]
            interface Summer {
                u32 sum([ByRef] bytes buf);
            };
            "#,
            &[],
        )
        .err()
        .unwrap();
        assert!(
            err.to_string()
                .contains(&message("Summer::sum", kotlin_implementations)),
            "{err}"
        );

        // Only Rust implements this trait, so Kotlin only calls it.
        generate_from_udl(
            r#"
            namespace rustine_test {};
            [Trait]
            interface Summer {
                u32 sum([ByRef] bytes buf);
            };
            "#,
            &[],
        )
        .unwrap();
    }

    #[test]
    fn rejects_methods_of_flat_errors() {
        let udl = r#"
            namespace rustine_test {
                [Throws=FlatError]
                void fails();
            };
            [Error]
            interface FlatError { A(); B(); };
        "#;
        let method = Metadata::Method(MethodMetadata {
            module_path: "rustine_test".to_string(),
            self_name: "FlatError".to_string(),
            name: "describe".to_string(),
            orig_name: None,
            is_async: false,
            inputs: vec![],
            return_type: Some(Type::String),
            throws: None,
            takes_self_by_arc: false,
            checksum: Some(0),
            docstring: None,
        });

        // The same error with fields can be passed back to Rust.
        let bindings = generate(ci_from_udl(udl, &[], vec![method.clone()]).unwrap(), "").unwrap();
        assert!(bindings
            .common
            .contains("public fun `describe`(): kotlin.String {"));

        let err = generate(ci_from_udl(udl, &["FlatError"], vec![method]).unwrap(), "")
            .err()
            .unwrap();
        assert!(
            err.to_string().contains(
                "Kotlin bindings do not support methods on flat error `FlatError`, such as \
                 `describe`, since flat errors can't be passed back to Rust. Remove `flat_error`, \
                 or add \"FlatError.describe\" to the `exclude` list in uniffi.toml."
            ),
            "{err}"
        );
    }

    #[test]
    fn rejects_custom_errors_over_types_that_are_not_errors() {
        let custom_error = |builtin: &str| {
            vec![
                Metadata::CustomType(CustomTypeMetadata {
                    module_path: "rustine_test".to_string(),
                    name: "CustomError".to_string(),
                    orig_name: None,
                    builtin: Type::Enum {
                        module_path: "rustine_test".to_string(),
                        name: builtin.to_string(),
                    },
                    docstring: None,
                }),
                function(
                    "fails_with_custom_error",
                    Some(Type::Custom {
                        module_path: "rustine_test".to_string(),
                        name: "CustomError".to_string(),
                        builtin: Box::new(Type::Enum {
                            module_path: "rustine_test".to_string(),
                            name: builtin.to_string(),
                        }),
                    }),
                ),
            ]
        };
        let udl = r#"
            namespace rustine_test {
                [Throws=ThrownError]
                void fails();
            };
            [Error]
            enum ThrownError { "A" };
            enum NotThrown { "A" };
        "#;

        // A custom type over an error can be thrown (mozilla/uniffi-rs#2658).
        let bindings = generate(
            ci_from_udl(udl, &[], custom_error("ThrownError")).unwrap(),
            "",
        )
        .unwrap();
        assert!(bindings
            .common
            .contains("public typealias CustomException = ThrownException"));
        for platform in [bindings.jvm.unwrap(), bindings.native.unwrap()] {
            assert!(platform.contains(
                "public object CustomExceptionErrorHandler : \
                 UniffiRustCallStatusErrorHandler<CustomException> {"
            ));
        }

        let err = generate(
            ci_from_udl(udl, &[], custom_error("NotThrown")).unwrap(),
            "",
        )
        .err()
        .unwrap();
        assert!(
            err.to_string().contains(
                "The custom type `CustomError` is used as an error, but its builtin type \
                 `NotThrown` is not."
            ),
            "{err}"
        );
    }

    /// In UDL mode, the component interfaces of other crates are unknown. Upstream fails to name
    /// the traits of other crates then; the type metadata tells whether Kotlin can implement them.
    #[test]
    fn names_traits_of_unknown_crates() {
        let udl = r#"
            namespace rustine_test {};
            interface RustOnlyImpl {};
            interface WithForeignImpl {};
        "#;
        let trait_impl = |name: &str, trait_name: &str, kind: TraitKind| {
            Metadata::ObjectTraitImpl(ObjectTraitImplMetadata {
                ty: Type::Object {
                    module_path: "rustine_test".to_string(),
                    name: name.to_string(),
                    imp: ObjectImpl::Struct,
                },
                trait_ty: Type::Object {
                    module_path: "other_crate".to_string(),
                    name: trait_name.to_string(),
                    imp: ObjectImpl::Trait(kind),
                },
            })
        };
        let ci = ci_from_udl(
            udl,
            &[],
            vec![
                trait_impl("RustOnlyImpl", "RustOnlyTrait", TraitKind::RustOnly),
                trait_impl("WithForeignImpl", "WithForeignTrait", TraitKind::Both),
            ],
        )
        .unwrap();
        let bindings = generate(ci, "[external_packages]\nother_crate = \"other.pkg\"\n").unwrap();
        let common = &bindings.common;
        assert!(
            common.contains(
                "public expect open class RustOnlyImpl: Disposable, RustOnlyImplInterface, \
                 other.pkg.RustOnlyTraitInterface {"
            ),
            "{common}"
        );
        assert!(
            common.contains(
                "public expect open class WithForeignImpl: Disposable, WithForeignImplInterface, \
                 other.pkg.WithForeignTrait {"
            ),
            "{common}"
        );
    }

    #[test]
    fn flat_enums_implement_display_only() {
        let bindings = generate_from_udl(
            r#"
            namespace rustine_test {};
            [Enum, Traits=(Display)]
            interface FlatEnum { A(); B(); };
            "#,
            &[],
        )
        .unwrap();
        assert!(bindings.common.contains("public enum class FlatEnum {"));
        assert!(bindings
            .common
            .contains("    override fun toString(): kotlin.String {"));

        let err = generate_from_udl(
            r#"
            namespace rustine_test {};
            [Enum, Traits=(Eq)]
            interface FlatEnum { A(); B(); };
            "#,
            &[],
        )
        .err()
        .unwrap();
        assert!(err
            .to_string()
            .contains("do not support exporting Eq/Ord/Hash for flat enum `FlatEnum`"));
    }

    #[test]
    fn flat_errors_reject_ord() {
        let udl = |traits: &str| {
            format!(
                r#"
                namespace rustine_test {{
                    [Throws=FlatError]
                    void fails();
                }};
                [Error, Traits=({traits})]
                interface FlatError {{ A(); B(); }};
                "#
            )
        };
        // Like upstream, flat errors implement no uniffi traits.
        let bindings = generate_from_udl(&udl("Display, Eq, Hash"), &["FlatError"]).unwrap();
        assert!(!bindings.common.contains("override fun toString()"));
        assert!(!bindings.common.contains("override fun equals("));

        let err = generate_from_udl(&udl("Ord"), &["FlatError"])
            .err()
            .unwrap();
        assert!(err
            .to_string()
            .contains("do not support exporting Ord for flat error `FlatError`"));
    }

    #[test]
    fn sealed_classes_implement_traits_once() {
        let bindings = generate_from_udl(
            r#"
            namespace rustine_test {};
            [Enum, Traits=(Debug, Eq, Hash, Ord)]
            interface SealedEnum {
                A();
                B(i32 value);
            };
            "#,
            &[],
        )
        .unwrap();
        let common = &bindings.common;
        assert!(common.contains("public sealed class SealedEnum: Comparable<SealedEnum>"));
        for member in [
            "final override fun toString(): kotlin.String {",
            "final override fun equals(other: kotlin.Any?): kotlin.Boolean {",
            "final override fun hashCode(): kotlin.Int {",
            "final override fun compareTo(other: SealedEnum): kotlin.Int {",
        ] {
            assert_eq!(common.matches(member).count(), 1, "{member}");
        }
        assert!(bindings
            .jvm
            .unwrap()
            .contains("internal actual fun SealedEnum."));
        assert!(bindings
            .stub
            .unwrap()
            .contains("internal actual fun SealedEnum."));
    }

    /// Inside a sealed class, a variant shadows the type of a default value of the same name.
    #[test]
    fn qualifies_default_values_in_sealed_classes() {
        let module_path = "rustine_test".to_string();
        let amount = Type::Record {
            module_path: module_path.clone(),
            name: "Amount".to_string(),
        };
        let color = Type::Enum {
            module_path: module_path.clone(),
            name: "Color".to_string(),
        };
        let field = |name: &str, ty: &Type, default| FieldMetadata {
            name: name.to_string(),
            orig_name: None,
            ty: ty.clone(),
            default: Some(default),
            docstring: None,
        };
        let variant = |name: &str, fields| VariantMetadata {
            name: name.to_string(),
            orig_name: None,
            discr: None,
            fields,
            docstring: None,
        };
        let red =
            || DefaultValueMetadata::Literal(LiteralMetadata::Enum("Red".into(), color.clone()));
        let extra = vec![
            Metadata::Enum(EnumMetadata {
                module_path: module_path.clone(),
                name: "Value".to_string(),
                orig_name: None,
                shape: EnumShape::Enum,
                variants: vec![
                    variant(
                        "Amount",
                        vec![field("amount", &amount, DefaultValueMetadata::Default)],
                    ),
                    variant("Color", vec![field("color", &color, red())]),
                ],
                discr_type: None,
                non_exhaustive: false,
                remote: false,
                docstring: None,
            }),
            Metadata::Method(MethodMetadata {
                module_path: module_path.clone(),
                self_name: "Value".to_string(),
                name: "replace".to_string(),
                orig_name: None,
                is_async: false,
                inputs: vec![
                    FnParamMetadata {
                        name: "amount".to_string(),
                        ty: amount.clone(),
                        by_ref: false,
                        optional: false,
                        default: Some(DefaultValueMetadata::Default),
                    },
                    FnParamMetadata {
                        name: "color".to_string(),
                        ty: color.clone(),
                        by_ref: false,
                        optional: false,
                        default: Some(red()),
                    },
                ],
                return_type: None,
                throws: None,
                takes_self_by_arc: false,
                checksum: Some(0),
                docstring: None,
            }),
        ];
        let udl = r#"
            namespace rustine_test {};
            dictionary Amount { u64 value = 0; };
            enum Color { "Red", "Green" };
        "#;
        let bindings = generate(ci_from_udl(udl, &[], extra).unwrap(), "").unwrap();
        let common = &bindings.common;
        for expected in [
            "`amount`: rustine_test.Amount = rustine_test.Amount()",
            "`color`: rustine_test.Color = rustine_test.Color.RED",
        ] {
            // The field of the variant and the argument of the method.
            assert_eq!(common.matches(expected).count(), 2, "{expected}\n{common}");
        }
    }
}
