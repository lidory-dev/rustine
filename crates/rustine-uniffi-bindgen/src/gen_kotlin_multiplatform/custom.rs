/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use anyhow::Result;
use uniffi_bindgen::{interface::DefaultValue, ComponentInterface};

use super::{CodeType, Config};

#[derive(Debug)]
pub struct CustomCodeType {
    name: String,
    builtin: Box<dyn CodeType>,
}

impl CustomCodeType {
    pub fn new(name: String, builtin: Box<dyn CodeType>) -> Self {
        CustomCodeType { name, builtin }
    }
}

impl CodeType for CustomCodeType {
    fn type_label(&self, ci: &ComponentInterface) -> String {
        super::KotlinCodeOracle.class_name(ci, &self.name)
    }

    fn canonical_name(&self) -> String {
        format!("Type{}", self.name)
    }

    fn default(
        &self,
        default: &DefaultValue,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String> {
        let builtin_default = self.builtin.default(default, ci, config).map_err(|_e| {
            anyhow::anyhow!("Unsupported default value for {}", self.type_label(ci))
        })?;
        // Upstream renders the builtin's default as is. When the custom type has a Kotlin type of
        // its own, the builtin value must be converted with the configured `lift` expression.
        Ok(match config.custom_types.get(&self.name) {
            Some(custom_type_config) => custom_type_config.lift(&builtin_default),
            None => builtin_default,
        })
    }
}
