package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.local.database.dao.ImportDao
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportReviewManager
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.ImportValidator
import com.inventorysmartai.app.domain.importing.NormalizedValue
import com.inventorysmartai.app.domain.importing.Normalizer
import com.inventorysmartai.app.domain.importing.MatchResult
import com.inventorysmartai.app.domain.importing.ParsedImportRow
import com.inventorysmartai.app.domain.importing.ProductMatcher
import com.inventorysmartai.app.domain.importing.RowDecision
import com.inventorysmartai.app.domain.importing.SimpleJson
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.model.ImportRowStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Step 10 (Review), spec sections 12-15: everything the review screen does to ONE pending row
 * short of the final "اعتماد الاستيراد" (that's [com.inventorysmartai.app.domain.repository.ImportRepository.approveJob])
 * — this class never touches production tables, only the pending import_rows themselves, per the
 * spec's "changes must affect only the pending import row until approval".
 */
@Singleton
class DefaultImportReviewManager @Inject constructor(
    private val importDao: ImportDao,
    private val productMatcher: ProductMatcher,
    private val normalizer: Normalizer,
    private val validator: ImportValidator
) : ImportReviewManager {

    override fun observeRows(importJobId: Long): Flow<List<ImportRow>> =
        importDao.observeRowsForJob(importJobId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun applyDecision(rowId: Long, decision: RowDecision) {
        val entity = importDao.getRowById(rowId) ?: return

        val updated = when (decision) {
            RowDecision.Accept -> {
                // A hard validation error must be fixed (via updateRowFields, which re-validates
                // the identity fields) before a row can be accepted — silently accepting an
                // invalid row would defeat the whole point of Validation being its own step.
                check(entity.errorCode == null) { "لا يمكن قبول صف يحتوي على خطأ يجب إصلاحه أولاً" }
                entity.copy(status = ImportRowStatus.ACCEPTED.name)
            }
            // "Reject" and "Ignore" are presented as two actions in the UI (spec section 13) but
            // have the same effect on the pending row: it is excluded from Approval. Distinct
            // wording, same terminal state — there is no user-visible difference once a job is
            // reported on (spec section 12's summary only ever needed one "not accepted" bucket).
            RowDecision.Reject, RowDecision.Ignore -> entity.copy(status = ImportRowStatus.REJECTED.name)
            RowDecision.CreateNewProduct -> entity.copy(
                status = ImportRowStatus.NEW_PRODUCT.name,
                matchedProductId = null,
                suggestedProductId = null,
                confidence = null
            )
            is RowDecision.ChangeMatch -> if (decision.productId != null) {
                entity.copy(
                    status = ImportRowStatus.MATCHED.name,
                    matchedProductId = decision.productId,
                    suggestedProductId = null,
                    confidence = null
                )
            } else {
                entity.copy(
                    status = ImportRowStatus.NEW_PRODUCT.name,
                    matchedProductId = null,
                    suggestedProductId = null,
                    confidence = null
                )
            }
        }
        importDao.updateRow(updated)
    }

    override suspend fun applyBulkDecision(importJobId: Long, rowIds: List<Long>, decision: RowDecision) {
        // Sequential on purpose (not parallel): Accept can throw for an error row, and the spec's
        // bulk actions (section 15) are explicitly meant to skip/refuse those individually rather
        // than abort the whole batch, so each row's outcome is independent.
        rowIds.forEach { rowId ->
            runCatching { applyDecision(rowId, decision) }
        }
    }

    override suspend fun updateRowFields(rowId: Long, edits: Map<ImportField, String?>) {
        val entity = importDao.getRowById(rowId) ?: return
        val current = SimpleJson.decodeMap(entity.normalizedData).toMutableMap()
        edits.forEach { (field, value) ->
            if (value.isNullOrBlank()) current.remove(field.name) else current[field.name] = value.trim()
        }

        // Re-validate EVERY edit (not only identity fields). Previously a row that failed validation
        // kept its errorCode forever, so "fix it first" (see applyDecision) was impossible.
        val importType = importDao.getJobById(entity.importJobId)?.importType
            ?.let { runCatching { ImportType.valueOf(it) }.getOrNull() } ?: ImportType.PRODUCTS

        val fields = mutableMapOf<ImportField, NormalizedValue>()
        current.entries.toList().forEach { (name, value) ->
            val field = runCatching { ImportField.valueOf(name) }.getOrNull() ?: return@forEach
            val normalized = normalizer.normalize(field, value)
            fields[field] = normalized
            // Store in the SAME form ImportRepositoryImpl.persistAnalysis does (identifiers normalized,
            // numbers as plain numerals, text as typed). Approval parses these strings with
            // toDoubleOrNull(), so an edit typed with Arabic digits ("٥") must be saved as "5".
            current[name] = when {
                field == ImportField.ITEM_NUMBER || field == ImportField.BARCODE -> normalized.normalized ?: value
                field.isNumeric -> normalized.numeric?.toString() ?: value
                else -> value
            }
        }
        val probe = ParsedImportRow(
            importJobId = entity.importJobId,
            rowIndex = entity.rowIndex,
            itemNumber = fields[ImportField.ITEM_NUMBER]?.normalized,
            barcode = fields[ImportField.BARCODE]?.normalized,
            name = fields[ImportField.PRODUCT_NAME]?.raw,
            quantity = null,
            rawJson = entity.rawData,
            importType = importType,
            fields = fields
        )
        val validation = validator.validate(probe)
        val warnings = validation.warnings.map { it.message }
        val firstError = validation.errors.firstOrNull()

        var status = entity.status
        var matchedProductId = entity.matchedProductId
        var suggestedProductId = entity.suggestedProductId
        var confidence = entity.confidence

        if (firstError != null) {
            // Still invalid: stays ERROR (cannot be accepted), message reflects the CURRENT problem.
            status = ImportRowStatus.ERROR.name
            matchedProductId = null; suggestedProductId = null; confidence = null
        } else {
            // Valid now (either it always was, or this edit just fixed it): re-run matching so the
            // edit can fix a bad match too, and so a formerly-ERROR row gets a real status.
            val wasError = entity.errorCode != null
            val identityChanged = edits.keys.any {
                it == ImportField.ITEM_NUMBER || it == ImportField.BARCODE || it == ImportField.PRODUCT_NAME
            }
            if (wasError || identityChanged) {
                when (val match = productMatcher.match(probe)) {
                    is MatchResult.ExactBarcode -> {
                        status = ImportRowStatus.MATCHED.name; matchedProductId = match.productId
                        suggestedProductId = null; confidence = null
                    }
                    is MatchResult.ExactItemNumber -> {
                        status = ImportRowStatus.MATCHED.name; matchedProductId = match.productId
                        suggestedProductId = null; confidence = null
                    }
                    is MatchResult.ExactName -> {
                        status = ImportRowStatus.MATCHED.name; matchedProductId = match.productId
                        suggestedProductId = null; confidence = null
                    }
                    is MatchResult.FuzzySuggestion -> {
                        status = ImportRowStatus.AMBIGUOUS.name; matchedProductId = null
                        suggestedProductId = match.productId; confidence = match.confidence
                    }
                    MatchResult.NewProduct -> {
                        status = ImportRowStatus.NEW_PRODUCT.name
                        matchedProductId = null; suggestedProductId = null; confidence = null
                    }
                    MatchResult.Unresolved -> {
                        status = ImportRowStatus.PENDING.name
                        matchedProductId = null; suggestedProductId = null; confidence = null
                    }
                }
            }
        }

        importDao.updateRow(
            entity.copy(
                normalizedData = SimpleJson.encodeMap(current),
                edited = true,
                status = status,
                matchedProductId = matchedProductId,
                suggestedProductId = suggestedProductId,
                confidence = confidence,
                errorCode = firstError?.code?.name,
                errorMessage = firstError?.message,
                warningsJson = if (warnings.isEmpty()) null else SimpleJson.encodeList(warnings)
            )
        )
    }
}
