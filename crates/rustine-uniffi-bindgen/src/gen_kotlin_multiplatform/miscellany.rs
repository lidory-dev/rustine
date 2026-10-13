/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use anyhow::{bail, Result};
use uniffi_bindgen::{interface::DefaultValue, ComponentInterface};

use super::{CodeType, Config};

macro_rules! impl_code_type_for_miscellany {
    ($T:ident, $class_name:literal, $canonical_name:literal, $def:expr) => {
        #[derive(Debug)]
        pub struct $T;

        impl CodeType for $T {
            fn type_label(&self, _ci: &ComponentInterface) -> String {
                $class_name.into()
            }

            fn canonical_name(&self) -> String {
                $canonical_name.into()
            }

            fn default(
                &self,
                default: &DefaultValue,
                _ci: &ComponentInterface,
                _config: &Config,
            ) -> Result<String> {
                let def: Option<&str> = $def;
                match (default, def) {
                    (DefaultValue::Default, Some(def)) => Ok(def.into()),
                    _ => bail!(
                        "Default values are not supported for {}: {default:?}",
                        $class_name
                    ),
                }
            }
        }
    };
}

// Upstream would render `Instant()` for a bare `#[uniffi(default)]`, which does not compile.
// `SystemTime` has no `Default` implementation in Rust either, so reject it.
impl_code_type_for_miscellany!(
    TimestampCodeType,
    "kotlinx.datetime.Instant",
    "Timestamp",
    None
);

impl_code_type_for_miscellany!(
    DurationCodeType,
    "kotlin.time.Duration",
    "Duration",
    Some("kotlin.time.Duration.ZERO")
);
