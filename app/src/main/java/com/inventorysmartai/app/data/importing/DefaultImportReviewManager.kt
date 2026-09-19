package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.local.database.dao.ImportDao
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportReviewManager
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
    private val productMatcher: ProductMatcher
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
            if (value.isNullOrBlank()) current.remove(field.name) else current[field.name] = value
        }

        val identityFields = setOf(ImportField.ITEM_NUMBER, ImportField.BARCODE, ImportField.PRODUCT_NAME)
        val identityChanged = edits.keys.any { it in identityFields }

        var status = entity.status
        var matchedProductId = entity.matchedProductId
        var suggestedProductId = entity.suggestedProductId
        var confidence = entity.confidence

        // Re-running ProductMatcher here is what lets an edit actually FIX a bad match, not just
        // change what is displayed — editing a wrong barcode and re-checking are the same action
        // from the reviewer's point of view. Skipped for a row that still has a hard validation
        // error: re-matching a row that cannot be accepted anyway would be wasted work, and the
        // row must go through Accept's own error check regardless.
        if (identityChanged && entity.errorCode == null) {
            val probe = ParsedImportRow(
                importJobId = entity.importJobId,
                rowIndex = entity.rowIndex,
                itemNumber = current[ImportField.ITEM_NUMBER.name],
                barcode = current[ImportField.BARCODE.name],
                name = current[ImportField.PRODUCT_NAME.name],
                quantity = null,
                rawJson = entity.rawData
            )
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

        importDao.updateRow(
            entity.copy(
                normalizedData = SimpleJson.encodeMap(current),
                edited = true,
                status = status,
                matchedProductId = matchedProductId,
                suggestedProductId = suggestedProductId,
                confidence = confidence
            )
        )
    }
}
