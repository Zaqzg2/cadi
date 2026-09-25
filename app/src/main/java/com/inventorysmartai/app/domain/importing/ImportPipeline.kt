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
/**
 * Which canonical field represents "the quantity" depends on what kind of import this is — a
 * purchase request's meaningful quantity is what was *requested*, a count's is what was *counted*,
 * etc. Shared by [DefaultImportPipeline.buildParsedRow] (tabular sources) and
 * `domain.importing.ai.AiExtractionMapper` (AI-extracted sources, Phase 4) so both derive
 * [ParsedImportRow.quantity] identically — if this rule ever changes, both sources of rows change
 * with it, rather than risking two copies quietly drifting apart.
 */
internal fun quantityFieldFor(importType: ImportType, fields: Map<ImportField, NormalizedValue>): Double? = when (importType) {
    ImportType.INVENTORY -> fields[ImportField.CURRENT_STOCK]?.numeric
    ImportType.COUNTING -> fields[ImportField.COUNTED_QUANTITY]?.numeric
    ImportType.PURCHASE_REQUESTS -> fields[ImportField.REQUESTED_QUANTITY]?.numeric
    ImportType.GOALS -> fields[ImportField.TARGET]?.numeric
    ImportType.PRODUCTS, ImportType.SALES_INVOICES -> fields[ImportField.QUANTITY]?.numeric
}

interface ImportPipeline {
    suspend fun analyze(
        table: RawTable,
        importType: ImportType,
        importJobId: Long,
        columnMappingOverride: ColumnMappingResult? = null,
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): PipelineAnalysisResult

    /**
     * Phase 4: the AI-extraction entry point. A photographed/PDF document has no literal
     * spreadsheet columns to detect a header row in or map — Gemini already returned field-level
     * values directly (see the app's `domain/importing/ai/AiExtractionMapper`, which turns the
     * backend's structured JSON into [rows] here, running each field's raw text through the exact
     * same [Normalizer] a spreadsheet cell would go through). So this method starts one stage
     * later than [analyze]: no [HeaderRowDetector], no [ColumnMapper] — but every stage after that
     * (duplicate detection, deterministic validation, [ProductMatcher]) is IDENTICAL code to
     * [analyze], via the shared private `runAnalyses` below, so an AI-sourced row and a
     * spreadsheet-sourced row are validated and matched by exactly one code path, never two that
     * could quietly disagree.
     */
    suspend fun analyzeRows(
        rows: List<ParsedImportRow>,
        importType: ImportType,
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> }
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

        val analyses = runAnalyses(parsedRows, duplicateOfByIndex, onProgress)

        return PipelineAnalysisResult(
            importType = importType,
            headerRowIndex = headerResult?.headerRowIndex,
            headers = headers,
            columnMapping = mapping,
            analyses = analyses,
            skippedBlankRows = skippedBlank
        )
    }

    override suspend fun analyzeRows(
        rows: List<ParsedImportRow>,
        importType: ImportType,
        onProgress: suspend (processed: Int, total: Int) -> Unit
    ): PipelineAnalysisResult {
        val duplicateGroups = duplicateDetector.findInFileDuplicates(rows)
        val duplicateOfByIndex = buildMap {
            duplicateGroups.forEach { group ->
                group.duplicateRowIndexes.forEach { dupPos -> put(dupPos, group.rowIndexes.first()) }
            }
        }

        val analyses = runAnalyses(rows, duplicateOfByIndex, onProgress)

        // A synthetic, identity "column mapping" — one entry per field actually present across
        // the extracted rows — purely so the review screen's "which column fed this field" UI has
        // something coherent to show even though there was no literal spreadsheet column. See
        // this interface method's doc comment for why there is no real header row / column
        // mapping to detect for an AI-sourced document.
        val fieldsPresent = rows.flatMap { it.fields.keys }.distinct()
        val syntheticMapping = ColumnMappingResult(
            fieldsPresent.mapIndexed { index, field -> ColumnMapping(columnIndex = index, header = field.labelAr, field = field) }
        )

        return PipelineAnalysisResult(
            importType = importType,
            headerRowIndex = null,
            headers = fieldsPresent.map { it.labelAr },
            columnMapping = syntheticMapping,
            analyses = analyses,
            skippedBlankRows = 0
        )
    }

    /** Shared by [analyze] and [analyzeRows]: validate -> (skip matching if invalid/duplicate) ->
     *  match -> report progress, for one already-parsed row list. This is deliberately the ONLY
     *  place either public method calls [validator]/[productMatcher], so a spreadsheet row and an
     *  AI-extracted row are judged by identical rules. */
    private suspend fun runAnalyses(
        rows: List<ParsedImportRow>,
        duplicateOfByIndex: Map<Int, Int>,
        onProgress: suspend (processed: Int, total: Int) -> Unit
    ): List<RowAnalysis> {
        val total = rows.size
        return rows.mapIndexed { position, row ->
            val validation = validator.validate(row)
            val isDuplicate = duplicateOfByIndex.containsKey(position)
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

        val quantity = quantityFieldFor(importType, fields)

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
