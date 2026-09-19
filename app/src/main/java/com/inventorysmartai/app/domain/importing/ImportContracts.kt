package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.model.ImportErrorCode
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.model.ImportSourceType
import java.io.InputStream

/**
 * Contracts for the bulk-import pipeline:
 *
 *   File -> FileDetector -> Parser -> RawRows -> HeaderDetection -> ColumnMapping ->
 *   Normalization -> Validation -> ProductMatching -> DuplicateDetection -> Review ->
 *   Approval -> Database
 *
 * Phase 1 shipped these as interfaces only. Phase 3 implements every step for EXCEL and CSV
 * (see [ImportType] for which of the five row-oriented import types are fully wired) — PDF/
 * IMAGE/CAMERA/BARCODE stay placeholders (no OCR/AI parsing here, by design; see the phase spec).
 * (Package is named "importing", not "import" — the latter is a reserved Kotlin keyword.)
 */

/** One raw row exactly as read from the file: one string per column, in column order, before
 *  any header detection or mapping. Blank/missing cells are empty strings, never null, so a
 *  merged-cell artifact or a short trailing row never crashes downstream indexing. */
typealias RawRow = List<String>

/** One sheet's (or a CSV file's single implicit "sheet") worth of raw rows, header row included —
 *  header detection happens after parsing, not during it, since a real file can have leading
 *  junk rows before the actual header (spec section 6/21). */
data class RawTable(val sheetName: String?, val rows: List<RawRow>)

/** A file opened for reading, with just enough metadata for the file-picker step (name/size/
 *  type/date — the file-picker UI adds "date selected" itself, at pick time) and a way to read
 *  its bytes as many times as the pipeline needs (sheet listing, then parsing, are two passes). */
interface OpenedFile {
    val displayName: String
    val sizeBytes: Long
    val mimeType: String?
    fun inputStream(): InputStream
}

/** Step 1 (File): opens whatever the file-picker returned into an [OpenedFile]. The Android-
 *  specific implementation (wrapping ContentResolver/SAF) lives in the data layer so this
 *  interface itself has no android.* dependency and is trivially fakeable in tests. */
interface ImportSource {
    suspend fun open(reference: String): OpenedFile
}

/** Step 2/3 (FileDetector -> Parser -> RawRows): recognizes and parses one file format. Each
 *  format (CSV, Excel) is its own small implementation so "keep these components independent"
 *  holds — CsvImportParser knows nothing about workbooks, ExcelImportParser knows nothing about
 *  delimiter/BOM detection. */
interface ImportParser {
    val sourceType: ImportSourceType

    /** True if this parser recognizes the file's actual content (not just its extension — see
     *  FileDetector, which sniffs magic bytes first). */
    fun supports(file: OpenedFile): Boolean

    /** The sheets available to import. A single-sheet CSV/workbook returns one entry; its name
     *  may be null (CSV has no sheet concept at all). Never empty for a file [supports] accepted;
     *  an empty/unreadable file is a [FileDetector] result, not something reaching this far. */
    suspend fun listSheets(file: OpenedFile): List<String?>

    /** Parses exactly one sheet (pass the value from [listSheets]) into a [RawTable]. */
    suspend fun parseSheet(file: OpenedFile, sheetName: String?): RawTable
}

/** Steps 4-5 (HeaderDetection -> ColumnMapping) live in [HeaderRowDetector] and [ColumnMapper]. */

/** Step 6 (Normalization) lives in [Normalizer]. */

/** Step 7 (Validation): checks a fully mapped+normalized row against its [ImportType]'s
 *  requirements before it is allowed anywhere near ProductMatching. */
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
 * confirmed by a human via [ImportReviewManager].
 */
interface ProductMatcher {
    suspend fun match(row: ParsedImportRow): MatchResult
}

/** Step 9 (Duplicate Detection) lives in [DuplicateDetector]. */

