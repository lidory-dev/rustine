/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

//! Renaming items with the `rename` table of the configs, like UniFFI 0.31 and later do.
//!
//! The component interfaces are renamed with `uniffi_bindgen::interface::rename`, like UniFFI's
//! Kotlin bindings, before the FFI functions are derived. The FFI symbols and the checksums keep
//! the Rust names: `BindgenLoader` computes the checksums of UDL items before the renames.
//!
//! Upstream's renamer misses a few places, where the bindings would reference the old names:
//! - It renames a type and the types it contains with the table of the same crate: the crate of
//!   the outermost named type, or the current crate. Types from other crates nested in an
//!   `Option`, a collection or a custom type are renamed with the wrong table. The renamer also
//!   doesn't look inside a `Box`. These types are protected from the renamer and renamed with the
//!   table of their own crate here.
//! - It doesn't rename the traits that objects implement, which are renamed here with the table
//!   of the trait's crate.
//! - It renames the names of errors with the table of the current crate. The names of errors from
//!   other crates are renamed here with the table of their own crate.
//!
//! Other places can't be renamed from outside `uniffi_bindgen`, so generation fails when a
//! rename would apply to them (see [check_renames]).

use std::collections::{HashMap, HashSet};

use anyhow::{bail, Result};
use uniffi_bindgen::interface::{
    rename, Argument, AsType, Callable, CallbackInterface, Constructor, DefaultValue, Enum, Field,
    Function, Literal, Method, Object, Record, Type, UniffiTraitMethods,
};
use uniffi_bindgen::{Component, ComponentInterface, VisitMut};

use crate::gen_kotlin_multiplatform::Config;

/// The `rename` tables of the crates, keyed by crate name.
type CrateRenames = HashMap<String, toml::Table>;

/// Rename the items of every component with the `rename` table of the crate defining them.
pub(crate) fn apply_renames(components: &mut [Component<Config>]) -> Result<()> {
    let renames: CrateRenames = components
        .iter()
        .filter(|c| !c.config.rename.is_empty())
        .map(|c| (c.ci.crate_name().to_string(), c.config.rename.clone()))
        .collect();
    if renames.is_empty() {
        return Ok(());
    }

    for c in &*components {
        check_renames(&c.ci, &renames)?;
    }
    for c in components {
        let external_errors = external_errors(&c.ci);
        c.ci.visit_mut(&ProtectTypes {
            this_crate: c.ci.crate_name().to_string(),
            external_errors: &external_errors,
        });
        rename(&mut c.ci, &renames);
        c.ci.visit_mut(&RenameFixups {
            renames: &renames,
            external_errors: &external_errors,
        });
    }
    Ok(())
}

/// The new name of the item named `key` in the crate `crate_name`.
fn new_name<'a>(renames: &'a CrateRenames, crate_name: &str, key: &str) -> Option<&'a str> {
    renames.get(crate_name)?.get(key)?.as_str()
}

/// The new name of a named type, which it gets from the table of its own crate.
fn new_type_name<'a>(renames: &'a CrateRenames, ty: &Type) -> Option<&'a str> {
    new_name(renames, ty.crate_name()?, ty.name()?)
}

fn set_type_name(ty: &mut Type, new_name: String) {
    match ty {
        Type::Object { name, .. }
        | Type::Record { name, .. }
        | Type::Enum { name, .. }
        | Type::CallbackInterface { name, .. }
        | Type::Custom { name, .. } => *name = new_name,
        _ => (),
    }
}

/// The types directly contained in `ty`.
fn nested_types_mut(ty: &mut Type) -> Vec<&mut Type> {
    match ty {
        Type::Optional { inner_type }
        | Type::Sequence { inner_type }
        | Type::Set { inner_type }
        | Type::Box { inner_type } => vec![inner_type],
        Type::Map {
            key_type,
            value_type,
        } => vec![key_type, value_type],
        Type::Custom { builtin, .. } => vec![builtin],
        _ => vec![],
    }
}

