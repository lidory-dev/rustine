/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.utils

import gobley.gradle.InternalGobleyGradleApi
import gobley.gradle.Variant
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.invocation.Gradle

@InternalGobleyGradleApi
object GradleUtils {
    private fun invokedByIde(): Boolean {
        return System.getProperty("idea.active").toBoolean()
    }

    private fun invokedByIdeSync(): Boolean {
        return invokedByIde() && System.getProperty("idea.sync.active").toBoolean()
    }

    private fun strictlyCalling(
        gradle: Gradle,
        vararg expectedTaskRequests: List<String>,
    ): Boolean {
        val taskRequests = gradle.startParameter.taskRequests.toMutableSet()
        if (taskRequests.isEmpty()) {
            return expectedTaskRequests.isEmpty()
        }
        for (expectedTaskRequest in expectedTaskRequests) {
            val taskRequest = taskRequests.firstOrNull { taskRequest ->
                if (expectedTaskRequest.size != taskRequest.args.size) {
                    return@firstOrNull false
                }
                expectedTaskRequest.zip(taskRequest.args).all { (expected, actual) ->
                    actual.endsWith(expected)
                }
            } ?: return false
            taskRequests.remove(taskRequest)
        }
        return taskRequests.isEmpty()
    }

    fun invokedByKotlinJvmBuild(gradle: Gradle): Boolean {
        return invokedByIde() && strictlyCalling(gradle, listOf(":classes"))
    }

    fun getComposePreviewVariant(gradle: Gradle): Variant? {
        return when {
            // Compose previews seem to be use information from the model built during IDE sync.
            // Since one of the dependencies of Compose previews, androidx.compose.ui:ui-tooling,
            // is referenced as debugImplementation in the default template generated from
            // Android Studio, and there is relatively small chance of users requiring to use
            // the Rust library from release mode Compose previews, let's return Variant.Debug
            // during IDE sync. See #94 for details.
            //
            // CargoPlugin's Project.configureJvmPostBuildTasks also considers debug mode Compose
            // previews only.
            invokedByIdeSync() -> Variant.Debug
            !invokedByIde() -> null
            strictlyCalling(gradle, listOf(":compileDebugSources")) -> Variant.Debug
            strictlyCalling(gradle, listOf(":compileReleaseSources")) -> Variant.Release
            else -> null
        }
    }

    /**
     * Returns `true` when a task requested in the command line looks like a release build or a
     * publishing task, i.e., its name contains `release` or starts with `publish`. For example,
     * `assembleRelease`, `:app:bundleRelease`, and `publishToMavenLocal`.
     */
    fun requestsReleaseBuild(gradle: Gradle): Boolean {
        // Included builds don't receive the requested tasks, so read them from the root build.
        val rootBuild = generateSequence(gradle) { it.parent }.last()
        return rootBuild.startParameter.taskNames.any { argument ->
            // The list also contains task options and their values, like `--tests *Release*`.
            // Skip arguments that can't be task paths.
            val isTaskPath = !argument.startsWith("-") && argument.none {
                it in INVALID_TASK_PATH_CHARACTERS || it.isWhitespace()
            }
            if (!isTaskPath) {
                return@any false
            }
            val taskName = argument.substringAfterLast(':')
            taskName.contains("release", ignoreCase = true) || taskName.startsWith("publish")
        }
    }

    // Characters not allowed in project and task names, plus `.` used in test filters.
    private const val INVALID_TASK_PATH_CHARACTERS = "/\\<>\"?*|."

    fun runTaskDuringSync(project: Project, vararg paths: Any) {
        if (!invokedByIdeSync()) return
        try {
            project.rootProject.tasks.named("prepareKotlinBuildScriptModel") { syncTask ->
                syncTask.dependsOn(*paths)
            }
        } catch (_: UnknownDomainObjectException) {
        }
    }
}