/** Step 10 (Review): the human-in-the-loop step. Nothing here writes to production tables
 *  directly — see [com.inventorysmartai.app.domain.repository.ImportRepository.approveJob] for
 *  the one place Approval actually happens. */
interface ImportReviewManager {
    fun observeRows(importJobId: Long): kotlinx.coroutines.flow.Flow<List<ImportRow>>
    suspend fun applyDecision(rowId: Long, decision: RowDecision)
    suspend fun applyBulkDecision(importJobId: Long, rowIds: List<Long>, decision: RowDecision)

    /** A human editing a row's mapped field values before approval (spec section 14). [edits]
     *  keys are [ImportField] names; a null value clears that field. Confined to the pending
     *  import row until approval, per the spec — it never touches production tables. */
    suspend fun updateRowFields(rowId: Long, edits: Map<ImportField, String?>)
}

/** A human decision on one review row (spec section 13's row actions, minus "Edit" which is
 *  [ImportReviewManager.updateRowFields] since it carries a payload of field edits rather than
 *  being a single fire-and-forget choice). */
sealed interface RowDecision {
    data object Accept : RowDecision
    data object Reject : RowDecision
    data object Ignore : RowDecision
    /** Overrides ProductMatcher's suggestion with a specific product (or null to instead treat
     *  the row as a brand-new product, same effect as [CreateNewProduct]). */
    data class ChangeMatch(val productId: Long?) : RowDecision
    data object CreateNewProduct : RowDecision
}

/**
 * One row's fully mapped+normalized state, threaded through Validation -> ProductMatching ->
 * DuplicateDetection. [itemNumber]/[barcode] are the NORMALIZED identifiers (digit-script
 * unified, whitespace-stripped — see Normalizer.normalizeIdentifier) since those are what
 * [ProductMatcher] and [DuplicateDetector] compare for exact identity; [name] is the RAW name
 * text, because [com.inventorysmartai.app.data.importing.DeterministicProductMatcher] already
 * normalizes it itself when comparing.
 *
 * [quantity]/[rawJson] are the original Phase 2 fields, kept for source-compatibility with
 * DeterministicProductMatcherTest; [quantity] is populated by the pipeline with whichever
 * quantity concept [importType] actually cares about (CURRENT_STOCK for INVENTORY,
 * COUNTED_QUANTITY for COUNTING, REQUESTED_QUANTITY for PURCHASE_REQUESTS, TARGET for GOALS),
 * so old code reading `.quantity` for "how many" still gets a sensible number. New code should
 * read [fields] by canonical [ImportField] instead of guessing which legacy property applies.
 */
data class ParsedImportRow(
    val importJobId: Long,
    val rowIndex: Int,
    val itemNumber: String?,
    val barcode: String?,
    val name: String?,
    val quantity: Double?,
    val rawJson: String,
    val importType: ImportType = ImportType.PRODUCTS,
    val fields: Map<ImportField, NormalizedValue> = emptyMap()
) {
    fun rawValue(field: ImportField): String? = fields[field]?.raw
    fun normalizedValue(field: ImportField): String? = fields[field]?.normalized
    fun numericValue(field: ImportField): Double? = fields[field]?.numeric
    fun hasRawValue(field: ImportField): Boolean = !fields[field]?.raw.isNullOrBlank()
    fun hasIdentification(): Boolean = !itemNumber.isNullOrBlank() || !barcode.isNullOrBlank() || !name.isNullOrBlank()
}

sealed interface MatchResult {
    data class ExactBarcode(val productId: Long) : MatchResult
    data class ExactItemNumber(val productId: Long) : MatchResult
    data class ExactName(val productId: Long) : MatchResult
    data class FuzzySuggestion(val productId: Long, val confidence: Double) : MatchResult
    data object NewProduct : MatchResult
    data object Unresolved : MatchResult
}

data class RowIssue(val code: ImportErrorCode, val message: String, val field: ImportField? = null)

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<RowIssue> = emptyList(),
    val warnings: List<RowIssue> = emptyList()
)
