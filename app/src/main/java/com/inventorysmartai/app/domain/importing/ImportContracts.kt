package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.model.ImportSourceType

/**
 * Contracts for the future bulk-import pipeline (Excel / CSV / PDF / Image / Camera).
 * Phase 1 ships these as interfaces only — no OCR, no AI parsing, no implementations.
 * (Package is named "importing", not "import" — the latter is a reserved Kotlin keyword.)
 */

/** Describes where import rows physically come from. */
interface ImportSource {
    val sourceType: ImportSourceType
    suspend fun readRawRows(uriOrPath: String): List<String>
}

/** Turns one raw row (an Excel row, a CSV line, an OCR block) into a structured candidate. */
interface ImportParser {
    suspend fun parse(rawRow: String): ParsedImportRow
}

/** Validates a parsed row before it is allowed anywhere near production tables. */
interface ImportValidator {
    fun validate(row: ParsedImportRow): ValidationResult
}

/**
 * Resolves a parsed row to an existing product using the required precedence:
 *   1. Exact barcode
 *   2. Exact item number
 *   3. Exact normalized name
 *   4. Fuzzy name suggestion only
 *   5. New product if no valid match
 * Matching must NEVER silently merge an uncertain match — anything below "exact" has to be
 * confirmed by a human via [ImportReview].
 */
interface ProductMatcher {
    suspend fun match(row: ParsedImportRow): MatchResult
}

/** The human-in-the-loop review step. Nothing here writes to production tables directly. */
interface ImportReview {
    suspend fun rowsPendingReview(importJobId: Long): List<ParsedImportRow>
    suspend fun confirmRow(rowId: Long, resolution: MatchResult)
    suspend fun rejectRow(rowId: Long)
}

data class ParsedImportRow(
    val importJobId: Long,
    val rowIndex: Int,
    val itemNumber: String?,
    val barcode: String?,
    val name: String?,
    val quantity: Double?,
    val rawJson: String
)

sealed interface MatchResult {
    data class ExactBarcode(val productId: Long) : MatchResult
    data class ExactItemNumber(val productId: Long) : MatchResult
    data class ExactName(val productId: Long) : MatchResult
    data class FuzzySuggestion(val productId: Long, val confidence: Double) : MatchResult
    data object NewProduct : MatchResult
    data object Unresolved : MatchResult
}

data class ValidationResult(val isValid: Boolean, val errors: List<String> = emptyList())
