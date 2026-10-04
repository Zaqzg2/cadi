package com.inventorysmartai.app.domain.importing

/**
 * Pure edit operations behind the mapping screen's add / rename / delete / restore column buttons
 * (kept free of Android and the ViewModel so they are unit-tested).
 */
object ColumnMappingEditor {
    /** Added columns get indexes far above any real column so they can never collide with the file's. */
    const val VIRTUAL_INDEX_BASE = 10_000

    /** Adds a column that gives every row the constant [value] for [field]. Returns the new result. */
    fun addConstantColumn(result: ColumnMappingResult, label: String, field: ImportField, value: String): ColumnMappingResult {
        val nextIndex = (result.mappings.filter { it.isVirtual }.maxOfOrNull { it.columnIndex } ?: (VIRTUAL_INDEX_BASE - 1)) + 1
        val column = ColumnMapping(
            columnIndex = nextIndex,
            header = label.trim(),
            field = field,
            isUserOverride = true,
            constantValue = value.trim()
        )
        return ColumnMappingResult(result.mappings + column)
    }

    /** Display name only — the file header (used to match saved templates) is untouched. */
    fun rename(result: ColumnMappingResult, columnIndex: Int, newLabel: String): ColumnMappingResult =
        update(result, columnIndex) {
            if (it.isVirtual) it.copy(header = newLabel.trim(), label = null) else it.copy(label = newLabel.trim().ifBlank { null })
        }

    fun setConstant(result: ColumnMappingResult, columnIndex: Int, value: String): ColumnMappingResult =
        update(result, columnIndex) { if (it.isVirtual) it.copy(constantValue = value.trim()) else it }

    /** Deleting an added column removes it; deleting a file column excludes it from the import (reversible). */
    fun remove(result: ColumnMappingResult, columnIndex: Int): ColumnMappingResult {
        val target = result.mappings.firstOrNull { it.columnIndex == columnIndex } ?: return result
        return if (target.isVirtual) {
            ColumnMappingResult(result.mappings.filterNot { it.columnIndex == columnIndex })
        } else {
            update(result, columnIndex) { it.copy(field = ImportField.IGNORE, requiresConfirmation = false, isUserOverride = true) }
        }
    }

    /** Brings an excluded file column back, unmapped, for the person to assign. */
    fun restore(result: ColumnMappingResult, columnIndex: Int): ColumnMappingResult =
        update(result, columnIndex) { it.copy(field = null, requiresConfirmation = false, isUserOverride = true) }

    /** Required fields of [importType] that no included column feeds (so every row would fail validation). */
    fun missingRequiredFields(result: ColumnMappingResult, importType: ImportType): List<ImportField> {
        val fed = result.mappings.mapNotNull { it.field }.toSet()
        return importType.requiredFields.filter { it !in fed }
    }

    private fun update(result: ColumnMappingResult, columnIndex: Int, change: (ColumnMapping) -> ColumnMapping) =
        ColumnMappingResult(result.mappings.map { if (it.columnIndex == columnIndex) change(it) else it })
}
