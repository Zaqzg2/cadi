package com.inventorysmartai.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    private val detector = DefaultDuplicateDetector()

    private fun row(barcode: String? = null, itemNumber: String? = null, name: String? = null) = ParsedImportRow(
        importJobId = 1L,
        rowIndex = 0,
        itemNumber = itemNumber,
        barcode = barcode,
        name = name,
        quantity = null,
        rawJson = "{}"
    )

    @Test
    fun `two rows with the same barcode are flagged as duplicates, first occurrence kept as original`() {
        val rows = listOf(row(barcode = "123456"), row(barcode = "123456"))
        val groups = detector.findInFileDuplicates(rows)
        assertEquals(1, groups.size)
        assertEquals(listOf(1), groups.first().duplicateRowIndexes)
    }

    @Test
    fun `barcode takes precedence over item number and name when both are present`() {
        // Same barcode but different item numbers -- still grouped by barcode, the higher-priority key.
        val rows = listOf(
            row(barcode = "999", itemNumber = "A1"),
            row(barcode = "999", itemNumber = "A2")
        )
        val groups = detector.findInFileDuplicates(rows)
        assertEquals(1, groups.size)
        assertEquals("barcode:999", groups.first().key)
    }

    @Test
    fun `rows with the same item number but no barcode are flagged as duplicates`() {
        val rows = listOf(row(itemNumber = "SKU-1"), row(itemNumber = "SKU-1"))
        val groups = detector.findInFileDuplicates(rows)
        assertEquals(1, groups.size)
    }

    @Test
    fun `rows matching by normalized name only are flagged as duplicates`() {
        val rows = listOf(row(name = "حليب السعودية"), row(name = "حليب السعوديه"))
        val groups = detector.findInFileDuplicates(rows)
        assertEquals(1, groups.size)
    }

    @Test
    fun `rows with no identity at all are never flagged as duplicates of each other`() {
        val rows = listOf(row(), row())
        assertTrue(detector.findInFileDuplicates(rows).isEmpty())
    }

    @Test
    fun `three rows sharing one identity produce two duplicate indexes, not three`() {
        val rows = listOf(row(barcode = "1"), row(barcode = "1"), row(barcode = "1"))
        val groups = detector.findInFileDuplicates(rows)
        assertEquals(1, groups.size)
        assertEquals(listOf(1, 2), groups.first().duplicateRowIndexes)
    }

    @Test
    fun `different products are never flagged as duplicates`() {
        val rows = listOf(row(barcode = "1", name = "أرز"), row(barcode = "2", name = "سكر"))
        assertTrue(detector.findInFileDuplicates(rows).isEmpty())
    }
}
