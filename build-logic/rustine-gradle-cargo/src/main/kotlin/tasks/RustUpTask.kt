/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.cargo.tasks

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.tasks.CommandTask
import rustine.gradle.utils.CommandSpec
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File

@OptIn(InternalRustineGradleApi::class)
abstract class RustUpTask : CommandTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val rustUp: Property<File>

    internal fun rustUp(
        vararg argument: String,
        action: CommandSpec.() -> Unit = {},
    ) = rustUp.map { it as Any }.orElse("rustup").flatMap { rustUp ->
        if (rustUp is File) {
            command(rustUp) {
                arguments(*argument)
                action()
            }
        } else {
            command("rustup") {
                arguments(*argument)
                action()
            }
        }
    }
}
