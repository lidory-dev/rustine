/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import io.kotest.assertions.throwables.*
import io.kotest.matchers.*
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.test.runTest
import proc_macro.*
import kotlin.test.*

class ProcMacroTest {

    @Test
    fun testMakeOne() {
        val one = makeOne(123)
        one.inner shouldBe 123
        oneInnerByRef(one) shouldBe 123
        one.getInnerValue() shouldBe 123
    }

    @Test
    fun testRecordMethods() = runTest {
        val one = One(41)
        one.addToInner() shouldBe 42
        one.addToInner(2) shouldBe 43
        one.checkedInnerValue() shouldBe 41
        shouldThrow<BasicException.InvalidInput> {
            One(-1).checkedInnerValue()
        }
        one.getInnerValueAsync() shouldBe 41
        one.parsesWithDocAttribute()

        makeRecordWithoutFields().name() shouldBe "RecordWithoutFields"
    }

    @Test
    fun testTwo() {
        val two = Two("a")
        takeTwo(two) shouldBe "a"
    }

    @Test
    fun testRecordWithBytes() {
        val rwb = RecordWithBytes(byteArrayOf(1, 2, 3))
        takeRecordWithBytes(rwb) shouldBe byteArrayOf(1, 2, 3)
    }

    @Test
    @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
    fun testObject() {
        var obj = Object()
        obj = Object.namedCtor(1u)
        obj.isHeavy() shouldBe MaybeBool.UNCERTAIN

        val obj2 = Object()
        obj.isOtherHeavy(obj2) shouldBe MaybeBool.UNCERTAIN
        // `Object` exports `Eq`, and all instances of the unit struct are equal.
        (obj == obj2) shouldBe true
    }

    @Test
    fun testEnumIdentity() {
        enumIdentity(MaybeBool.TRUE) shouldBe MaybeBool.TRUE
        MaybeBool.TRUE.next() shouldBe MaybeBool.FALSE
        enumIdentity(MaybeBool.TRUE).next() shouldBe MaybeBool.FALSE
    }

    @Test
    fun testThree() {
        // just make sure this works / doesn't crash
        Three(Object())
    }

    @Test
    fun testMakeZero() {
        makeZero().inner shouldBe "ZERO"
    }

    @Test
    fun testMakeRecordWithBytes() {
        makeRecordWithBytes().someBytes shouldBe byteArrayOf(0, 1, 2, 3, 4)
    }

    @Test
    fun testHashSet() {
        makeHashSet("hello") shouldBe setOf("hello")
        val hashSet: Set<String> = setOf("a", "b", "c")
        returnHashSet(hashSet) shouldBe hashSet
    }

    @Test
    fun testBorrowedBytes() {
        sumBytesProcmacro(byteArrayOf()) shouldBe 0u
        sumBytesProcmacro(byteArrayOf(1, 2, 3)) shouldBe 6u
        firstByteProcmacro(byteArrayOf()) shouldBe null
        firstByteProcmacro(byteArrayOf(42)) shouldBe 42.toUByte()

        val large = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        sumBytesProcmacro(large) shouldBe large.sumOf { it.toUByte().toUInt() }
        firstByteProcmacro(large) shouldBe 0.toUByte()

        joinBytes(byteArrayOf(1, 2), 0u, byteArrayOf(3)) shouldBe byteArrayOf(1, 2, 0, 3)
        joinBytes(byteArrayOf(), 0u, byteArrayOf()) shouldBe byteArrayOf(0)
        bytesToString("hello".encodeToByteArray()) shouldBe "hello"
        bytesToString(byteArrayOf()) shouldBe ""
        shouldThrow<BasicException.InvalidInput> {
            bytesToString(byteArrayOf(-1))
        }
    }

    @Test
    fun testBorrowedBytesInObjects() {
        val slices = ByteSlices(byteArrayOf(1, 2))
        slices.prefix() shouldBe byteArrayOf(1, 2)
        slices.prependTo(byteArrayOf(3)) shouldBe byteArrayOf(1, 2, 3)
        slices.prependTo(byteArrayOf()) shouldBe byteArrayOf(1, 2)
        slices.getTrait().countByte(byteArrayOf(1, 2, 1), 1u) shouldBe 2u
        slices.getTrait().countByte(byteArrayOf(), 1u) shouldBe 0u

        ByteSlices(byteArrayOf()).prefix() shouldBe byteArrayOf()
        ByteSlices.fromUtf8("abc".encodeToByteArray()).prefix() shouldBe "abc".encodeToByteArray()
        shouldThrow<BasicException.InvalidInput> {
            ByteSlices.fromUtf8(byteArrayOf(-1))
        }

        val large = ByteArray(4 * 1024 * 1024) { it.toByte() }
        ByteSlices(large).prependTo(large).size shouldBe 2 * large.size

        val rwb = RecordWithBytes(byteArrayOf(1, 2, 3))
        rwb.startsWith(byteArrayOf(1, 2)) shouldBe true
        rwb.startsWith(byteArrayOf(2)) shouldBe false
        rwb.startsWith(byteArrayOf()) shouldBe true
    }

    @Test
    fun testJoin() {
        join(listOf("a", "b", "c"), ":") shouldBe "a:b:c"
    }

    @Test
    fun testAlwaysFails() {
        val e = shouldThrow<BasicException> {
            alwaysFails()
        }
        e.isUnexpected() shouldBe false
        BasicException.UnexpectedException("reason").isUnexpected() shouldBe true
    }

    @Test
    fun testDoStuffWith5() {
        val obj = Object.namedCtor(1u)
        obj.doStuff(5u)
    }

