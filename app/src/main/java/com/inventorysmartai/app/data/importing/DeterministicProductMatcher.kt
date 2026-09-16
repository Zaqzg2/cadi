package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.domain.importing.MatchResult
import com.inventorysmartai.app.domain.importing.ParsedImportRow
import com.inventorysmartai.app.domain.importing.ProductMatcher
import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2's ProductMatcher: only the three deterministic strategies the spec allows right now.
 * Tried in the required precedence — barcode, then item number, then normalized name — and stops
 * at the first hit. Never returns [MatchResult.FuzzySuggestion]; that stays unimplemented until a
 * later phase, and even then must remain suggestion-only per the contract's own doc comment.
 *
 * A row with a name but no deterministic hit comes back as [MatchResult.NewProduct] rather than
 * [MatchResult.Unresolved] — it's a specific, actionable outcome ("create this product") that the
 * human review step (ImportReview) can act on directly. [MatchResult.Unresolved] is reserved for
 * rows with nothing usable to match or name a product by at all.
 */
@Singleton
class DeterministicProductMatcher @Inject constructor(
    private val productDao: ProductDao
) : ProductMatcher {

    override suspend fun match(row: ParsedImportRow): MatchResult {
        row.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
            productDao.getByBarcode(barcode)?.let { return MatchResult.ExactBarcode(it.id) }
        }

        row.itemNumber?.takeIf { it.isNotBlank() }?.let { itemNumber ->
            productDao.getByItemNumber(itemNumber)?.let { return MatchResult.ExactItemNumber(it.id) }
        }

        row.name?.takeIf { it.isNotBlank() }?.let { name ->
            val normalized = ArabicTextNormalizer.normalize(name)
            if (normalized.isNotEmpty()) {
                productDao.getByNormalizedName(normalized)?.let { return MatchResult.ExactName(it.id) }
            }
        }

        return if (row.name.isNullOrBlank()) MatchResult.Unresolved else MatchResult.NewProduct
    }
}
