/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// Ported from upstream's bindgen-tests/kotlin/enum-payload-clash (mozilla/uniffi-rs#2698).
// Inside the sealed class of an enum, a variant shadows a type of the same name.

use std::collections::{HashMap, HashSet};

#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct Amount {
    pub value: u64,
}

#[derive(Clone, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum AmountOrMax {
    Amount { amount: Amount },
    Max,
}

#[derive(Clone, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum ComplexValue {
    OptionalAmount {
        maybe_amount: Option<Amount>,
    },
    Amounts {
        amounts: Vec<Amount>,
    },
    AmountMap {
        amount_map: HashMap<String, Amount>,
    },
    NestedOptional {
        maybe_amounts: Option<Vec<Amount>>,
    },
    NestedMap {
        nested_map: HashMap<String, Vec<Amount>>,
    },
}

#[uniffi::export]
pub fn get_value() -> AmountOrMax {
    AmountOrMax::Amount {
        amount: Amount { value: 100 },
    }
}

#[uniffi::export]
pub fn get_complex_value() -> ComplexValue {
    ComplexValue::Amounts {
        amounts: vec![Amount { value: 1 }, Amount { value: 2 }],
    }
}

// Rustine: the signatures of the methods of an enum are qualified too.
#[uniffi::export]
impl AmountOrMax {
    fn amount(&self) -> Option<Amount> {
        match self {
            Self::Amount { amount } => Some(amount.clone()),
            Self::Max => None,
        }
    }
}

// Rustine: variants can shadow the Kotlin collections.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum AmountCollection {
    List { amounts: Vec<Amount> },
    Map { amounts: HashMap<String, Amount> },
    Set { names: HashSet<String> },
}

#[uniffi::export]
pub fn roundtrip_collection(collection: AmountCollection) -> AmountCollection {
    collection
}

// Rustine: the variants of errors shadow types the same way.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum AmountError {
    #[error("amount too large: {amount:?}")]
    Amount { amount: Amount },
    #[error("too many amounts")]
    List { amounts: Vec<Amount> },
}

#[uniffi::export]
pub fn check_amount(amount: Amount) -> Result<Amount, AmountError> {
    if amount.value > 1000 {
        Err(AmountError::Amount { amount })
    } else {
        Ok(amount)
    }
}

#[uniffi::export]
pub fn check_amounts(amounts: Vec<Amount>) -> Result<Vec<Amount>, AmountError> {
    if amounts.len() > 2 {
        Err(AmountError::List { amounts })
    } else {
        Ok(amounts)
    }
}

// Rustine: a variant can shadow the error type of a method of the enum too.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum AmountOverflow {
    #[error("the amount overflows")]
    Overflow,
}

#[derive(Clone, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum AmountChange {
    Add { amount: Amount },
    AmountOverflow,
}

#[uniffi::export]
impl AmountChange {
    fn apply_to(&self, amount: Amount) -> Result<Amount, AmountOverflow> {
        match self {
            Self::Add { amount: added } => amount
                .value
                .checked_add(added.value)
                .map(|value| Amount { value })
                .ok_or(AmountOverflow::Overflow),
            Self::AmountOverflow => Err(AmountOverflow::Overflow),
        }
    }
}

// Rustine: variants can shadow the Kotlin types in the signatures of the members that implement
// uniffi traits.
#[derive(Clone, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, uniffi::Enum)]
#[uniffi::export(Display, Eq, Hash, Ord)]
pub enum Value {
    String { value: String },
    Int { value: i32 },
    Boolean { value: bool },
    Any,
}

impl std::fmt::Display for Value {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::String { value } => write!(f, "String({value})"),
            Self::Int { value } => write!(f, "Int({value})"),
            Self::Boolean { value } => write!(f, "Boolean({value})"),
            Self::Any => write!(f, "Any"),
        }
    }
}

#[uniffi::export]
pub fn roundtrip_value(value: Value) -> Value {
    value
}

// Rustine: and of the `equals()` and `hashCode()` of variants with byte array fields.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum Blob {
    Int { bytes: Vec<u8> },
    Boolean { bytes: Vec<u8> },
    Any { bytes: Vec<u8> },
}

#[uniffi::export]
pub fn roundtrip_blob(blob: Blob) -> Blob {
    blob
}

// Rustine: the variants of errors can shadow `String`, the type of their messages.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ValueKindError {
    #[error("unexpected string: {value}")]
    String { value: String },
    #[error("unexpected value")]
    Any,
}

#[uniffi::export]
pub fn expect_boolean(value: Value) -> Result<bool, ValueKindError> {
    match value {
        Value::Boolean { value } => Ok(value),
        Value::String { value } => Err(ValueKindError::String { value }),
        _ => Err(ValueKindError::Any),
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum FlatValueKindError {
    #[error("unexpected string")]
    String,
    #[error("unexpected value")]
    Any,
}

#[uniffi::export]
pub fn expect_boolean_flat(value: Value) -> Result<bool, FlatValueKindError> {
    match value {
        Value::Boolean { value } => Ok(value),
        Value::String { .. } => Err(FlatValueKindError::String),
        _ => Err(FlatValueKindError::Any),
    }
}

// Rustine: variants can shadow the types of default values too.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct Limit {
    #[uniffi(default = 10)]
    pub value: u64,
}

#[derive(Clone, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum LimitOrUnlimited {
    Limit {
        #[uniffi(default)]
        limit: Limit,
    },
    Unlimited,
}

#[uniffi::export]
impl LimitOrUnlimited {
    #[uniffi::method(default(fallback))]
    fn limit_or(&self, fallback: Limit) -> Limit {
        match self {
            Self::Limit { limit } => limit.clone(),
            Self::Unlimited => fallback,
        }
    }
}

uniffi::setup_scaffolding!("enum_payload_clash");
