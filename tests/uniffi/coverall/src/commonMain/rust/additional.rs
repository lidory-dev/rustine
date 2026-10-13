/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use std::sync::Arc;

// #74
#[derive(uniffi::Object)]
struct NotConstructible {}

// #74
#[uniffi::export]
impl NotConstructible {
    #[uniffi::constructor]
    fn new() -> Arc<Self> {
        panic!("constructor panic")
    }
}

// #74
#[uniffi::export]
fn new_not_constructible() -> Arc<NotConstructible> {
    panic!("function panic")
}

// #74
#[derive(Debug, thiserror::Error, uniffi::Error)]
enum NotConstructibleError {
    #[error("not constructible")]
    NotConstructible,
}

// #74
#[derive(uniffi::Object)]
struct NotConstructible2 {}

// #74
#[uniffi::export]
impl NotConstructible2 {
    #[uniffi::constructor]
    fn new() -> Result<Arc<Self>, NotConstructibleError> {
        Err(NotConstructibleError::NotConstructible)
    }
}

// #74
#[uniffi::export]
fn new_not_constructible2() -> Result<Arc<NotConstructible2>, NotConstructibleError> {
    Err(NotConstructibleError::NotConstructible)
}

// #193
#[derive(uniffi::Enum)]
enum EnumWithVariousVariants {
    First,
    Second(i32, f32),
    Third(i32, Vec<u8>, String),
    Fourth { val: i32 },
    Fifth {
        val1: i32,
        val2: Vec<u8>,
        val3: String,
    },
}

// Methods and uniffi traits of records, enums and errors, which are plain classes in common Kotlin
// code. The stubs for the JS and Wasm targets must define them too.
#[derive(uniffi::Record, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
#[uniffi::export(Debug, Eq, Hash, Ord)]
struct RecordWithMethods {
    value: i32,
}

#[uniffi::export]
impl RecordWithMethods {
    fn doubled(&self) -> i32 {
        self.value * 2
    }

    async fn doubled_async(&self) -> i32 {
        self.value * 2
    }
}

#[derive(uniffi::Enum)]
#[uniffi::export(Display)]
enum FlatEnumWithMethods {
    First,
    Second,
}

impl std::fmt::Display for FlatEnumWithMethods {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::First => write!(f, "first"),
            Self::Second => write!(f, "second"),
        }
    }
}

#[uniffi::export]
impl FlatEnumWithMethods {
    fn other(&self) -> FlatEnumWithMethods {
        match self {
            Self::First => Self::Second,
            Self::Second => Self::First,
        }
    }
}

#[derive(uniffi::Enum, PartialEq, Eq, PartialOrd, Ord)]
#[uniffi::export(Eq, Ord)]
enum EnumWithMethods {
    Empty,
    Value(i32),
}

#[uniffi::export]
impl EnumWithMethods {
    fn value_or(&self, default: i32) -> i32 {
        match self {
            Self::Empty => default,
            Self::Value(value) => *value,
        }
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi::export(Display)]
enum MethodsError {
    #[error("code {code}")]
    Code { code: i32 },
}

#[uniffi::export]
impl MethodsError {
    fn code(&self) -> i32 {
        match self {
            Self::Code { code } => *code,
        }
    }
}

#[uniffi::export]
fn throw_methods_error(code: i32) -> Result<(), MethodsError> {
    Err(MethodsError::Code { code })
}

// Kotlin compares ByteArrays by reference, so the bindings generate `equals()` and `hashCode()`,
// unless they come from Rust.
#[derive(uniffi::Record, PartialEq, Eq)]
#[uniffi::export(Eq)]
struct RecordWithBytesAndEq {
    bytes: Vec<u8>,
}

#[derive(uniffi::Enum, PartialEq, Eq)]
#[uniffi::export(Eq)]
enum EnumWithBytesAndEq {
    Empty,
    Bytes(Vec<u8>),
}

#[derive(uniffi::Object, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[uniffi::export(Debug, Eq, Ord)]
struct OrderedObject {
    value: i32,
}

#[uniffi::export]
impl OrderedObject {
    #[uniffi::constructor]
    fn new(value: i32) -> Arc<Self> {
        Arc::new(Self { value })
    }
}

// Fields named `coverall`, like the package of the bindings, shadow the package in the default
// values of the members of the classes, so the bindings can't name the types of these values with
// their fully qualified names there.
#[derive(uniffi::Record)]
struct Increment {
    #[uniffi(default = 1)]
    value: i32,
}

#[derive(uniffi::Record)]
struct FieldNamedLikeThePackage {
    coverall: i32,
}

#[uniffi::export]
impl FieldNamedLikeThePackage {
    #[uniffi::method(default(increment))]
    fn incremented(&self, increment: Increment) -> i32 {
        self.coverall + increment.value
    }
}

#[derive(uniffi::Enum)]
enum VariantFieldNamedLikeThePackage {
    Value {
        coverall: i32,
        #[uniffi(default)]
        increment: Increment,
    },
}