/// Rename `ty` and the types it contains with the table of their own crate.
fn rename_type(ty: &mut Type, renames: &CrateRenames) {
    if let Some(new_name) = new_type_name(renames, ty) {
        set_type_name(ty, new_name.to_string());
    }
    for nested in nested_types_mut(ty) {
        rename_type(nested, renames);
    }
}

/// Appended to the names of the types that the renamer must not rename. No rename table names a
/// type like this.
const PROTECTED_SUFFIX: char = '\0';

/// Protect the types in `ty` that the renamer would rename with the table of another crate. It
/// renames them with the table of `renamer_crate`, and doesn't look inside a `Box`.
fn protect_type(ty: &mut Type, renamer_crate: Option<&str>) {
    if let (Some(name), Some(crate_name)) = (ty.name(), ty.crate_name()) {
        if renamer_crate != Some(crate_name) {
            set_type_name(ty, format!("{name}{PROTECTED_SUFFIX}"));
        }
    }
    let renamer_crate = match ty {
        Type::Box { .. } => None,
        _ => renamer_crate,
    };
    for nested in nested_types_mut(ty) {
        protect_type(nested, renamer_crate);
    }
}

/// Rename the types that [protect_type] protected with the table of their own crate.
fn rename_protected_type(ty: &mut Type, renames: &CrateRenames) {
    if let Some(name) = ty
        .name()
        .and_then(|name| name.strip_suffix(PROTECTED_SUFFIX))
    {
        let name = name.to_string();
        let new_name = ty
            .crate_name()
            .and_then(|crate_name| new_name(renames, crate_name, &name))
            .map_or(name, ToString::to_string);
        set_type_name(ty, new_name);
    }
    for nested in nested_types_mut(ty) {
        rename_protected_type(nested, renames);
    }
}

/// Call `f` for `ty` and every type it contains, including the types inside a `Box`.
fn for_each_type<'a>(ty: &'a Type, f: &mut impl FnMut(&'a Type) -> Result<()>) -> Result<()> {
    f(ty)?;
    match ty {
        Type::Optional { inner_type }
        | Type::Sequence { inner_type }
        | Type::Set { inner_type }
        | Type::Box { inner_type } => for_each_type(inner_type, f),
        Type::Map {
            key_type,
            value_type,
        } => {
            for_each_type(key_type, f)?;
            for_each_type(value_type, f)
        }
        Type::Custom { builtin, .. } => for_each_type(builtin, f),
        _ => Ok(()),
    }
}

