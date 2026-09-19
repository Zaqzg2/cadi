package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/** One duplicate group: every row index sharing the same identity [key]. [duplicateRowIndexes]
 *  (everything after the first occurrence) is what actually gets flagged DUPLICATE — the first
 *  occurrence of a repeated identity is the "original" and is free to proceed to ProductMatching
 *  like any other row. */
data class DuplicateGroup(val key: String, val rowIndexes: List<Int>) {
    val duplicateRowIndexes: List<Int> get() = rowIndexes.drop(1)
}

/**
 * Step 11.A/11.B (spec section 11): "duplicate rows inside the same file" and "repeated products
 * inside the same import" are the same question — two rows in this one file resolving to the
 * same product identity — so both are this single detector. Section 11.C ("existing records
 * already present in the database") is deliberately NOT this detector's job: that is exactly
 * what [ProductMatcher] returning MATCHED already answers, kept as a separate concern so a row
 * is never double-counted as both "duplicate" and "matched existing product" (mirroring the
 * spec's "do not count matched existing products as new products" rule for this pair too).
 */
interface DuplicateDetector {
    fun findInFileDuplicates(rows: List<ParsedImportRow>): List<DuplicateGroup>
}

@Singleton
class DefaultDuplicateDetector @Inject constructor() : DuplicateDetector {

    override fun findInFileDuplicates(rows: List<ParsedImportRow>): List<DuplicateGroup> {
        val byKey = LinkedHashMap<String, MutableList<Int>>()
        rows.forEachIndexed { index, row ->
            val key = identityKey(row) ?: return@forEachIndexed
            byKey.getOrPut(key) { mutableListOf() }.add(index)
        }
        return byKey.filterValues { it.size > 1 }.map { (key, indexes) -> DuplicateGroup(key, indexes) }
    }

    /** Same precedence as [com.inventorysmartai.app.data.importing.DeterministicProductMatcher]
     *  (barcode > item number > normalized name) so "this row is a duplicate of that row" and
     *  "these two rows would match the same product" never disagree. A row with none of the
     *  three has no identity key and is simply never flagged as a duplicate by this detector —
     *  it will already fail validation separately for lacking product identification. */
    private fun identityKey(row: ParsedImportRow): String? {
        row.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let { return "barcode:$it" }
        row.itemNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { return "item:$it" }
        row.name?.let { name ->
            val normalized = ArabicTextNormalizer.normalize(name)
            if (normalized.isNotEmpty()) return "name:$normalized"
        }
        return null
    }
}
