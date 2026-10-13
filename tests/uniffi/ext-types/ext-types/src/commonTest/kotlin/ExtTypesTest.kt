/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import kotlin.test.Test
import ext_types.*
import custom_types.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import sub_lib.*
import uniffi_one.*
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking

class ExtTypesTest {
    @Test
    fun run() {
        // First step: implement a trait from an external crate in Kotlin and pass it to a function from this
        // crate.  This tests mozilla/uniffi-rs#2343 -- the codegen for this module needs to initialize the vtable from
        // uniffi_one.
        class KtUniffiOneImpl: UniffiOneTrait {
            override fun hello(): String {
                return "Hello from Kotlin"
            }
        }
        invokeUniffiOneTrait(KtUniffiOneImpl()) shouldBe "Hello from Kotlin"

        val ct = getCombinedType(null)
        ct.uot.sval shouldBe "hello"
        ct.guid shouldBe  "a-guid"
        ct.url shouldBe  Url("http://example.com/")

        val ct2 = getCombinedType(ct)
        ct shouldBe ct2

        getObjectsType(null).maybeInterface shouldBe null
        getObjectsType(null).maybeTrait shouldBe null
        getUniffiOneTrait(null) shouldBe null

        getSubType(null).maybeInterface shouldBe null
        getTraitImpl().hello() shouldBe "sub-lib trait impl says hello"

        val url = Url("http://example.com/")
        getUrl(url) shouldBe  url
        getMaybeUrl(url)!! shouldBe  url
        getMaybeUrl(null) shouldBe  null
        getUrls(listOf(url)) shouldBe  listOf(url)
        getMaybeUrls(listOf(url, null)) shouldBe listOf(url, null)

        getGuid("guid") shouldBe "guid"
        getOuid("ouid") shouldBe "ouid"
        //getImportedGuid("guid") shouldBe "guid"
        getImportedOuid("ouid") shouldBe "ouid"
        getImportedHandleU8(null) shouldBe 3u.toUByte()
        runBlocking {
            getNestedExternalOuidAsync(null) shouldBe "nested-external-ouid"
            getLocalExternalGuidAsync() shouldBe "local-external-guid"
        }

        val uot = UniffiOneType("hello")
        getUniffiOneType(uot) shouldBe uot
        getMaybeUniffiOneType(uot)!! shouldBe uot
        getMaybeUniffiOneType(null) shouldBe null
        getUniffiOneTypes(listOf(uot)) shouldBe listOf(uot)
        getMaybeUniffiOneTypes(listOf(uot, null)) shouldBe listOf(uot, null)

        val uopmt = UniffiOneProcMacroType("hello from proc-macro world")
        getUniffiOneProcMacroType(uopmt) shouldBe uopmt
        getMyProcMacroType(uopmt) shouldBe uopmt

        val uoe = UniffiOneEnum.ONE
        getUniffiOneEnum(uoe) shouldBe uoe
        getMaybeUniffiOneEnum(uoe)!! shouldBe uoe
        getMaybeUniffiOneEnum(null) shouldBe null
        getUniffiOneEnums(listOf(uoe)) shouldBe listOf(uoe)
        getMaybeUniffiOneEnums(listOf(uoe, null)) shouldBe listOf(uoe, null)

        run {
            val e = shouldThrow<UniffiOneException> {
                throwUniffiOneError()
            }
            e.shouldBeTypeOf<UniffiOneException.Oops>()
            e.v1 shouldBe "oh no"
        }

        run {
            val e = shouldThrow<UniffiOneErrorInterface> {
                throwUniffiOneErrorInterface()
            }
            e.message() shouldBe "interface oops"
        }

        ct.ecd.sval shouldBe "ecd"
        getExternalCrateInterface("foo").value() shouldBe "foo"

        // An error of another crate, which is only used as a field and an argument here
        // (mozilla/uniffi-rs#2641).
        val notToThrow = NotToThrowException.Variant(1UL)
        ContainsExternalException.ExternalException(notToThrow).v1 shouldBe notToThrow
        ContainsExternalError2(notToThrow).error shouldBe notToThrow
        takesExternalError(notToThrow)

        // `BindingRenamedType` is renamed in the config of uniffi-one.
        val renamedType: KotlinRenamedType = getBindingRenamedType("external_rename_test")
        renamedType.kotlinValue shouldBe "external_rename_test"
        getBindingRenamedType(null).kotlinValue shouldBe "test_renamed_type"
        getBindingRenamedTypes(listOf("a", "b")) shouldBe listOf(
            KotlinRenamedType("a"),
            KotlinRenamedType("b"),
        )
        getMaybeBindingRenamedType("maybe")?.kotlinValue shouldBe "maybe"
        getMaybeBindingRenamedType(null) shouldBe null
        roundtripBoxedBindingRenamedType(renamedType) shouldBe renamedType
    }
}