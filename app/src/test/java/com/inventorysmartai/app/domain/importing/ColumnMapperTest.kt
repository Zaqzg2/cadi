package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFieldDictionaryTest {

    @Test
    fun `product name variants all resolve to PRODUCT_NAME`() {
        listOf("اسم الصنف", "اسم المنتج", "المنتج", "الصنف", "وصف الصنف").forEach { header ->
            assertEquals("\"$header\" should map to PRODUCT_NAME", ImportField.PRODUCT_NAME, ImportField.suggest(header)?.field)
        }
    }

    @Test
    fun `item number variants all resolve to ITEM_NUMBER`() {
        listOf("رقم الصنف", "رقم المنتج", "كود الصنف", "الكود").forEach { header ->
            assertEquals("\"$header\" should map to ITEM_NUMBER", ImportField.ITEM_NUMBER, ImportField.suggest(header)?.field)
        }
    }

    @Test
    fun `barcode variants including the English forms resolve to BARCODE`() {
        listOf("باركود", "الباركود", "Barcode", "EAN").forEach { header ->
            assertEquals("\"$header\" should map to BARCODE", ImportField.BARCODE, ImportField.suggest(header)?.field)
        }
    }

    @Test
    fun `ambiguous quantity words map to the generic QUANTITY field but are flagged for confirmation`() {
        listOf("الكمية", "العدد", "المخزون", "الرصيد").forEach { header ->
            val suggestion = ImportField.suggest(header)
            assertEquals("\"$header\" should map to the generic QUANTITY field", ImportField.QUANTITY, suggestion?.field)
            assertTrue("\"$header\" must require confirmation, not be silently trusted", suggestion?.requiresConfirmation == true)
        }
    }

    @Test
    fun `specific quantity phrasings are NOT collapsed onto the generic field`() {
        assertEquals(ImportField.CURRENT_STOCK, ImportField.suggest("الرصيد الحالي")?.field)
        assertEquals(ImportField.REQUESTED_QUANTITY, ImportField.suggest("المطلوب")?.field)
        assertEquals(ImportField.REQUESTED_QUANTITY, ImportField.suggest("الكمية المطلوبة")?.field)
        assertEquals(ImportField.COUNTED_QUANTITY, ImportField.suggest("الكمية المجرودة")?.field)
    }

    @Test
    fun `specific quantity phrasings do not require confirmation`() {
        assertEquals(false, ImportField.suggest("المطلوب")?.requiresConfirmation)
        assertEquals(false, ImportField.suggest("الرصيد الحالي")?.requiresConfirmation)
    }

    @Test
    fun `a header with decoration around a known phrase still matches via contains`() {
        assertEquals(ImportField.PRODUCT_NAME, ImportField.suggest("اسم الصنف *")?.field)
    }

    @Test
    fun `a completely unknown header does not match anything`() {
        assertNull(ImportField.suggest("عمود عشوائي غير معروف تمامًا"))
    }

    @Test
    fun `blank header does not match anything`() {
        assertNull(ImportField.suggest("   "))
    }
}

class DefaultColumnMapperTest {

    private val mapper = DefaultColumnMapper()

    @Test
    fun `suggests a full mapping for a clean products header row`() {
        val headers = listOf("اسم الصنف", "الباركود", "رقم الصنف", "التصنيف")
        val result = mapper.suggestMapping(headers, ImportType.PRODUCTS)

        assertEquals(ImportField.PRODUCT_NAME, result.fieldFor(0))
        assertEquals(ImportField.BARCODE, result.fieldFor(1))
        assertEquals(ImportField.ITEM_NUMBER, result.fieldFor(2))
        assertEquals(ImportField.CATEGORY, result.fieldFor(3))
        assertEquals(0, result.unmapped.size)
    }

    @Test
    fun `two columns guessed as the same non-repeatable field collide into one mapped, one unmapped`() {
        // Two different "name-like" columns both suggest PRODUCT_NAME; only the first should win.
        val headers = listOf("اسم الصنف", "اسم المنتج")
        val result = mapper.suggestMapping(headers, ImportType.PRODUCTS)

        assertEquals(ImportField.PRODUCT_NAME, result.fieldFor(0))
        assertNull(result.fieldFor(1))
    }

