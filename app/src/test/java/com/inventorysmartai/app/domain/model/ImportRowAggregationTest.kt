package com.inventorysmartai.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportRowAggregationTest {

    private fun row(status: ImportRowStatus) = ImportRow(
        importJobId = 1L, rowIndex = 0, rawData = "{}", status = status, createdAt = 0L
    )

    @Test
    fun `accepted rows are counted separately from matched or new-product rows`() {
        val rows = listOf(
            row(ImportRowStatus.ACCEPTED),
            row(ImportRowStatus.ACCEPTED),
            row(ImportRowStatus.MATCHED),
            row(ImportRowStatus.NEW_PRODUCT)
        )
        val counts = aggregateImportRowCounts(rows)
        // Two rows were accepted, but they are not the same two rows as "matched" or "new" —
        // acceptedRows must not be conflated with (matchedRows + newProductRows).
        assertEquals(2, counts.acceptedRows)
        assertEquals(1, counts.matchedRows)
        assertEquals(1, counts.newProductRows)
    }

    @Test
    fun `every status bucket is tallied independently`() {
        val rows = listOf(
            row(ImportRowStatus.PENDING),
            row(ImportRowStatus.MATCHED),
            row(ImportRowStatus.NEW_PRODUCT),
            row(ImportRowStatus.DUPLICATE),
            row(ImportRowStatus.ERROR),
            row(ImportRowStatus.ACCEPTED),
            row(ImportRowStatus.REJECTED)
        )
        val counts = aggregateImportRowCounts(rows)
        assertEquals(7, counts.totalRows)
        assertEquals(6, counts.processedRows) // everything except the still-PENDING row
        assertEquals(1, counts.matchedRows)
        assertEquals(1, counts.newProductRows)
        assertEquals(1, counts.duplicateRows)
        assertEquals(1, counts.errorRows)
        assertEquals(1, counts.acceptedRows)
    }

    @Test
    fun `empty batch is all zeros, not an error`() {
        val counts = aggregateImportRowCounts(emptyList())
        assertEquals(0, counts.totalRows)
        assertEquals(0, counts.processedRows)
        assertEquals(0, counts.acceptedRows)
    }
}
