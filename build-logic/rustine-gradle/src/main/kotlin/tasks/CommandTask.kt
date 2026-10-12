/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.tasks

import rustine.gradle.InternalRustineGradleApi
import rustine.gradle.utils.Command
import rustine.gradle.utils.CommandResult
import rustine.gradle.utils.CommandSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.of
import java.io.File
import javax.inject.Inject

@InternalRustineGradleApi
abstract class CommandTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val additionalEnvironment: MapProperty<String, Any>

    @get:Input
    @get:Optional
    abstract val additionalEnvironmentPath: ListProperty<File>

    @get:Inject
    abstract val projectLayout: ProjectLayout

    @get:Inject
    abstract val providerFactory: ProviderFactory

    open fun configureFromProperties(spec: CommandSpec) = with(spec) {
        additionalEnvironmentPath(additionalEnvironmentPath)
        for ((key, value) in additionalEnvironment.get()) {
            additionalEnvironment(key, value)
        }
    }

    @JvmName("commandWithRegularFile")
    fun command(
        command: Provider<RegularFile>,
        action: CommandSpec.() -> Unit = {},
    ) = command(command.map { it.asFile }, action)

    fun command(
        command: File,
        action: CommandSpec.() -> Unit = {},
    ) = command(providerFactory.provider { command }, action)

    @JvmName("commandWithFile")
    fun command(
        command: Provider<File>,
        action: CommandSpec.() -> Unit = {},
    ) = commandImpl(command.map { it.name }) {
        additionalEnvironmentPath(command.map { it.parentFile })
        configureFromProperties(this)
        action()
    }

    fun command(
        command: String,
        action: CommandSpec.() -> Unit = {},
    ) = command(providerFactory.provider { command }, action)

    fun command(
        command: Provider<String>,
        action: CommandSpec.() -> Unit = {},
    ) = commandImpl(command) {
        configureFromProperties(this)
        action()
    }

    private fun commandImpl(
        command: Provider<String>,
        action: CommandSpec.() -> Unit,
    ): Provider<CommandResult> = providerFactory.of(Command::class) {
        it.parameters.command.set(command)
        CommandSpec(projectLayout, providerFactory, it.parameters).apply(action)
    }
}
