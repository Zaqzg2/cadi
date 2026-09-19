package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.model.ImportRowStatus
import javax.inject.Inject
import javax.inject.Singleton

/** One row's fully analyzed outcome — everything the Review screen (spec section 13) needs to
 *  show for a single row, and everything Approval needs to decide what to write. */
data class RowAnalysis(
    val row: ParsedImportRow,
    val validation: ValidationResult,
    val matchResult: MatchResult,
    val isDuplicate: Boolean,
    val duplicateOfRowIndex: Int? = null
) {
    /** The one [ImportRowStatus] this analysis resolves to. Precedence matches the spec's own
     *  ordering of concerns: a hard validation error always wins — a row is never MATCHED or
     *  DUPLICATE if it is also invalid — then duplicate-in-file, then whatever ProductMatcher
     *  decided. Exhaustive over [MatchResult]'s subtypes on purpose (no `else`), so a future new
     *  MatchResult variant fails to compile here instead of silently mapping to the wrong status. */
    fun resolvedStatus(): ImportRowStatus = when {
        !validation.isValid -> ImportRowStatus.ERROR
        isDuplicate -> ImportRowStatus.DUPLICATE
        else -> when (matchResult) {
            is MatchResult.ExactBarcode, is MatchResult.ExactItemNumber, is MatchResult.ExactName -> ImportRowStatus.MATCHED
            is MatchResult.FuzzySuggestion -> ImportRowStatus.AMBIGUOUS
            is MatchResult.NewProduct -> ImportRowStatus.NEW_PRODUCT
            is MatchResult.Unresolved -> ImportRowStatus.PENDING
        }
    }

    val matchedProductId: Long?
        get() = when (val m = matchResult) {
            is MatchResult.ExactBarcode -> m.productId
            is MatchResult.ExactItemNumber -> m.productId
            is MatchResult.ExactName -> m.productId
            else -> null
        }

    val suggestedProductId: Long?
        get() = (matchResult as? MatchResult.FuzzySuggestion)?.productId

    val confidence: Double?
        get() = (matchResult as? MatchResult.FuzzySuggestion)?.confidence
}

data class PipelineAnalysisResult(
    val importType: ImportType,
    val headerRowIndex: Int?,
    val headers: List<String>,
    val columnMapping: ColumnMappingResult,
    val analyses: List<RowAnalysis>,
    val skippedBlankRows: Int
) {
    val totalRows: Int get() = analyses.size
    val acceptableRows: Int get() = analyses.count { it.validation.isValid }
    val errorRows: Int get() = analyses.count { !it.validation.isValid }
}

/** Runs steps 4 (HeaderDetection) through 9 (DuplicateDetection) of the pipeline over one parsed
 *  [RawTable] in a single pass. Steps 1-3 (File -> FileDetector -> Parser -> RawRows) already
 *  happened to produce the [RawTable]; steps 10-12 (Review -> Approval -> Database) are
 *  [ImportReviewManager] and [com.inventorysmartai.app.domain.repository.ImportRepository].
 *
 *  [onProgress] (spec section 23: "Show progress, rows processed, current step") is invoked
 *  after each row's ProductMatching/DuplicateDetection resolves — the DB-hitting step, and so
 *  the one worth reporting granularly on a large file — with (rowsProcessed, totalRows). It is a
 *  plain callback rather than this function returning a Flow so the pure analysis logic stays
 *  trivially unit-testable with a no-op lambda; the caller (ImportEngine) is what turns this into
 *  a Flow<ImportProgress> for the UI. */
interface ImportPipeline {
    suspend fun analyze(
        table: RawTable,
        importType: ImportType,
        importJobId: Long,
        columnMappingOverride: ColumnMappingResult? = null,
        onProgress: suspend (processed: Int, total: Int) -> Unit = {}
    ): PipelineAnalysisResult
}

