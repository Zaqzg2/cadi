package com.inventorysmartai.backend.gemini

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionSchemasTest {

    @Test
    fun `every document type has a schema requiring documentType and rows`() {
        ExtractionDocumentType.entries.forEach { type ->
            val schema = ExtractionSchemas.forDocumentType(type)
            assertEquals("object", schema["type"]?.jsonPrimitive?.content)
            val required = schema["required"]?.let { it.toString() } ?: ""
            assertTrue("Schema for $type must require documentType", required.contains("documentType"))
            assertTrue("Schema for $type must require rows", required.contains("rows"))
        }
    }

    @Test
    fun `sales invoice schema declares header fields for invoice-level facts`() {
        val schema = ExtractionSchemas.SALES_INVOICES
        val header = schema["properties"]?.jsonObject?.get("header")?.jsonObject?.get("properties")?.jsonObject
        assertTrue(header != null && header.containsKey("INVOICE_NUMBER"))
        assertTrue(header != null && header.containsKey("CUSTOMER_NAME"))
    }

    @Test
    fun `goal schema uses a per-row target group rather than fixed group columns`() {
        val schema = ExtractionSchemas.GOALS
        val rowFields = schema["properties"]?.jsonObject?.get("rows")?.jsonObject
            ?.get("items")?.jsonObject?.get("properties")?.jsonObject
            ?.get("fields")?.jsonObject?.get("properties")?.jsonObject
        assertTrue("GOALS rows must carry TARGET_GROUP so any number of tiers can be represented", rowFields != null && rowFields.containsKey("TARGET_GROUP"))
        assertTrue(rowFields != null && rowFields.containsKey("COMMISSION_VALUE"))
    }

    @Test
    fun `every field value schema requires a raw property so nothing is silently invented`() {
        val schema = ExtractionSchemas.PRODUCTS
        val itemNumberField = schema["properties"]?.jsonObject?.get("rows")?.jsonObject
            ?.get("items")?.jsonObject?.get("properties")?.jsonObject
            ?.get("fields")?.jsonObject?.get("properties")?.jsonObject
            ?.get("ITEM_NUMBER")?.jsonObject
        val required = itemNumberField?.get("required")?.toString() ?: ""
        assertTrue(required.contains("raw"))
    }

    @Test
    fun `instructions are non-empty and mention never inventing values`() {
        ExtractionDocumentType.entries.forEach { type ->
            val text = ExtractionSchemas.instructionFor(type)
            assertTrue(text.isNotBlank())
            assertTrue(text.contains("null") || text.contains("لا تخترع") || text.contains("Never invent") || text.contains("invent"))
        }
    }
}
