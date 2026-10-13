/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use kmm_uniffi_one::{UniffiOneEnum, UniffiOneInterface, UniffiOneTrait};
use std::sync::Arc;

#[derive(Default, uniffi::Record)]
pub struct SubLibType {
    pub maybe_enum: Option<UniffiOneEnum>,
    pub maybe_trait: Option<Arc<dyn UniffiOneTrait>>,
    pub maybe_interface: Option<Arc<UniffiOneInterface>>,
}

#[uniffi::export]
fn get_sub_type(existing: Option<SubLibType>) -> SubLibType {
    existing.unwrap_or_default()
}

struct OneImpl;

impl UniffiOneTrait for OneImpl {
    fn hello(&self) -> String {
        "sub-lib trait impl says hello".to_string()
    }
}

#[uniffi::export]
fn get_trait_impl() -> Arc<dyn UniffiOneTrait> {
    Arc::new(OneImpl {})
}

#[derive(uniffi::Error, Debug)]
pub enum NotToThrowError {
    Variant(u64),
}

impl std::fmt::Display for NotToThrowError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            NotToThrowError::Variant(code) => {
                write!(f, "NotToThrowError with code {code}")
            }
        }
    }
}

uniffi::setup_scaffolding!("sub_lib");
