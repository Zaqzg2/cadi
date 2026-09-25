package com.inventorysmartai.app.domain.importing.ai

import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.Normalizer
import com.inventorysmartai.app.domain.importing.NormalizedValue
import com.inventorysmartai.app.domain.importing.ParsedImportRow
import com.inventorysmartai.app.domain.importing.SimpleJson
import com.inventorysmartai.app.domain.importing.quantityFieldFor
import javax.inject.Inject
import javax.inject.Singleton

data class AiMappingResult(
    val rows: List<ParsedImportRow>,
    /** Document-header fields (an invoice's number/customer/..., a purchase request's requester/
     *  date), destined for [com.inventorysmartai.app.domain.model.ImportJob.metadataJson] — null
     *  when [AiExtractionDocument.header] was empty (PRODUCTS/INVENTORY/COUNTING/GOALS never have
     *  header-level facts). */
    val metadataJson: String?
)

/**
 * The one bridge between Phase 4's AI extraction and the Phase 2/3 deterministic pipeline —
 * everything downstream of [toParsedRows] (validation, product matching, duplicate detection,
 * review, approval) is the exact same code a spreadsheet import uses (see domain/importing/
 * ImportPipeline.kt's `analyzeRows`). This class's only job is turning Gemini's field-name/raw-
 * text pairs into the pipeline's own [ParsedImportRow] shape:
 *  - each field's raw text goes through [Normalizer.normalize] — identical to what a spreadsheet
 *    cell's text would go through, so normalization is never a second, AI-specific implementation
 *    that could quietly disagree with the tabular one;
 *  - a field Gemini marked `uncertain`, or a warning Gemini attached to a row, becomes a
 *    [ParsedImportRow.sourceWarnings] entry — the review screen's existing warnings display
 *    handles the rest, no new UI concept needed;
 *  - [ImportField] values not present in a particular schema (e.g. `TARGET_GROUP` only appears
 *    for GOALS) simply never show up in a PRODUCTS row's `fields` map, so no per-type branching
 *    is needed here at all — whatever field names the backend's schema for this [ImportType]
 *    declared are exactly the ones that can appear.
 */
@Singleton
class AiExtractionMapper @Inject constructor(
    private val normalizer: Normalizer
) {
    fun toParsedRows(document: AiExtractionDocument, importType: ImportType, importJobId: Long): AiMappingResult {
        val rows = document.rows.mapIndexed { index, aiRow ->
            val fields = mutableMapOf<ImportField, NormalizedValue>()
            val rawByLabel = LinkedHashMap<String, String?>()
            val sourceWarnings = mutableListOf<String>()

            aiRow.fields.forEach { (fieldName, value) ->
                val field = runCatching { ImportField.valueOf(fieldName) }.getOrNull()
                if (field == null || field == ImportField.IGNORE) return@forEach
                fields[field] = normalizer.normalize(field, value.raw)
                rawByLabel[field.labelAr] = value.raw
                if (value.uncertain) {
                    sourceWarnings += "قيمة \"${field.labelAr}\" غير واضحة في المصدر الأصلي (الصفحة ${aiRow.sourcePage ?: "؟"}) — يرجى التحقق"
                }
            }
            sourceWarnings += aiRow.rowWarnings

            ParsedImportRow(
                importJobId = importJobId,
                rowIndex = index,
                itemNumber = fields[ImportField.ITEM_NUMBER]?.normalized,
                barcode = fields[ImportField.BARCODE]?.normalized,
                name = fields[ImportField.PRODUCT_NAME]?.raw,
                quantity = quantityFieldFor(importType, fields),
                rawJson = SimpleJson.encodeMap(rawByLabel),
                importType = importType,
                fields = fields,
                sourceWarnings = sourceWarnings
            )
        }

        val metadataJson = document.header.takeIf { it.isNotEmpty() }
            ?.mapValues { (_, value) -> value.raw.orEmpty() }
            ?.let { SimpleJson.encodeMap(it) }

        return AiMappingResult(rows, metadataJson)
    }
}
