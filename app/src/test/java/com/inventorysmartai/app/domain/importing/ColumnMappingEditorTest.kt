package com.inventorysmartai.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnMappingEditorTest {
    private val base = ColumnMappingResult(
        listOf(
            ColumnMapping(0, "الاسم", ImportField.PRODUCT_NAME),
            ColumnMapping(1, "الكمية", ImportField.CURRENT_STOCK),
            ColumnMapping(2, "ملاحظة", null)
        )
    )

    @Test fun addConstantColumnGetsVirtualIndexAndValue() {
        val r = ColumnMappingEditor.addConstantColumn(base, " الفرع ", ImportField.BRANCH, " الرياض ")
        val added = r.mappings.last()
        assertTrue(added.isVirtual)
        assertEquals("الرياض", added.constantValue)
        assertEquals("الفرع", added.header)
        assertEquals(ImportField.BRANCH, added.field)
        assertTrue(added.columnIndex >= ColumnMappingEditor.VIRTUAL_INDEX_BASE)
    }

    @Test fun twoAddedColumnsGetDifferentIndexes() {
        val r1 = ColumnMappingEditor.addConstantColumn(base, "أ", ImportField.BRANCH, "1")
        val r2 = ColumnMappingEditor.addConstantColumn(r1, "ب", ImportField.UNIT, "2")
        assertEquals(2, r2.mappings.filter { it.isVirtual }.map { it.columnIndex }.distinct().size)
    }

    @Test fun renameFileColumnKeepsRealHeader() {
        val r = ColumnMappingEditor.rename(base, 0, "اسم المنتج")
        val c = r.mappings.first { it.columnIndex == 0 }
        assertEquals("الاسم", c.header)
        assertEquals("اسم المنتج", c.displayName)
    }

    @Test fun removeFileColumnExcludesItAndRestoreBringsItBack() {
        val removed = ColumnMappingEditor.remove(base, 1)
        assertEquals(ImportField.IGNORE, removed.fieldFor(1))
        assertEquals(3, removed.mappings.size) // still listed, so it can be restored
        val restored = ColumnMappingEditor.restore(removed, 1)
        assertNull(restored.fieldFor(1))
    }

    @Test fun removeAddedColumnDeletesIt() {
        val added = ColumnMappingEditor.addConstantColumn(base, "الفرع", ImportField.BRANCH, "الرياض")
        val idx = added.mappings.last().columnIndex
        val r = ColumnMappingEditor.remove(added, idx)
        assertEquals(3, r.mappings.size)
        assertFalse(r.mappings.any { it.isVirtual })
    }

    @Test fun missingRequiredFieldIsReported() {
        val noQuantity = ColumnMappingEditor.remove(base, 1)
        assertEquals(listOf(ImportField.CURRENT_STOCK), ColumnMappingEditor.missingRequiredFields(noQuantity, ImportType.INVENTORY))
        assertTrue(ColumnMappingEditor.missingRequiredFields(base, ImportType.INVENTORY).isEmpty())
    }

    @Test fun addedConstantSatisfiesRequiredField() {
        val noQuantity = ColumnMappingEditor.remove(base, 1)
        val r = ColumnMappingEditor.addConstantColumn(noQuantity, "كمية", ImportField.CURRENT_STOCK, "1")
        assertTrue(ColumnMappingEditor.missingRequiredFields(r, ImportType.INVENTORY).isEmpty())
    }
}
