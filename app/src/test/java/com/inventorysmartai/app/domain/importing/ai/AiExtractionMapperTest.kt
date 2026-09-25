package com.inventorysmartai.app.domain.importing.ai

import com.inventorysmartai.app.domain.importing.DefaultNormalizer
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiExtractionMapperTest {

    private val mapper = AiExtractionMapper(DefaultNormalizer())

    @Test
    fun `raw text is normalized exactly like a spreadsheet cell would be`() {
        val document = AiExtractionDocument(
            documentType = "PRODUCTS",
            rows = listOf(
                AiExtractionRow(
                    fields = mapOf(
                        "PRODUCT_NAME" to AiFieldValue(raw = "أرز بسمتي", confidence = 0.95, uncertain = false),
                        "BARCODE" to AiFieldValue(raw = " 123456 ", confidence = 0.9, uncertain = false)
                    )
                )
            )
        )

        val result = mapper.toParsedRows(document, ImportType.PRODUCTS, importJobId = 1L)

        assertEquals(1, result.rows.size)
        val row = result.rows.first()
        assertEquals("123456", row.barcode) // Normalizer trims whitespace, same as a tabular cell would
        assertEquals("أرز بسمتي", row.name)
        assertTrue(row.sourceWarnings.isEmpty())
    }

    @Test
    fun `an uncertain field becomes a source warning the reviewer can see`() {
        val document = AiExtractionDocument(
            documentType = "PURCHASE_REQUESTS",
            rows = listOf(
                AiExtractionRow(
                    sourcePage = 1,
                    fields = mapOf(
                        "PRODUCT_NAME" to AiFieldValue(raw = "سكر", confidence = 0.9, uncertain = false),
                        "REQUESTED_QUANTITY" to AiFieldValue(raw = "5", confidence = 0.4, uncertain = true)
                    ),
                    rowWarnings = listOf("خط اليد غير واضح في هذا السطر")
                )
            )
        )

        val result = mapper.toParsedRows(document, ImportType.PURCHASE_REQUESTS, importJobId = 2L)

        val row = result.rows.first()
        assertEquals(5.0, row.quantity) // still parsed — uncertain flags for review, never silently drops a value
        assertEquals(2, row.sourceWarnings.size)
        assertTrue(row.sourceWarnings.any { it.contains("الكمية المطلوبة") })
        assertTrue(row.sourceWarnings.any { it.contains("خط اليد") })
    }

    @Test
    fun `a document-level header becomes metadataJson, never a per-row field`() {
        val document = AiExtractionDocument(
            documentType = "SALES_INVOICES",
            header = mapOf(
                "INVOICE_NUMBER" to AiFieldValue(raw = "INV-100", confidence = 0.9, uncertain = false),
                "CUSTOMER_NAME" to AiFieldValue(raw = "شركة الأمل", confidence = 0.85, uncertain = false)
            ),
            rows = listOf(
                AiExtractionRow(fields = mapOf("PRODUCT_NAME" to AiFieldValue(raw = "زيت", confidence = 0.9, uncertain = false)))
            )
        )

        val result = mapper.toParsedRows(document, ImportType.SALES_INVOICES, importJobId = 3L)

        assertTrue(result.metadataJson!!.contains("INV-100"))
        assertTrue(result.metadataJson!!.contains("شركة الأمل"))
        // Header-only concepts (invoice number/customer) are never valid ImportField names, so
        // they structurally cannot end up in a row's own fields map — only PRODUCT_NAME here.
        assertEquals(setOf(ImportField.PRODUCT_NAME), result.rows.first().fields.keys)
    }

    @Test
    fun `several rows for the same product keep their own TARGET_GROUP for later grouping`() {
        val document = AiExtractionDocument(
            documentType = "GOALS",
            rows = listOf(
                AiExtractionRow(
                    fields = mapOf(
                        "PRODUCT_NAME" to AiFieldValue(raw = "عصير", confidence = 0.9, uncertain = false),
                        "TARGET_GROUP" to AiFieldValue(raw = "المجموعة الأولى", confidence = 0.9, uncertain = false),
                        "TARGET" to AiFieldValue(raw = "100", confidence = 0.9, uncertain = false)
                    )
                ),
                AiExtractionRow(
                    fields = mapOf(
                        "PRODUCT_NAME" to AiFieldValue(raw = "عصير", confidence = 0.9, uncertain = false),
                        "TARGET_GROUP" to AiFieldValue(raw = "المجموعة الثانية", confidence = 0.9, uncertain = false),
                        "TARGET" to AiFieldValue(raw = "200", confidence = 0.9, uncertain = false)
                    )
                )
            )
        )

        val result = mapper.toParsedRows(document, ImportType.GOALS, importJobId = 4L)

        assertEquals(2, result.rows.size)
        assertEquals(100.0, result.rows[0].quantity)
        assertEquals(200.0, result.rows[1].quantity)
        assertEquals("المجموعة الأولى", result.rows[0].fields[ImportField.TARGET_GROUP]?.raw)
        assertEquals("المجموعة الثانية", result.rows[1].fields[ImportField.TARGET_GROUP]?.raw)
    }
}