@Singleton
class DefaultImportPipeline @Inject constructor(
    private val columnMapper: ColumnMapper,
    private val normalizer: Normalizer,
    private val validator: ImportValidator,
    private val productMatcher: ProductMatcher,
    private val duplicateDetector: DuplicateDetector
) : ImportPipeline {

    override suspend fun analyze(
        table: RawTable,
        importType: ImportType,
        importJobId: Long,
        columnMappingOverride: ColumnMappingResult?,
        onProgress: suspend (processed: Int, total: Int) -> Unit
    ): PipelineAnalysisResult {
        val headerResult = HeaderRowDetector.detect(table.rows)
        val headers = headerResult?.headers.orEmpty()
        val dataRows = headerResult?.dataRows.orEmpty()

        val mapping = columnMappingOverride ?: columnMapper.suggestMapping(headers, importType)

        val parsedRows = mutableListOf<ParsedImportRow>()
        var skippedBlank = 0
        dataRows.forEachIndexed { index, raw ->
            if (raw.all { it.isBlank() }) {
                skippedBlank++
            } else {
                parsedRows += buildParsedRow(importJobId, index, headers, raw, mapping, importType)
            }
        }

        val duplicateGroups = duplicateDetector.findInFileDuplicates(parsedRows)
        val duplicateOfByIndex = buildMap {
            duplicateGroups.forEach { group ->
                group.duplicateRowIndexes.forEach { dupPos -> put(dupPos, group.rowIndexes.first()) }
            }
        }

        val total = parsedRows.size
        val analyses = parsedRows.mapIndexed { position, row ->
            val validation = validator.validate(row)
            val isDuplicate = duplicateOfByIndex.containsKey(position)
            // A row that already failed validation, or is a same-file duplicate, is never sent
            // to ProductMatcher — there is nothing useful to resolve it against yet, and it
            // avoids spending a DB lookup on a row that cannot be accepted as-is anyway.
            val match = if (validation.isValid && !isDuplicate) productMatcher.match(row) else MatchResult.Unresolved
            onProgress(position + 1, total)
            RowAnalysis(
                row = row,
                validation = validation,
                matchResult = match,
                isDuplicate = isDuplicate,
                duplicateOfRowIndex = duplicateOfByIndex[position]
            )
        }

        return PipelineAnalysisResult(
            importType = importType,
            headerRowIndex = headerResult?.headerRowIndex,
            headers = headers,
            columnMapping = mapping,
            analyses = analyses,
            skippedBlankRows = skippedBlank
        )
    }

    /** Turns one raw data row into a [ParsedImportRow]: keeps the ORIGINAL header->value map
     *  verbatim in [ParsedImportRow.rawJson] (never mutated, per spec rule 9), and a
     *  canonical-field->[NormalizedValue] map in [ParsedImportRow.fields] for everything
     *  downstream (validation, matching, review display, approval) to read. */
    private fun buildParsedRow(
        importJobId: Long,
        rowIndex: Int,
        headers: List<String>,
        raw: RawRow,
        mapping: ColumnMappingResult,
        importType: ImportType
    ): ParsedImportRow {
        val rawByHeader = LinkedHashMap<String, String?>()
        val fields = mutableMapOf<ImportField, NormalizedValue>()

        raw.forEachIndexed { colIndex, cellValue ->
            val header = headers.getOrElse(colIndex) { "العمود ${colIndex + 1}" }
            rawByHeader[header] = cellValue

            val field = mapping.fieldFor(colIndex)
            if (field == null || field == ImportField.IGNORE) return@forEachIndexed

            val normalized = normalizer.normalize(field, cellValue)
            // If a field is (incorrectly) mapped from more than one column, the first non-blank
            // value wins rather than a later blank column silently erasing a good one.
            if (fields[field]?.raw.isNullOrBlank()) fields[field] = normalized
        }

        val quantity = when (importType) {
            ImportType.INVENTORY -> fields[ImportField.CURRENT_STOCK]?.numeric
            ImportType.COUNTING -> fields[ImportField.COUNTED_QUANTITY]?.numeric
            ImportType.PURCHASE_REQUESTS -> fields[ImportField.REQUESTED_QUANTITY]?.numeric
            ImportType.GOALS -> fields[ImportField.TARGET]?.numeric
            ImportType.PRODUCTS, ImportType.SALES_INVOICES -> fields[ImportField.QUANTITY]?.numeric
        }

        return ParsedImportRow(
            importJobId = importJobId,
            rowIndex = rowIndex,
            itemNumber = fields[ImportField.ITEM_NUMBER]?.normalized,
            barcode = fields[ImportField.BARCODE]?.normalized,
            name = fields[ImportField.PRODUCT_NAME]?.raw,
            quantity = quantity,
            rawJson = SimpleJson.encodeMap(rawByHeader),
            importType = importType,
            fields = fields
        )
    }
}