/// Fail for renames that would leave the bindings inconsistent.
///
/// - The FFI function that registers the Kotlin implementations of a callback interface, or of a
///   trait that Kotlin can implement, is looked up with the name of the interface. Renaming it
///   would break the symbol name, so it is rejected.
/// - `uniffi_bindgen::interface::rename` doesn't visit the methods of records and enums, the
///   uniffi traits, the return and error types of the methods of callback interfaces and the
///   error types of constructors. The bindings would reference the old names of the types they
///   use, and Rustine can't rename them from outside `uniffi_bindgen`.
/// - Default values naming enum variants keep the old variant names.
fn check_renames(ci: &ComponentInterface, renames: &CrateRenames) -> Result<()> {
    let crate_name = ci.crate_name();
    for cbi in ci.callback_interface_definitions() {
        check_foreign_trait_rename(renames, crate_name, cbi.name())?;
    }
    for obj in ci.object_definitions() {
        if obj.has_callback_interface() {
            check_foreign_trait_rename(renames, crate_name, obj.name())?;
        }
    }

    let check_types = |location: &str, types: Vec<Type>| -> Result<()> {
        for ty in &types {
            for_each_type(ty, &mut |ty| {
                if let Some(new_name) = new_type_name(renames, ty) {
                    bail!(
                        "Can't rename `{}` to `{new_name}`: it is used by {location}. Renaming \
                         the types used by the methods of records and enums, by uniffi traits, by \
                         the return values and errors of callback interface methods, and by the \
                         errors of constructors is not supported.",
                        ty.name().unwrap_or_default(),
                    );
                }
                Ok(())
            })?;
        }
        Ok(())
    };
    // Records and enums are lowered with the type of the receiver.
    let method_types = |meth: &Method| -> Vec<Type> {
        let mut types = callable_types(meth);
        types.extend(meth.self_type());
        types
    };
    let uniffi_trait_types = |methods: UniffiTraitMethods, with_receiver: bool| -> Vec<Type> {
        [
            methods.debug_fmt,
            methods.display_fmt,
            methods.eq_eq,
            methods.eq_ne,
            methods.hash_hash,
            methods.ord_cmp,
        ]
        .iter()
        .flatten()
        .flat_map(|m| {
            if with_receiver {
                method_types(m)
            } else {
                callable_types(m)
            }
        })
        .collect()
    };

    for rec in ci.record_definitions() {
        for meth in rec.methods() {
            let location = format!("the method `{}.{}`", rec.name(), meth.name());
            check_types(&location, method_types(meth))?;
            check_argument_defaults(renames, &location, meth.arguments())?;
        }
        let location = format!("the uniffi traits of `{}`", rec.name());
        check_types(
            &location,
            uniffi_trait_types(rec.uniffi_trait_methods(), true),
        )?;
        for field in rec.fields() {
            let location = format!("`{}`", rec.name());
            check_default(renames, &location, field.name(), field.default_value())?;
        }
    }
    for e in ci.enum_definitions() {
        for meth in e.methods() {
            let location = format!("the method `{}.{}`", e.name(), meth.name());
            check_types(&location, method_types(meth))?;
            check_argument_defaults(renames, &location, meth.arguments())?;
        }
        let location = format!("the uniffi traits of `{}`", e.name());
        check_types(
            &location,
            uniffi_trait_types(e.uniffi_trait_methods(), true),
        )?;
        for variant in e.variants() {
            let location = format!("`{}.{}`", e.name(), variant.name());
            for field in variant.fields() {
                check_default(renames, &location, field.name(), field.default_value())?;
            }
        }
    }
    for obj in ci.object_definitions() {
        // Objects are passed to their methods by handle, without lowering the receiver.
        let location = format!("the uniffi traits of `{}`", obj.name());
        check_types(
            &location,
            uniffi_trait_types(obj.uniffi_trait_methods(), false),
        )?;
        for cons in obj.constructors() {
            let location = format!("the constructor `{}.{}`", obj.name(), cons.name());
            check_types(&location, cons.throws_type().into_iter().cloned().collect())?;
            check_argument_defaults(renames, &location, cons.arguments())?;
        }
        for meth in obj.methods() {
            let location = format!("the method `{}.{}`", obj.name(), meth.name());
            check_argument_defaults(renames, &location, meth.arguments())?;
        }
    }
    for cbi in ci.callback_interface_definitions() {
        for meth in cbi.methods() {
            let location = format!("the method `{}.{}`", cbi.name(), meth.name());
            let types = meth.return_type().into_iter().chain(meth.throws_type());
            check_types(&location, types.cloned().collect())?;
        }
    }
    for func in ci.function_definitions() {
        let location = format!("the function `{}`", func.name());
        check_argument_defaults(renames, &location, func.arguments())?;
    }
    Ok(())
}

fn check_foreign_trait_rename(renames: &CrateRenames, crate_name: &str, name: &str) -> Result<()> {
    if let Some(new_name) = new_name(renames, crate_name, name) {
        bail!(
            "Can't rename `{name}` to `{new_name}`. Renaming callback interfaces and traits that \
             foreign code can implement is not supported, as their FFI functions are looked up \
             by name."
        );
    }
    Ok(())
}

/// The argument, return and error types of a callable, without its receiver.
fn callable_types(callable: &impl Callable) -> Vec<Type> {
    callable
        .arguments()
        .into_iter()
        .map(Argument::as_type)
        .chain(callable.return_type().cloned())
        .chain(callable.throws_type().cloned())
        .collect()
}