    @Test
    fun testDoStuffWith0() {
        shouldThrow<FlatException.InvalidInput> {
            val obj = Object.namedCtor(1u)
            obj.doStuff(0u)
        }
    }

    @Test
    fun testRecordWithDefaults() {
        val recordWithDefaults = RecordWithDefaults("Test")
        recordWithDefaults.noDefaultString shouldBe "Test"
        recordWithDefaults.boolean shouldBe true
        recordWithDefaults.integer shouldBe 42
        recordWithDefaults.floatVar shouldBe 4.2
        recordWithDefaults.vec shouldBe beEmpty<Boolean>()
        recordWithDefaults.optVec shouldBe null
        recordWithDefaults.optInteger shouldBe 42
        recordWithDefaults.customInteger shouldBe 42
        recordWithDefaults.booleanDefault shouldBe false
        recordWithDefaults.stringDefault shouldBe ""
        recordWithDefaults.optDefault shouldBe null
        recordWithDefaults.sub.int32 shouldBe 0

        doubleWithDefault() shouldBe 42
        sumWithDefault(2) shouldBe 2
        sumWithDefault(2, 1) shouldBe 3
    }

    @Test
    fun testRecordWithImplicitDefaults() {
        val recordWithImplicitDefaults = RecordWithImplicitDefaults()
        recordWithImplicitDefaults.boolean shouldBe false
        recordWithImplicitDefaults.int8 shouldBe 0.toByte()
        recordWithImplicitDefaults.int16 shouldBe 0.toShort()
        recordWithImplicitDefaults.int32 shouldBe 0
        recordWithImplicitDefaults.int64 shouldBe 0L
        recordWithImplicitDefaults.uint8 shouldBe 0U.toUByte()
        recordWithImplicitDefaults.uint16 shouldBe 0U.toUShort()
        recordWithImplicitDefaults.uint32 shouldBe 0U
        recordWithImplicitDefaults.uint64 shouldBe 0UL
        recordWithImplicitDefaults.afloat shouldBe 0f
        recordWithImplicitDefaults.adouble shouldBe 0.0
        recordWithImplicitDefaults.vec shouldBe beEmpty<Boolean>()
        recordWithImplicitDefaults.map shouldBe emptyMap<String, String>()
        recordWithImplicitDefaults.set shouldBe emptySet<String>()
        recordWithImplicitDefaults.someBytes.size shouldBe 0
        recordWithImplicitDefaults.optInt32 shouldBe null
        recordWithImplicitDefaults.`object` shouldBe Object()
        recordWithImplicitDefaults.customInteger shouldBe 0
    }

    @Test
    fun testObjectWithDefaults() {
        val objWithDefaults = ObjectWithDefaults()
        objWithDefaults.addToNum() shouldBe 42
        objWithDefaults.addToImplicitNum() shouldBe 30
        objWithDefaults.addToImplicitNum(1) shouldBe 31
    }

    @Test
    fun testReprU8() {
        ReprU8.entries.map { it.value } shouldBe listOf<UByte>(1u, 3u, 4u)
    }

    @Test
    fun testTraitImpl() {
        val obj = Object.namedCtor(1u)
        val traitImpl = obj.getTrait(null)
        traitImpl.concatStrings("foo", "bar") shouldBe "foobar"
        obj.getTrait(traitImpl).concatStrings("foo", "bar") shouldBe "foobar"
        concatStringsByRef(traitImpl, "foo", "bar") shouldBe "foobar"

        val traitImpl2 = obj.getTraitWithForeign(null)
        traitImpl2.name() shouldBe "RustTraitImpl"
        obj.getTraitWithForeign(traitImpl2).name() shouldBe "RustTraitImpl"
    }

    class KtTestCallbackInterface : TestCallbackInterface {
        override fun doNothing() {}

        override fun add(a: UInt, b: UInt) = a + b

        override fun optional(a: UInt?) = a ?: 0u

        override fun withBytes(rwb: RecordWithBytes) = rwb.someBytes

        override fun tryParseInt(value: String): UInt {
            if (value == "force-unexpected-error") {
                // raise an error that's not expected
                throw RuntimeException(value)
            }
            try {
                return value.toUInt()
            } catch (e: NumberFormatException) {
                throw BasicException.InvalidInput()
            }
        }

        override fun callbackHandler(h: Object): UInt {
            val v = h.takeError(BasicException.InvalidInput())
            return v
        }

        override fun getOtherCallbackInterface() = KtTestCallbackInterface2()
    }

    class KtTestCallbackInterface2 : OtherCallbackInterface {
        override fun multiply(a: UInt, b: UInt) = a * b
    }

    @Test
    fun testCallbackInterface() {
        callCallbackInterface(KtTestCallbackInterface())
    }

    @Test
    fun testMixedEnum() {
        getMixedEnum(null) shouldBe MixedEnum.Int(1)
        getMixedEnum(MixedEnum.None) shouldBe MixedEnum.None
        getMixedEnum(MixedEnum.String("hello")) shouldBe MixedEnum.String("hello")
        getMixedEnum(MixedEnum.None).isNotNone() shouldBe false
        MixedEnum.None.isNotNone() shouldBe false
        MixedEnum.Int(1).isNotNone() shouldBe true

        val e = getMixedEnum(null)
        e.shouldBeTypeOf<MixedEnum.Int>()
        run {
            // you can destruct the enum into its bits.
            val (i) = e
            i shouldBe 1L
        }
        val eb = MixedEnum.Both("hi", 2)
        val (s, i) = eb
        s shouldBe "hi"
        i shouldBe 2L

        getMixedEnum(MixedEnum.Vec(listOf("hello"))) shouldBe MixedEnum.Vec(listOf("hello"))
    }
}