    @Test
    fun `a field not assignable for this import type is left unmapped`() {
        // TARGET is a GOALS-only field; a PRODUCTS import should not auto-assign it.
        val headers = listOf("اسم الصنف", "الهدف")
        val result = mapper.suggestMapping(headers, ImportType.PRODUCTS)

        assertEquals(ImportField.PRODUCT_NAME, result.fieldFor(0))
        assertNull(result.fieldFor(1))
    }

    @Test
    fun `manual override replaces the auto-suggested field and clears the confirmation flag`() {
        val headers = listOf("الكمية")
        val suggested = mapper.suggestMapping(headers, ImportType.INVENTORY)
        assertTrue(suggested.mappings.first().requiresConfirmation)

        val overridden = mapper.applyOverride(suggested, columnIndex = 0, field = ImportField.CURRENT_STOCK)
        assertEquals(ImportField.CURRENT_STOCK, overridden.fieldFor(0))
        assertEquals(false, overridden.mappings.first().requiresConfirmation)
        assertTrue(overridden.mappings.first().isUserOverride)
    }

    @Test
    fun `override to null clears a column back to unmapped`() {
        val headers = listOf("اسم الصنف")
        val suggested = mapper.suggestMapping(headers, ImportType.PRODUCTS)
        val cleared = mapper.applyOverride(suggested, columnIndex = 0, field = null)
        assertNull(cleared.fieldFor(0))
    }

    @Test
    fun `ignore is always offered even though it is not in the type's assignable fields`() {
        val headers = listOf("عمود غير مهم")
        val result = mapper.suggestMapping(headers, ImportType.GOALS)
        val ignored = mapper.applyOverride(result, columnIndex = 0, field = ImportField.IGNORE)
        assertEquals(ImportField.IGNORE, ignored.fieldFor(0))
        assertEquals(1, ignored.ignored.size)
    }

    @Test
    fun `applying a saved template maps headers by their saved field even when the dictionary would guess differently`() {
        val template = ImportMappingTemplate(
            name = "قالب مخصص",
            importType = ImportType.INVENTORY,
            headerFingerprint = ImportMappingTemplate.fingerprintOf(listOf("العدد")),
            mappingJson = SimpleJson.encodeMap(mapOf(ArabicTextNormalizer.normalize("العدد") to ImportField.CURRENT_STOCK.name)),
            createdAt = 0L,
            updatedAt = 0L
        )
        val result = mapper.applyTemplate(listOf("العدد"), ImportType.INVENTORY, template)
        // Without the template "العدد" would be the ambiguous generic QUANTITY; the template
        // pins it to CURRENT_STOCK instead, with no confirmation flag since it's an explicit choice.
        assertEquals(ImportField.CURRENT_STOCK, result.fieldFor(0))
        assertEquals(false, result.mappings.first().requiresConfirmation)
    }

    @Test
    fun `applying a template falls back to the dictionary for a header the template does not cover`() {
        val template = ImportMappingTemplate(
            name = "قالب جزئي",
            importType = ImportType.PRODUCTS,
            headerFingerprint = ImportMappingTemplate.fingerprintOf(listOf("اسم الصنف")),
            mappingJson = SimpleJson.encodeMap(mapOf(ArabicTextNormalizer.normalize("اسم الصنف") to ImportField.PRODUCT_NAME.name)),
            createdAt = 0L,
            updatedAt = 0L
        )
        // "الباركود" was never part of this template — still resolved via the plain dictionary.
        val result = mapper.applyTemplate(listOf("اسم الصنف", "الباركود"), ImportType.PRODUCTS, template)
        assertEquals(ImportField.PRODUCT_NAME, result.fieldFor(0))
        assertEquals(ImportField.BARCODE, result.fieldFor(1))
    }
}
