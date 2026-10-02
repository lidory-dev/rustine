/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#[uniffi::export]
fn add(lhs: i32, rhs: i32) -> i32 {
    lhs + rhs
}

#[derive(uniffi::Object)]
struct Greeter {
    greeting: String,
}

#[uniffi::export]
impl Greeter {
    #[uniffi::constructor]
    fn new(greeting: String) -> Self {
        Self { greeting }
    }

    fn greet(&self, name: String) -> String {
        format!("{}, {name}!", self.greeting)
    }
}

uniffi::setup_scaffolding!();
