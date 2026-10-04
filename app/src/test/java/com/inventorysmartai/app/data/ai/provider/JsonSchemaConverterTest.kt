package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.data.ai.AiObject
import com.inventorysmartai.app.data.ai.AiString
import com.inventorysmartai.app.data.ai.ExtractionSchemas
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonSchemaConverterTest {

    @Test
    fun `nullable scalars become a type union with null`() {
        val schema = JsonSchemaConverter.toJsonSchema(AiString(nullable = true, description = "d"))
        assertEquals(listOf("string", "null"), schema["type"])
        assertEquals("d", schema["description"])
    }

    @Test
    fun `required lists only the required properties, in declaration order`() {
        val node = AiObject(
            properties = mapOf("a" to AiString(), "b" to AiString(), "c" to AiString()),
            required = setOf("c", "a")
        )
        assertEquals(listOf("a", "c"), JsonSchemaConverter.toJsonSchema(node)["required"])
    }

    @Test
    fun `every document type produces schema text that mentions its own fields`() {
        for (type in AiExtractionDocumentType.entries) {
            val text = JsonSchemaConverter.toSchemaText(ExtractionSchemas.forDocumentType(type))
            assertTrue("$type", text.contains("\"rows\""))
            assertTrue("$type", text.contains("PRODUCT_NAME") || text.contains("TARGET_GROUP"))
        }
    }

    @Test
    fun `sales invoice schema carries header fields`() {
        val text = JsonSchemaConverter.toSchemaText(ExtractionSchemas.SALES_INVOICES)
        assertTrue(text.contains("INVOICE_NUMBER"))
        assertTrue(text.contains("INVOICE_TOTAL"))
    }
}
