/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.uniffi.tasks

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder
import rustine.gradle.uniffi.Config
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

class MergeUniffiConfigTaskTest {
    @Test
    fun testItemKeys() {
        val projectDir = createTempDirectory("merge-uniffi-config").toFile()
        try {
            val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
            val originalConfig = projectDir.resolve("uniffi.toml")
            originalConfig.writeText(
                """
                exclude = ["MyObject.new"]
                mutable_records = ["MyRecord"]

                [rename]
                MyRecord = "KotlinRecord"
                "MyRecord.field" = "kotlinField"

                [custom_types.Url]
                type_name = "URL"
                into_custom = "URL({})"
                from_custom = "{}.toString()"
                """.trimIndent()
            )
            val outputConfig = projectDir.resolve("merged.toml")
            val task = project.tasks.register<MergeUniffiConfigTask>("mergeUniffiConfig") {
                this.originalConfig.set(originalConfig)
                this.outputConfig.set(outputConfig)
                exclude.add("my_function")
                rename.put("MyRecord", "IgnoredRecord")
                rename.put("my_function.arg", "kotlinArg")
                mutableRecords.add("OtherRecord")
            }.get()
            task.mergeConfig()

            val merged = outputConfig.readText()
            merged shouldContain "\"MyRecord.field\" = \"kotlinField\""
            merged shouldContain "\"my_function.arg\" = \"kotlinArg\""
            val config = Config(outputConfig)
            config.exclude shouldBe listOf("MyObject.new", "my_function")
            config.mutableRecords shouldBe listOf("MyRecord", "OtherRecord")
            // The entries of the original config take precedence.
            config.rename shouldBe mapOf(
                "MyRecord" to "KotlinRecord",
                "MyRecord.field" to "kotlinField",
                "my_function.arg" to "kotlinArg",
            )
            val customType = config.customTypes!!.getValue("Url")
            customType.intoCustom shouldBe "URL({})"
            customType.fromCustom shouldBe "{}.toString()"
        } finally {
            projectDir.deleteRecursively()
        }
    }
}