fn check_argument_defaults(
    renames: &CrateRenames,
    location: &str,
    arguments: Vec<&Argument>,
) -> Result<()> {
    for arg in arguments {
        check_default(renames, location, arg.name(), arg.default_value())?;
    }
    Ok(())
}

/// Fail for a default value naming an enum variant that is renamed.
fn check_default(
    renames: &CrateRenames,
    owner: &str,
    name: &str,
    default: Option<&DefaultValue>,
) -> Result<()> {
    fn check_literal(renames: &CrateRenames, literal: &Literal) -> Option<String> {
        match literal {
            // The type of the literal keeps the old name of the enum.
            Literal::Enum(variant, ty) => {
                let key = format!("{}.{variant}", ty.name()?);
                new_name(renames, ty.crate_name()?, &key).map(|_| key)
            }
            Literal::Some { inner } => match inner.as_ref() {
                DefaultValue::Literal(literal) => check_literal(renames, literal),
                DefaultValue::Default => None,
            },
            _ => None,
        }
    }

    if let Some(DefaultValue::Literal(literal)) = default {
        if let Some(variant) = check_literal(renames, literal) {
            bail!(
                "Can't rename the enum variant `{variant}` because the default value of `{name}` \
                 in {owner} uses it. Renaming the variants used by default values is not \
                 supported."
            );
        }
    }
    Ok(())
}

/// The crates of the errors from other crates, keyed by error name. The renamer renames the
/// names of errors with the table of the current crate.
fn external_errors(ci: &ComponentInterface) -> HashMap<String, String> {
    let local_names: HashSet<_> = ci.iter_local_types().filter_map(Type::name).collect();
    ci.iter_external_types()
        .filter_map(|ty| {
            let name = ty.name()?;
            if !ci.is_name_used_as_error(name) || local_names.contains(name) {
                return None;
            }
            Some((name.to_string(), ty.crate_name()?.to_string()))
        })
        .collect()
}

/// Protects the types that the renamer would rename with the table of another crate, before the
/// renamer visits the component interface.
struct ProtectTypes<'a> {
    this_crate: String,
    external_errors: &'a HashMap<String, String>,
}

impl VisitMut for ProtectTypes<'_> {
    fn visit_record(&self, _record: &mut Record) {}

    fn visit_object(&self, _object: &mut Object) {}

    fn visit_enum(&self, _is_error: bool, _enum_: &mut Enum) {}

    fn visit_type(&self, type_: &mut Type) {
        // The renamer uses the table of the crate of a named type for the type and the types it
        // contains, and the table of the current crate otherwise.
        let renamer_crate = type_.crate_name().unwrap_or(&self.this_crate).to_string();
        protect_type(type_, Some(&renamer_crate));
    }

    fn visit_error_name(&self, name: &mut String) {
        if self.external_errors.contains_key(name) {
            name.push(PROTECTED_SUFFIX);
        }
    }

    fn visit_method(&self, _object_name: &str, _method: &mut Method) {}

    fn visit_constructor(&self, _object_name: &str, _constructor: &mut Constructor) {}

    fn visit_function(&self, _function: &mut Function) {}
}

/// Renames what `uniffi_bindgen::interface::rename` misses, after the renamer visited the
/// component interface.
struct RenameFixups<'a> {
    renames: &'a CrateRenames,
    external_errors: &'a HashMap<String, String>,
}

impl VisitMut for RenameFixups<'_> {
    fn visit_record(&self, _record: &mut Record) {}

    fn visit_object(&self, object: &mut Object) {
        // The renamer doesn't visit the trait implementations.
        for trait_impl in object.trait_impls_mut() {
            rename_type(&mut trait_impl.ty, self.renames);
            rename_type(&mut trait_impl.trait_ty, self.renames);
        }
    }

    fn visit_callback_interface(&self, _iface: &mut CallbackInterface) {}

    fn visit_field(&self, _field: &mut Field) {}

    fn visit_enum(&self, _is_error: bool, _enum_: &mut Enum) {}

    fn visit_type(&self, type_: &mut Type) {
        rename_protected_type(type_, self.renames);
    }

    fn visit_error_name(&self, name: &mut String) {
        let Some(old_name) = name.strip_suffix(PROTECTED_SUFFIX) else {
            return;
        };
        let new_name = self
            .external_errors
            .get(old_name)
            .and_then(|crate_name| new_name(self.renames, crate_name, old_name))
            .unwrap_or(old_name);
        *name = new_name.to_string();
    }

    fn visit_method(&self, _object_name: &str, _method: &mut Method) {}

    fn visit_constructor(&self, _object_name: &str, _constructor: &mut Constructor) {}

    fn visit_function(&self, _function: &mut Function) {}
}

