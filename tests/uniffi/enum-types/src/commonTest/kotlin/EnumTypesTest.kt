/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import enum_types.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class EnumTypesTest {
    @Test
    fun checkVariantValues() {
        AnimalUInt.Dog.value shouldBe 3.toUByte()
        AnimalUInt.Cat.value shouldBe 4.toUByte()

        AnimalLargeUInt.Dog.value shouldBe 4294967298.toULong()
        AnimalLargeUInt.Cat.value shouldBe 4294967299.toULong()

        // could check `value == (-3).toByte()` but that's ugly :)
        AnimalSignedInt.Dog.value + 3 shouldBe 0
    }

    @Test
    fun foo() {
        getAnimalEnum(Animal.Dog).let { a ->
            a.shouldBeInstanceOf<AnimalEnum.Dog>()
            a shouldBe getAnimalEnum(Animal.Dog)
            // markh can't work out how to make this work!?
            // assert(a.v1.getRecord().name == "dog")
        }
    }
}
