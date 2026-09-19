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

    // --- Phase 3 additions: digit-variant and punctuation handling for import normalization ---

    @Test
    fun `Arabic-Indic digits normalize to the same string as ASCII digits`() {
        val arabicIndic = ArabicTextNormalizer.normalize("أرز بسمتي ٥ كجم")
        val ascii = ArabicTextNormalizer.normalize("ارز بسمتي 5 كجم")
        assertEquals(ascii, arabicIndic)
    }

    @Test
    fun `Persian digits normalize to the same string as ASCII digits`() {
        val persian = ArabicTextNormalizer.normalize("أرز بسمتي ۵ كجم")
        val ascii = ArabicTextNormalizer.normalize("ارز بسمتي 5 كجم")
        assertEquals(ascii, persian)
    }

    @Test
    fun `normalizeDigits leaves non-digit characters untouched`() {
        assertEquals("حليب 5", ArabicTextNormalizer.normalizeDigits("حليب \u0665"))
    }

    @Test
    fun `punctuation separating words normalizes like a plain space, without fusing the words`() {
        val withDash = ArabicTextNormalizer.normalize("دجاج-مشوي")
        val withSpace = ArabicTextNormalizer.normalize("دجاج مشوي")
        assertEquals(withSpace, withDash)
    }

    @Test
    fun `Arabic comma and question mark do not change the comparison key`() {
        val withPunctuation = ArabicTextNormalizer.normalize("صنف جديد؟")
        val without = ArabicTextNormalizer.normalize("صنف جديد")
        assertEquals(without, withPunctuation)
    }
}
