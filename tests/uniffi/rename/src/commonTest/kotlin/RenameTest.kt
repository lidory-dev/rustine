/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.Url
import rename.*
import kotlin.test.Test

class RenameTest {
    @Test
    fun renamedTypes() {
        // Test renamed types - should be accessible by their custom names
        val record = RenamedRecord(renamedField = 42)
        record.renamedField shouldBe 42

        RenamedEnum.RenamedVariant.shouldBeInstanceOf<RenamedEnum>()
        RenamedEnum.Record(record).v1 shouldBe record
        RenamedEnumWithFields.RenamedVariantWithFields(renamedVariantField = 42u)
            .renamedVariantField shouldBe 42u

        // Test renamed function
        val result = renamedFunction(record)
        result.shouldBeInstanceOf<RenamedEnum.Record>()
        result.v1.renamedField shouldBe 42

        // Test renamed object with renamed constructor and method
        val obj = RenamedObject.renamedConstructor(123)
        obj.renamedMethod() shouldBe 123

        // Test trait method renaming (trait itself keeps original name)
        val traitImpl: Trait = createTraitImpl(5)
        traitImpl.renamedTraitMethod(10) shouldBe 50 // 10 * 5 = 50
    }

    //
    // Test TOML-based renaming with dot notation
    //
    @Test
    fun renamedWithToml() {
        val ktRecord = KtRecord(kotlinItem = 123) // Field renamed: item -> kotlinItem
        ktRecord.kotlinItem shouldBe 123

        // Function binding_function -> ktFunction, arg record -> kotlinRecord
        val ktResult = ktFunction(kotlinRecord = ktRecord)
        ktResult.shouldBeInstanceOf<KtEnum.KotlinRecord>()
        ktResult.v1 shouldBe ktRecord

        val withFields = KtEnumWithFields.KotlinVariantA(kotlinInt = 1U)
        withFields.kotlinInt shouldBe 1U

        // throwing renamed exception.
        shouldThrow<KtException.KotlinSimple> {
            ktFunction(null)
        }

        val ktObj = KtObject(100)
        // method -> kotlinMethod, arg -> kotlinArg
        ktObj.kotlinMethod(kotlinArg = 10) shouldBe 110

        // Test enum renaming with dot notation for variants
        KtEnum.KotlinVariantA.shouldBeInstanceOf<KtEnum>() // Variant renamed: VariantA -> KotlinVariantA
        KtEnum.KotlinRecord(ktRecord).v1 shouldBe ktRecord // Variant renamed: Record -> KotlinRecord

        // Test trait renaming.
        val ktTraitImpl: KtTrait = createBindingTraitImpl(3)
        ktTraitImpl.kotlinTraitMethod(4) shouldBe 12

        // Test renamed custom type that also has binding configuration
        val url = Url("https://example.com/test")
        roundtripUrl(url) shouldBe url

        // We can't test excluded items directly, however the tests will fail if the code is not
        // working since that will result in items generated with "" as their name
    }
}
