/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.kotlin

import rustine.gradle.InternalRustineGradleApi
import org.gradle.api.DomainObjectCollection
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@InternalRustineGradleApi
interface RustineKotlinExtensionDelegate {
    val pluginId: String
    val targets: DomainObjectCollection<KotlinTarget>
    val sourceSets: RustineKotlinSourceSetCollection
    val implementationVersion: String?
    val jvmTarget: KotlinTarget?
    val androidTarget: KotlinTarget?
}