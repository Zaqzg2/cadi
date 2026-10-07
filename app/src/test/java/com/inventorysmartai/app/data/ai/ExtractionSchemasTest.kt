package com.inventorysmartai.app.data.ai

import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * These schemas decide the shape of everything the model returns, and AiExtractionMapper silently drops any
 * field name it does not recognise — so drift between the two must fail here, not in front of a user.
 * Pure Kotlin: no Android.
 */
class ExtractionSchemasTest {

    private val allTypes = AiExtractionDocumentType.values().toList()

    private fun rowOf(schema: AiObject): AiObject =
        ((schema.properties.getValue("rows") as AiArray).items as AiObject)

    private fun fieldsOf(schema: AiObject): AiObject = rowOf(schema).properties.getValue("fields") as AiObject

    @Test
    fun `there is a schema for every document type`() {
        assertEquals(6, allTypes.size)
        allTypes.forEach { type ->
            val schema = ExtractionSchemas.forDocumentType(type)
            assertTrue("$type: documentType is a string", schema.properties["documentType"] is AiString)
            assertTrue("$type: rows is an array", schema.properties["rows"] is AiArray)
            assertEquals("$type: only documentType and rows are required", setOf("documentType", "rows"), schema.required)
        }
    }

    @Test
    fun `every row requires fields, and every field value requires raw, confidence and uncertain`() {
        allTypes.forEach { type ->
            val schema = ExtractionSchemas.forDocumentType(type)
            assertEquals("$type: a row only requires fields", setOf("fields"), rowOf(schema).required)

            val fields = fieldsOf(schema)
            assertTrue("$type: fields declares at least one field", fields.properties.isNotEmpty())
            assertTrue("$type: fields are all optional — a field absent from the document is omitted", fields.required.isEmpty())

            fields.properties.forEach { (name, node) ->
                val value = node as AiObject
                assertEquals("$type/$name", setOf("raw", "confidence", "uncertain"), value.required)
                val raw = value.properties.getValue("raw") as AiString
                assertTrue("$type/$name: raw may be null (field not in the document)", raw.nullable)
                assertTrue(value.properties.getValue("confidence") is AiNumber)
                assertTrue(value.properties.getValue("uncertain") is AiBoolean)
            }
        }
    }

    @Test
    fun `every row-level field name is a real ImportField`() {
        val known = ImportField.values().map { it.name }.toSet() - ImportField.IGNORE.name
        allTypes.forEach { type ->
            val unknown = fieldsOf(ExtractionSchemas.forDocumentType(type)).properties.keys - known
            assertTrue("$type asks for fields the mapper would silently drop: $unknown", unknown.isEmpty())
        }
    }

    @Test
    fun `sales invoices carry the header keys that approval reads`() {
        val header = ExtractionSchemas.SALES_INVOICES.properties["header"] as AiObject
        val approvalReads = setOf(
            "INVOICE_NUMBER", "INVOICE_DATE", "CUSTOMER_NAME", "WAREHOUSE",
            "CURRENCY", "PREVIOUS_BALANCE", "INVOICE_TOTAL"
        )
        assertTrue("missing: ${approvalReads - header.properties.keys}", header.properties.keys.containsAll(approvalReads))
        assertTrue("header facts are optional", header.required.isEmpty())
    }

    @Test
    fun `only the types with document-level facts have a header`() {
        assertTrue(ExtractionSchemas.SALES_INVOICES.properties.containsKey("header"))
        assertTrue(ExtractionSchemas.PURCHASE_REQUESTS.properties.containsKey("header"))
        listOf(ExtractionSchemas.PRODUCTS, ExtractionSchemas.INVENTORY, ExtractionSchemas.COUNTING, ExtractionSchemas.GOALS)
            .forEach { assertFalse(it.properties.containsKey("header")) }
    }

    @Test
    fun `goals have one row per tier so they need TARGET_GROUP`() {
        assertTrue(fieldsOf(ExtractionSchemas.GOALS).properties.containsKey("TARGET_GROUP"))
    }

    @Test
    fun `instructions tell the model never to invent values`() {
        allTypes.forEach { type ->
            val text = ExtractionSchemas.instructionFor(type)
            assertTrue("$type", text.contains("Never invent or guess"))
            assertTrue("$type", text.contains("\"raw\""))
        }
        assertTrue(ExtractionSchemas.instructionFor(AiExtractionDocumentType.SALES_INVOICES).contains("a sales invoice"))
    }

    @Test
    fun `optionalProperties is everything not required, in declaration order`() {
        val obj = AiObject(
            properties = linkedMapOf("a" to AiString(), "b" to AiString(), "c" to AiString(), "d" to AiString()),
            required = setOf("c", "a")
        )
        assertEquals(listOf("b", "d"), obj.optionalProperties)
        assertEquals(listOf("x", "y"), AiObject(linkedMapOf("x" to AiString(), "y" to AiString())).optionalProperties)
        assertTrue(AiObject(mapOf("x" to AiString()), required = setOf("x")).optionalProperties.isEmpty())
    }

    @Test
    fun `an object cannot require a property it does not have`() {
        try {
            AiObject(properties = mapOf("a" to AiString()), required = setOf("a", "ghost"))
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("ghost"))
            return
        }
        fail("expected IllegalArgumentException")
    }
}
