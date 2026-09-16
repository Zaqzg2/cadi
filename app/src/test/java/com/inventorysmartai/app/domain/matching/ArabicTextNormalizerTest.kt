package com.inventorysmartai.app.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicTextNormalizerTest {

    @Test
    fun `ta-marbuta and ha variants normalize to the same string (the spec's own example)`() {
        val a = ArabicTextNormalizer.normalize("حليب السعودية")
        val b = ArabicTextNormalizer.normalize("حليب السعوديه")
        assertEquals(a, b)
    }

    @Test
    fun `alef variants normalize to bare alef`() {
        val withHamza = ArabicTextNormalizer.normalize("أرز بسمتي")
        val bare = ArabicTextNormalizer.normalize("ارز بسمتي")
        assertEquals(withHamza, bare)
    }

    @Test
    fun `diacritics are stripped`() {
        val withTashkeel = ArabicTextNormalizer.normalize("حَلِيبٌ")
        val without = ArabicTextNormalizer.normalize("حليب")
        assertEquals(without, withTashkeel)
    }

    @Test
    fun `extra internal whitespace collapses`() {
        val spaced = ArabicTextNormalizer.normalize("حليب    سعودي")
        val normal = ArabicTextNormalizer.normalize("حليب سعودي")
        assertEquals(normal, spaced)
    }

    @Test
    fun `different products still normalize differently`() {
        val milk = ArabicTextNormalizer.normalize("حليب سعودي كامل الدسم")
        val rice = ArabicTextNormalizer.normalize("أرز بسمتي 5 كجم")
        assert(milk != rice)
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", ArabicTextNormalizer.normalize("   "))
    }
}
