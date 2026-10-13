/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import enum_payload_clash.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class EnumPayloadClashTest {
    // Test basic enum with name clash
    @Test
    fun testVariantNamedLikeItsPayload() {
        val value = getValue()
        value.shouldBeInstanceOf<AmountOrMax.Amount>()
        value.amount.value shouldBe 100UL
    }

    // Test complex nested types to cover fully_qualified_type_label paths
    @Test
    fun testComplexValue() {
        val complexValue = getComplexValue()
        complexValue.shouldBeInstanceOf<ComplexValue.Amounts>()
        complexValue.amounts.size shouldBe 2
        complexValue.amounts[0].value shouldBe 1UL
        complexValue.amounts[1].value shouldBe 2UL
    }

    // Test Optional type path
    @Test
    fun testOptionalAmount() {
        val optionalAmount = ComplexValue.OptionalAmount(Amount(42UL))
        optionalAmount.maybeAmount?.value shouldBe 42UL
    }

    // Test Map type path
    @Test
    fun testAmountMap() {
        val amountMap = ComplexValue.AmountMap(mapOf("key1" to Amount(10UL), "key2" to Amount(20UL)))
        amountMap.amountMap["key1"]?.value shouldBe 10UL
        amountMap.amountMap["key2"]?.value shouldBe 20UL
    }

    // Test deeply nested Optional<Sequence>
    @Test
    fun testNestedOptional() {
        val nestedOptional = ComplexValue.NestedOptional(listOf(Amount(1UL), Amount(2UL)))
        nestedOptional.maybeAmounts?.size shouldBe 2
    }

    // Test deeply nested Map<String, Sequence>
    @Test
    fun testNestedMap() {
        val nestedMap = ComplexValue.NestedMap(
            mapOf(
                "list1" to listOf(Amount(1UL), Amount(2UL)),
                "list2" to listOf(Amount(3UL))
            )
        )
        nestedMap.nestedMap["list1"]?.size shouldBe 2
        nestedMap.nestedMap["list2"]?.size shouldBe 1
    }

    @Test
    fun testMethodReturningShadowedType() {
        getValue().amount() shouldBe Amount(100UL)
        AmountOrMax.Max.amount() shouldBe null
    }

    @Test
    fun testVariantsNamedLikeCollections() {
        val list = AmountCollection.List(listOf(Amount(1UL)))
        roundtripCollection(list) shouldBe list
        val map = AmountCollection.Map(mapOf("a" to Amount(2UL)))
        roundtripCollection(map) shouldBe map
        val set = AmountCollection.Set(setOf("b"))
        roundtripCollection(set) shouldBe set
    }

    @Test
    fun testErrorVariantsNamedLikeTheirPayload() {
        checkAmount(Amount(1UL)) shouldBe Amount(1UL)
        shouldThrow<AmountException.Amount> {
            checkAmount(Amount(1001UL))
        }.amount shouldBe Amount(1001UL)

        val amounts = listOf(Amount(1UL), Amount(2UL), Amount(3UL))
        shouldThrow<AmountException.List> {
            checkAmounts(amounts)
        }.amounts shouldBe amounts
    }

    @Test
    fun testMethodThrowingShadowedError() {
        AmountChange.Add(Amount(1UL)).applyTo(Amount(2UL)) shouldBe Amount(3UL)
        shouldThrow<AmountOverflow.Overflow> {
            AmountChange.Add(Amount(ULong.MAX_VALUE)).applyTo(Amount(1UL))
        }
        shouldThrow<AmountOverflow.Overflow> {
            AmountChange.AmountOverflow.applyTo(Amount(1UL))
        }
    }

    @Test
    fun testVariantsNamedLikeTheTypesOfTraitMembers() {
        val string = Value.String("a")
        string.toString() shouldBe "String(a)"
        Value.Any.toString() shouldBe "Any"
        roundtripValue(string) shouldBe string
        (Value.Int(1) == Value.Int(1)).shouldBeTrue()
        (Value.Int(1) != Value.Int(2)).shouldBeTrue()
        Value.Boolean(true).hashCode() shouldBe roundtripValue(Value.Boolean(true)).hashCode()
        (Value.String("z") < Value.Int(0)).shouldBeTrue()
        (Value.Boolean(false) < Value.Any).shouldBeTrue()
    }

    @Test
    fun testByteArrayVariantsNamedLikeKotlinTypes() {
        val blob = Blob.Int(byteArrayOf(1, 2))
        roundtripBlob(blob) shouldBe blob
        blob.hashCode() shouldBe Blob.Int(byteArrayOf(1, 2)).hashCode()
        (Blob.Boolean(byteArrayOf(1)) == Blob.Boolean(byteArrayOf(1))).shouldBeTrue()
        (Blob.Any(byteArrayOf(1)) != Blob.Any(byteArrayOf(2))).shouldBeTrue()
    }

    @Test
    fun testErrorVariantsNamedLikeString() {
        expectBoolean(Value.Boolean(true)) shouldBe true
        shouldThrow<ValueKindException.String> {
            expectBoolean(Value.String("a"))
        }.message shouldBe "value=a"
        shouldThrow<ValueKindException.Any> {
            expectBoolean(Value.Int(1))
        }
        expectBooleanFlat(Value.Boolean(false)) shouldBe false
        shouldThrow<FlatValueKindException.String> {
            expectBooleanFlat(Value.String("a"))
        }.message shouldBe "unexpected string"
    }
}
