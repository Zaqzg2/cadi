package com.inventorysmartai.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultNormalizerTest {

    private val normalizer = DefaultNormalizer()

    @Test
    fun `Arabic-Indic digits in a barcode normalize to ASCII with internal spaces stripped`() {
        val result = normalizer.normalize(ImportField.BARCODE, "٠١٢٣٤٥")
        assertEquals("٠١٢٣٤٥", result.raw)
        assertEquals("012345", result.normalized)
    }

    @Test
    fun `whitespace inside a barcode is removed entirely, not just trimmed`() {
        val result = normalizer.normalize(ImportField.BARCODE, "123 456 789")
        assertEquals("123456789", result.normalized)
    }

    @Test
    fun `barcode normalization never lowercases or alters letters`() {
        val result = normalizer.normalize(ImportField.ITEM_NUMBER, "ABC-123")
        // Only whitespace/digit-script are touched for identifiers — exact-match semantics
        // against whatever is already stored in the database must be preserved.
        assertEquals("ABC-123", result.normalized)
    }

    @Test
    fun `a plain integer parses with no thousands separator involved`() {
        val result = normalizer.normalize(ImportField.CURRENT_STOCK, "42")
        assertEquals(42.0, result.numeric)
    }

    @Test
    fun `a decimal number parses correctly`() {
        val result = normalizer.normalize(ImportField.QUANTITY, "12.5")
        assertEquals(12.5, result.numeric)
    }

    @Test
    fun `a comma-grouped thousands separator is stripped before parsing`() {
        val result = normalizer.normalize(ImportField.CURRENT_STOCK, "1,234")
        assertEquals(1234.0, result.numeric)
    }

    @Test
    fun `Arabic-Indic digits in a numeric field parse correctly`() {
        val result = normalizer.normalize(ImportField.REQUESTED_QUANTITY, "٥٠")
        assertEquals(50.0, result.numeric)
    }

    @Test
    fun `non-numeric text in a numeric field fails to parse rather than silently becoming zero`() {
        val result = normalizer.normalize(ImportField.CURRENT_STOCK, "غير متوفر")
        assertNull(result.numeric)
        // The raw value is preserved so the validator can report exactly what was wrong.
        assertEquals("غير متوفر", result.raw)
    }

    @Test
    fun `a text field is fully normalized for comparison`() {
        val result = normalizer.normalize(ImportField.PRODUCT_NAME, "  حليب   السعوديه  ")
        assertEquals("حليب السعوديه".let { com.inventorysmartai.app.domain.matching.ArabicTextNormalizer.normalize(it) }, result.normalized)
    }

    @Test
    fun `blank input produces a null normalized value without throwing`() {
        val result = normalizer.normalize(ImportField.BARCODE, "   ")
        assertNull(result.normalized)
        assertNull(result.numeric)
    }

    @Test
    fun `null input produces a null normalized value without throwing`() {
        val result = normalizer.normalize(ImportField.NOTES, null)
        assertNull(result.normalized)
    }
}
