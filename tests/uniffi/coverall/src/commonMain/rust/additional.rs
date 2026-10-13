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

// Methods on records, enums and errors, which are plain classes in common Kotlin code. The stubs
// for the JS and Wasm targets must define them too.
#[derive(uniffi::Record)]
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
enum FlatEnumWithMethods {
    First,
    Second,
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

#[derive(uniffi::Enum)]
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
