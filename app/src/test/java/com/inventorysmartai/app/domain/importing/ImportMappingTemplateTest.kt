package com.inventorysmartai.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportMappingTemplateTest {

    private fun template(headers: List<String>) = ImportMappingTemplate(
        name = "قالب",
        importType = ImportType.INVENTORY,
        headerFingerprint = ImportMappingTemplate.fingerprintOf(headers),
        mappingJson = "{}",
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `identical header sets match at a ratio of exactly 1`() {
        val t = template(listOf("اسم الصنف", "الباركود", "الكمية"))
        assertEquals(1.0, t.structureMatchRatio(listOf("اسم الصنف", "الباركود", "الكمية")), 0.0001)
    }

    @Test
    fun `column order does not affect the match ratio`() {
        val t = template(listOf("اسم الصنف", "الباركود", "الكمية"))
        assertEquals(1.0, t.structureMatchRatio(listOf("الكمية", "اسم الصنف", "الباركود")), 0.0001)
    }

    @Test
    fun `a completely different header set scores zero`() {
        val t = template(listOf("اسم الصنف", "الباركود"))
        assertEquals(0.0, t.structureMatchRatio(listOf("عمود أ", "عمود ب")), 0.0001)
    }

    @Test
    fun `partial overlap is a fraction of the CURRENT file's headers, not the template's`() {
        val t = template(listOf("اسم الصنف", "الباركود", "الكمية", "الفرع"))
        // Current file only has 2 headers, both of which the template recognizes -> ratio is
        // relative to the 2 current headers (2/2 = 1.0), not the template's original 4.
        assertEquals(1.0, t.structureMatchRatio(listOf("اسم الصنف", "الباركود")), 0.0001)
    }

    @Test
    fun `a file with one matching and one unknown header is offered only above the threshold`() {
        val t = template(listOf("اسم الصنف", "الباركود", "الكمية", "الفرع"))
        // 1 of 2 current headers recognized -> ratio 0.5, below AUTO_OFFER_THRESHOLD (0.7).
        val ratio = t.structureMatchRatio(listOf("اسم الصنف", "عمود جديد تمامًا"))
        assertEquals(0.5, ratio, 0.0001)
        assertTrue("0.5 must fall below the auto-offer threshold", ratio < ImportMappingTemplate.AUTO_OFFER_THRESHOLD)
    }

    @Test
    fun `fingerprint normalizes headers so spelling variants still count as the same column`() {
        val t = template(listOf("حليب السعوديه")) // ha-form, as typed by one user
        val ratio = t.structureMatchRatio(listOf("حليب السعودية")) // ta-marbuta form, typed by another
        assertEquals(1.0, ratio, 0.0001)
    }

    @Test
    fun `an empty current header list never matches anything`() {
        val t = template(listOf("اسم الصنف"))
        assertEquals(0.0, t.structureMatchRatio(emptyList()), 0.0001)
    }
}
