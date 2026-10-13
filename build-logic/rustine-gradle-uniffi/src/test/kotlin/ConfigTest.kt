/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rustine.gradle.uniffi

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class ConfigTest {
    private fun roundTrip(config: Config): Config {
        return Config.toml.decodeFromString<Config>(Config.toml.encodeToString(config))
    }

    @Test
    fun testItemKeys() {
        val config = Config.toml.decodeFromString<Config>(
            """
            exclude = ["my_function", "MyObject.new"]
            mutable_records = ["MyRecord"]

            [rename]
            BindingRenamedType = "KotlinRenamedType"
            "BindingRenamedType.value" = "kotlinValue"
            "MyEnum.Variant.field" = "kotlinField"
            """.trimIndent()
        )
        config.exclude shouldBe listOf("my_function", "MyObject.new")
        config.mutableRecords shouldBe listOf("MyRecord")
        val rename = mapOf(
            "BindingRenamedType" to "KotlinRenamedType",
            "BindingRenamedType.value" to "kotlinValue",
            "MyEnum.Variant.field" to "kotlinField",
        )
        config.rename shouldBe rename

        // Dotted keys must stay quoted, or they would be read as nested tables.
        val encoded = Config.toml.encodeToString(config)
        encoded shouldContain "\"BindingRenamedType.value\" = \"kotlinValue\""
        encoded shouldContain "\"MyEnum.Variant.field\" = \"kotlinField\""
        roundTrip(config) shouldBe config
    }

    @Test
    fun testCustomTypeAliases() {
        val config = Config.toml.decodeFromString<Config>(
            """
            [custom_types.Url]
            type_name = "URL"
            imports = ["java.net.URL"]
            into_custom = "URL({})"
            from_custom = "{}.toString()"
            """.trimIndent()
        )
        val customType = config.customTypes!!.getValue("Url")
        customType.intoCustom shouldBe "URL({})"
        customType.fromCustom shouldBe "{}.toString()"
        roundTrip(config) shouldBe config
    }
}
