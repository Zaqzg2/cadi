package com.inventorysmartai.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {
    @Test fun parsesLatinDigits() = assertEquals(12.5, "12.5".toDecimalOrNull()!!, 0.0)
    @Test fun parsesArabicIndicDigits() = assertEquals(35.0, "٣٥".toDecimalOrNull()!!, 0.0)
    @Test fun parsesPersianDigits() = assertEquals(7.0, "۷".toDecimalOrNull()!!, 0.0)
    @Test fun arabicDecimalSeparator() = assertEquals(2.5, "٢٫٥".toDecimalOrNull()!!, 0.0)
    @Test fun commaAsDecimal() = assertEquals(1.5, "1,5".toDecimalOrNull()!!, 0.0)
    @Test fun blankIsNull() = assertNull("  ".toDecimalOrNull())
    @Test fun garbageIsNull() = assertNull("abc".toDecimalOrNull())
    @Test fun infinityIsNull() = assertNull("Infinity".toDecimalOrNull())
}
