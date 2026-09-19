package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.model.ImportErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportValidatorTest {

    private val validator = DefaultImportValidator()

    private fun row(
        importType: ImportType,
        name: String? = "صنف تجريبي",
        itemNumber: String? = null,
        barcode: String? = null,
        fields: Map<ImportField, NormalizedValue> = emptyMap()
    ) = ParsedImportRow(
        importJobId = 1L,
        rowIndex = 0,
        itemNumber = itemNumber,
        barcode = barcode,
        name = name,
        quantity = null,
        rawJson = "{}",
        importType = importType,
        fields = fields
    )

    @Test
    fun `a row with no item number, barcode, or name fails with MISSING_REQUIRED_FIELD`() {
        val result = validator.validate(row(ImportType.PRODUCTS, name = null))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == ImportErrorCode.MISSING_REQUIRED_FIELD })
    }

    @Test
    fun `a PRODUCTS row needs only identification, no quantity field at all`() {
        val result = validator.validate(row(ImportType.PRODUCTS, name = "أرز بسمتي"))
        assertTrue(result.isValid)
    }

    @Test
    fun `an INVENTORY row without CURRENT_STOCK fails validation`() {
        val result = validator.validate(row(ImportType.INVENTORY, name = "أرز بسمتي"))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == ImportField.CURRENT_STOCK })
    }

    @Test
    fun `an INVENTORY row with a valid CURRENT_STOCK passes`() {
        val fields = mapOf(ImportField.CURRENT_STOCK to NormalizedValue(raw = "10", normalized = "10", numeric = 10.0))
        val result = validator.validate(row(ImportType.INVENTORY, name = "أرز بسمتي", fields = fields))
        assertTrue(result.isValid)
    }

    @Test
    fun `a COUNTING row requires COUNTED_QUANTITY specifically, CURRENT_STOCK does not satisfy it`() {
        val fields = mapOf(ImportField.CURRENT_STOCK to NormalizedValue(raw = "10", normalized = "10", numeric = 10.0))
        val result = validator.validate(row(ImportType.COUNTING, name = "أرز بسمتي", fields = fields))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == ImportField.COUNTED_QUANTITY })
    }

    @Test
    fun `a PURCHASE_REQUESTS row requires REQUESTED_QUANTITY`() {
        val result = validator.validate(row(ImportType.PURCHASE_REQUESTS, name = "أرز بسمتي"))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == ImportField.REQUESTED_QUANTITY })
    }

    @Test
    fun `a GOALS row requires TARGET`() {
        val result = validator.validate(row(ImportType.GOALS, name = "أرز بسمتي"))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == ImportField.TARGET })
    }

    @Test
    fun `a numeric field with an unparseable raw value raises INVALID_NUMBER`() {
        val fields = mapOf(ImportField.CURRENT_STOCK to NormalizedValue(raw = "غير صالح", normalized = "غير صالح", numeric = null))
        val result = validator.validate(row(ImportType.INVENTORY, name = "أرز بسمتي", fields = fields))
        assertTrue(result.errors.any { it.code == ImportErrorCode.INVALID_NUMBER })
    }

    @Test
    fun `a negative quantity raises INVALID_NUMBER even though it parsed fine`() {
        val fields = mapOf(ImportField.CURRENT_STOCK to NormalizedValue(raw = "-5", normalized = "-5", numeric = -5.0))
        val result = validator.validate(row(ImportType.INVENTORY, name = "أرز بسمتي", fields = fields))
        assertTrue(result.errors.any { it.code == ImportErrorCode.INVALID_NUMBER && it.field == ImportField.CURRENT_STOCK })
    }

    @Test
    fun `an unusual barcode is a warning, not an error, and does not block the row`() {
        val result = validator.validate(row(ImportType.PRODUCTS, name = "صنف", barcode = "AB-not-a-real-barcode"))
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.code == ImportErrorCode.INVALID_BARCODE })
    }

    @Test
    fun `a normal-looking numeric barcode raises no warning`() {
        val result = validator.validate(row(ImportType.PRODUCTS, name = "صنف", barcode = "6291041500213"))
        assertTrue(result.warnings.isEmpty())
    }
}