#[cfg(test)]
mod tests {
    use super::*;
    use uniffi_meta::{
        FieldMetadata, Metadata, ObjectImpl, ObjectTraitImplMetadata, RecordMetadata, TraitKind,
    };

    fn component(
        crate_name: &str,
        udl: &str,
        extra: Vec<Metadata>,
        config: &str,
    ) -> Component<Config> {
        let mut group = uniffi_udl::parse_udl(udl, crate_name).unwrap();
        group.items.extend(extra);
        let mut ci = ComponentInterface::new(crate_name);
        ci.add_metadata(group).unwrap();
        let config = toml::from_str(config).unwrap();
        Component { ci, config }
    }

    const ONE_UDL: &str = r#"
        namespace one {};
        dictionary Thing { u32 value; };
        [Error]
        enum OneError { "Oops" };
        [Trait]
        interface OneTrait { u32 get(); };
    "#;

    const ONE_CONFIG: &str = r#"
        [rename]
        Thing = "KtThing"
        "Thing.value" = "ktValue"
        OneError = "KtOneError"
        OneTrait = "KtOneTrait"
    "#;

    const TWO_UDL: &str = r#"
        namespace two {
            sequence<Thing> things();
            Thing? maybe_thing();
            record<string, Thing> thing_map();
            Thing thing();
            [Throws=OneError]
            void fail();
        };
        [External="one"]
        typedef dictionary Thing;
        [External="one"]
        typedef enum OneError;
        [External="one"]
        typedef trait OneTrait;
        dictionary Local { u32 value; };
        interface Impl {};
    "#;

    fn two_extra() -> Vec<Metadata> {
        vec![
            // UDL can't declare `Box` fields or trait implementations.
            Metadata::Record(RecordMetadata {
                module_path: "two".to_string(),
                name: "Boxed".to_string(),
                orig_name: None,
                remote: false,
                fields: vec![FieldMetadata {
                    name: "thing".to_string(),
                    orig_name: None,
                    ty: Type::Box {
                        inner_type: Box::new(Type::Record {
                            module_path: "one".to_string(),
                            name: "Thing".to_string(),
                        }),
                    },
                    default: None,
                    docstring: None,
                }],
                docstring: None,
            }),
            Metadata::ObjectTraitImpl(ObjectTraitImplMetadata {
                ty: Type::Object {
                    module_path: "two".to_string(),
                    name: "Impl".to_string(),
                    imp: ObjectImpl::Struct,
                },
                trait_ty: Type::Object {
                    module_path: "one".to_string(),
                    name: "OneTrait".to_string(),
                    imp: ObjectImpl::Trait(TraitKind::RustOnly),
                },
            }),
        ]
    }

    fn function_return_type(ci: &ComponentInterface, name: &str) -> Type {
        ci.get_function_definition(name)
            .unwrap()
            .return_type()
            .unwrap()
            .clone()
    }

    fn thing(name: &str) -> Type {
        Type::Record {
            module_path: "one".to_string(),
            name: name.to_string(),
        }
    }

