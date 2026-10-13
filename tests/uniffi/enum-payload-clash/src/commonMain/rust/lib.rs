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

uniffi::setup_scaffolding!("enum_payload_clash");
