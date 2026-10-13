/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class RenamesTest {
    // Items renamed with the `name` arguments of the proc-macros
    @Test
    fun testRenamesInRust() {
        val rec = RenamedRecord(item = 42)
        rec.item shouldBe 42

        RenamedEnum.RenamedVariant.shouldBeInstanceOf<RenamedEnum>()
        RenamedEnum.Record(rec).v1 shouldBe rec

        val returnedRec = renamedFunction(rec)
        returnedRec.shouldBeInstanceOf<RenamedEnum.Record>()
        returnedRec.v1 shouldBe rec

        val obj = RenamedObject.renamedConstructor(123)
        obj.renamedMethod() shouldBe 123

        val traitImpl = createTraitImpl(5)
        traitImpl.renamedTraitMethod(10) shouldBe 50
    }

    // Items renamed with the `rename` table of uniffi.toml
    @Test
    fun testRenamesInBindings() {
        val ktRec = KtRecord(ktItem = 100)
        ktRec.ktItem shouldBe 100

        // Test constructing BindingEnumToRename variants
        KtEnum.KtVariantA.shouldBeInstanceOf<KtEnum>()
        KtEnum.KtRecord(ktRec).v1 shouldBe ktRec
        KtEnumWithFields.KtVariantA(1u).ktInt shouldBe 1u

        val returnedKtRec = ktFunction(ktRec)
        returnedKtRec.shouldBeInstanceOf<KtEnum.KtRecord>()
        returnedKtRec.v1 shouldBe ktRec

        shouldThrow<KtException.KtSimple> {
            ktFunction(null)
        }

        val ktObj = KtObject(200)
        ktObj.ktMethod(50) shouldBe 250
        ktObj.ktMethod(ktArg = 50) shouldBe 250

        val ktTraitImpl = createBindingTraitToRenameImpl(3)
        ktTraitImpl.ktTraitMethod(7) shouldBe 21
    }
}