    #[test]
    fn renames_items_of_their_crate() {
        let mut components = vec![
            component("one", ONE_UDL, vec![], ONE_CONFIG),
            component(
                "two",
                TWO_UDL,
                two_extra(),
                // `Thing` names a type of another crate, so this doesn't rename it.
                r#"
                [rename]
                Local = "KtLocal"
                "Local.value" = "ktValue"
                Thing = "WrongThing"
                OneError = "WrongError"
                things = "kt_things"
                "#,
            ),
        ];
        apply_renames(&mut components).unwrap();

        let one = &components[0].ci;
        let rec = one.get_record_definition("KtThing").unwrap();
        assert_eq!(rec.fields()[0].name(), "ktValue");
        assert!(one.get_object_definition("KtOneTrait").is_some());
        assert!(one.is_name_used_as_error("KtOneError"));

        let two = &components[1].ci;
        assert!(two.get_record_definition("KtLocal").is_some());
        assert_eq!(function_return_type(two, "thing"), thing("KtThing"));
        // Upstream renames nested types with the table of the current crate...
        assert_eq!(
            function_return_type(two, "kt_things"),
            Type::Sequence {
                inner_type: Box::new(thing("KtThing"))
            }
        );
        assert_eq!(
            function_return_type(two, "maybe_thing"),
            Type::Optional {
                inner_type: Box::new(thing("KtThing"))
            }
        );
        assert_eq!(
            function_return_type(two, "thing_map"),
            Type::Map {
                key_type: Box::new(Type::String),
                value_type: Box::new(thing("KtThing")),
            }
        );
        // ... and doesn't rename types inside a `Box`.
        assert_eq!(
            two.get_record_definition("Boxed").unwrap().fields()[0].as_type(),
            Type::Box {
                inner_type: Box::new(thing("KtThing"))
            }
        );
        // Upstream doesn't rename the names of errors from other crates...
        assert!(two.is_name_used_as_error("KtOneError"));
        assert!(!two.is_name_used_as_error("OneError"));
        // ... nor the traits that objects implement.
        let trait_impls = two.get_object_definition("Impl").unwrap().trait_impls();
        assert_eq!(
            trait_impls[0].trait_ty,
            Type::Object {
                module_path: "one".to_string(),
                name: "KtOneTrait".to_string(),
                imp: ObjectImpl::Trait(TraitKind::RustOnly),
            }
        );
    }

    #[test]
    fn rejects_renaming_foreign_traits() {
        let udl = r#"
            namespace one {};
            callback interface Callback { void call(); };
        "#;
        let mut components = vec![component(
            "one",
            udl,
            vec![],
            "[rename]\nCallback = \"KtCallback\"",
        )];
        let err = apply_renames(&mut components).unwrap_err();
        assert!(err
            .to_string()
            .contains("Can't rename `Callback` to `KtCallback`. Renaming callback interfaces"));

        // Renaming its methods works.
        let mut components = vec![component(
            "one",
            udl,
            vec![],
            "[rename]\n\"Callback.call\" = \"ktCall\"",
        )];
        apply_renames(&mut components).unwrap();
    }

    #[test]
    fn rejects_renaming_types_the_renamer_misses() {
        let err = |udl: &str, config: &str| {
            let mut components = vec![component("one", udl, vec![], config)];
            apply_renames(&mut components).unwrap_err().to_string()
        };

        let udl = r#"
            namespace one {};
            [Enum, Traits=(Display)]
            interface Shape { Circle(); };
        "#;
        assert!(err(udl, "[rename]\nShape = \"KtShape\"").contains(
            "Can't rename `Shape` to `KtShape`: it is used by the uniffi traits of `Shape`."
        ));

        let udl = r#"
            namespace one {};
            dictionary Thing { u32 value; };
            callback interface Callback { Thing get(); };
        "#;
        assert!(err(udl, "[rename]\nThing = \"KtThing\"").contains(
            "Can't rename `Thing` to `KtThing`: it is used by the method `Callback.get`."
        ));

        let udl = r#"
            namespace one {};
            enum Color { "Red", "Green" };
            dictionary Paint { Color color = "Red"; };
        "#;
        assert!(err(udl, "[rename]\n\"Color.Red\" = \"Crimson\"").contains(
            "Can't rename the enum variant `Color.Red` because the default value of `color` in \
             `Paint` uses it."
        ));
    }
}
