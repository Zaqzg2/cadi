package com.inventorysmartai.app.data.repository

import androidx.room.withTransaction
import com.inventorysmartai.app.data.importing.toDomain
import com.inventorysmartai.app.data.local.database.InventorySmartDatabase
import com.inventorysmartai.app.data.local.database.dao.BranchDao
import com.inventorysmartai.app.data.local.database.dao.CategoryDao
import com.inventorysmartai.app.data.local.database.dao.CountingDao
import com.inventorysmartai.app.data.local.database.dao.GoalDao
import com.inventorysmartai.app.data.local.database.dao.ImportDao
import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.PurchaseDao
import com.inventorysmartai.app.data.local.database.dao.UnitDao
import com.inventorysmartai.app.data.local.database.entity.BranchEntity
import com.inventorysmartai.app.data.local.database.entity.CategoryEntity
import com.inventorysmartai.app.data.local.database.entity.CommissionEntity
import com.inventorysmartai.app.data.local.database.entity.GoalEntity
import com.inventorysmartai.app.data.local.database.entity.ImportJobEntity
import com.inventorysmartai.app.data.local.database.entity.ImportMappingTemplateEntity
import com.inventorysmartai.app.data.local.database.entity.ImportRowEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountItemEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryMovementEntity
import com.inventorysmartai.app.data.local.database.entity.ProductEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestItemEntity
import com.inventorysmartai.app.data.local.database.entity.UnitEntity
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportMappingTemplate
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.NormalizedValue
import com.inventorysmartai.app.domain.importing.PipelineAnalysisResult
import com.inventorysmartai.app.domain.importing.SimpleJson
import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import com.inventorysmartai.app.domain.model.CommissionType
import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.domain.model.ImportJob
import com.inventorysmartai.app.domain.model.ImportJobStatus
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.model.ImportRowStatus
import com.inventorysmartai.app.domain.model.ImportSourceType
import com.inventorysmartai.app.domain.model.MovementType
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.domain.model.aggregateImportRowCounts
import com.inventorysmartai.app.domain.repository.ImportApprovalResult
import com.inventorysmartai.app.domain.repository.ImportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1/2's bookkeeping-only [createJob]/[finalizeJob] are kept exactly as they were (still
 * used by the PDF/IMAGE/CAMERA/BARCODE placeholders in DataCenterViewModel). Everything else
 * below is Phase 3: the real EXCEL/CSV pipeline's persistence, and [approveJob] — the ONE place
 * production tables are written, per import type, inside a single transaction (spec section 17).
 */
@Singleton
class ImportRepositoryImpl @Inject constructor(
    private val database: InventorySmartDatabase,
    private val importDao: ImportDao,
    private val productDao: ProductDao,
    private val branchDao: BranchDao,
    private val categoryDao: CategoryDao,
    private val unitDao: UnitDao,
    private val inventoryDao: InventoryDao,
    private val inventoryMovementDao: InventoryMovementDao,
    private val countingDao: CountingDao,
    private val purchaseDao: PurchaseDao,
    private val goalDao: GoalDao
) : ImportRepository {

    // ---------------------------------------------------------------------------------------
    // Phase 1/2 — unchanged
    // ---------------------------------------------------------------------------------------

    override fun observeJobs(): Flow<List<ImportJob>> =
        importDao.observeJobs().map { list -> list.map { it.toDomain() } }

    override fun observeJob(jobId: Long): Flow<ImportJob?> =
        importDao.observeJob(jobId).map { it?.toDomain() }

    override suspend fun getJob(jobId: Long): ImportJob? = importDao.getJobById(jobId)?.toDomain()

    override suspend fun createJob(sourceType: ImportSourceType, fileName: String?): Long {
        val now = System.currentTimeMillis()
        return importDao.insertJob(
            ImportJobEntity(
                sourceType = sourceType.name,
                fileName = fileName,
                status = ImportJobStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun finalizeJob(jobId: Long, rows: List<ImportRow>) {
        val counts = aggregateImportRowCounts(rows)
        importDao.updateJobCounts(
            jobId = jobId,
            status = ImportJobStatus.REVIEW_REQUIRED.name,
            totalRows = counts.totalRows,
            processedRows = counts.processedRows,
            acceptedRows = counts.acceptedRows,
            matchedRows = counts.matchedRows,
            newProductRows = counts.newProductRows,
            duplicateRows = counts.duplicateRows,
            errorRows = counts.errorRows,
            updatedAt = System.currentTimeMillis()
        )
    }

    // ---------------------------------------------------------------------------------------
    // Phase 3 — job lifecycle
    // ---------------------------------------------------------------------------------------

    override suspend fun startImportJob(
        sourceType: ImportSourceType,
        fileName: String?,
        fileSizeBytes: Long?,
        mimeType: String?,
        importType: ImportType,
        sheetName: String?,
        defaultBranchId: Long?,
        defaultSupplierId: Long?
    ): Long {
        val now = System.currentTimeMillis()
        return importDao.insertJob(
            ImportJobEntity(
                sourceType = sourceType.name,
                fileName = fileName,
                status = ImportJobStatus.PROCESSING.name,
                createdAt = now,
                updatedAt = now,
                importType = importType.name,
                sheetName = sheetName,
                defaultBranchId = defaultBranchId,
                defaultSupplierId = defaultSupplierId,
                fileSizeBytes = fileSizeBytes,
                mimeType = mimeType
            )
        )
    }

    override suspend fun persistAnalysis(jobId: Long, analysis: PipelineAnalysisResult) {
        val now = System.currentTimeMillis()
        // Replace, not append (see interface doc) — re-analyzing (a different sheet, a changed
        // column mapping) must not leave the previous attempt's rows lying around alongside it.
        importDao.deleteRowsForJob(jobId)

        val entities = analysis.analyses.map { rowAnalysis ->
            val row = rowAnalysis.row
            val storageMap = row.fields.entries.associate { (field, value) -> field.name to value.forStorage(field) }
            val warnings = rowAnalysis.validation.warnings.map { it.message }
            ImportRowEntity(
                importJobId = jobId,
                rowIndex = row.rowIndex,
                rawData = row.rawJson,
                normalizedData = SimpleJson.encodeMap(storageMap),
                matchedProductId = rowAnalysis.matchedProductId,
                suggestedProductId = rowAnalysis.suggestedProductId,
                confidence = rowAnalysis.confidence,
                status = rowAnalysis.resolvedStatus().name,
                errorMessage = rowAnalysis.validation.errors.firstOrNull()?.message,
                createdAt = now,
                errorCode = rowAnalysis.validation.errors.firstOrNull()?.code?.name,
                warningsJson = if (warnings.isEmpty()) null else SimpleJson.encodeList(warnings)
            )
        }
        importDao.insertRows(entities)

        val statuses = analysis.analyses.map { it.resolvedStatus() }
        importDao.updateJobCounts(
            jobId = jobId,
            status = ImportJobStatus.REVIEW_REQUIRED.name,
            totalRows = statuses.size,
            processedRows = statuses.size,
            acceptedRows = 0,
            matchedRows = statuses.count { it == ImportRowStatus.MATCHED },
            newProductRows = statuses.count { it == ImportRowStatus.NEW_PRODUCT },
            duplicateRows = statuses.count { it == ImportRowStatus.DUPLICATE },
            errorRows = statuses.count { it == ImportRowStatus.ERROR },
            updatedAt = now
        )
    }

    override suspend fun cancelJob(jobId: Long) {
        importDao.updateJobStatus(jobId, ImportJobStatus.CANCELLED.name, System.currentTimeMillis())
    }

    // ---------------------------------------------------------------------------------------
    // Phase 3 — Approval (spec section 17: one transaction, full rollback on any failure)
    // ---------------------------------------------------------------------------------------

    override suspend fun approveJob(jobId: Long): ImportApprovalResult {
        val jobEntity = importDao.getJobById(jobId)
            ?: return ImportApprovalResult(jobId, ImportJobStatus.FAILED, 0, 0, "لم يتم العثور على عملية الاستيراد")

        val importType = jobEntity.importType?.let { runCatching { ImportType.valueOf(it) }.getOrNull() }
            ?: return ImportApprovalResult(jobId, ImportJobStatus.FAILED, 0, 0, "نوع الاستيراد غير محدد لهذه العملية")

        val allRows = importDao.getRowsForJob(jobId)
        val acceptedRows = allRows.filter { it.status == ImportRowStatus.ACCEPTED.name }
        if (acceptedRows.isEmpty()) {
            val currentStatus = runCatching { ImportJobStatus.valueOf(jobEntity.status) }.getOrDefault(ImportJobStatus.REVIEW_REQUIRED)
            return ImportApprovalResult(jobId, currentStatus, 0, 0, "لا توجد صفوف مقبولة لاعتمادها — يرجى قبول صف واحد على الأقل")
        }

        val now = System.currentTimeMillis()

        return try {
            database.withTransaction {
                when (importType) {
                    ImportType.PRODUCTS -> approveProducts(acceptedRows, now)
                    ImportType.INVENTORY -> approveInventory(jobEntity, acceptedRows, now)
                    ImportType.COUNTING -> approveCounting(jobEntity, acceptedRows, now)
                    ImportType.PURCHASE_REQUESTS -> approvePurchaseRequests(jobEntity, acceptedRows, now)
                    ImportType.GOALS -> approveGoals(acceptedRows, now)
                    ImportType.SALES_INVOICES ->
                        error("استيراد فواتير المبيعات لا يحتوي بعد على منطق اعتماد متخصص في هذه المرحلة")
                }
            }
            val rejected = allRows.size - acceptedRows.size
            val status = if (rejected == 0) ImportJobStatus.COMPLETED else ImportJobStatus.PARTIALLY_COMPLETED
            importDao.updateJobApprovalOutcome(jobId, status.name, rejected, now, now)
            ImportApprovalResult(jobId, status, acceptedRows.size, rejected)
        } catch (e: Exception) {
            // Room's withTransaction already rolled back everything above on this exception —
            // nothing from this attempt was written. The job is deliberately left in its
            // previous status (not overwritten to FAILED here) so it stays available for retry
            // exactly as it was, per spec section 17.
            ImportApprovalResult(jobId, ImportJobStatus.FAILED, 0, acceptedRows.size, e.message ?: "فشل حفظ البيانات، تم التراجع عن كل التغييرات")
        }
    }

    private suspend fun approveProducts(rows: List<ImportRowEntity>, now: Long) {
        rows.forEach { entity ->
            val fields = SimpleJson.decodeMap(entity.normalizedData)
            resolveOrCreateProductId(entity, fields, now, updateMasterFieldsIfMatched = true)
        }
    }

    private suspend fun approveInventory(job: ImportJobEntity, rows: List<ImportRowEntity>, now: Long) {
        rows.forEach { entity ->
            val fields = SimpleJson.decodeMap(entity.normalizedData)
            val productId = resolveOrCreateProductId(entity, fields, now, updateMasterFieldsIfMatched = false)
            val branchId = resolveBranchId(fields, job, now)
            val newQuantity = fields[ImportField.CURRENT_STOCK.name]?.toDoubleOrNull()
                ?: error("قيمة \"${ImportField.CURRENT_STOCK.labelAr}\" مفقودة أو غير صالحة لأحد الصفوف")

            val existing = inventoryDao.getForProductAndBranch(productId, branchId)
            val delta = newQuantity - (existing?.quantity ?: 0.0)
            inventoryDao.upsert(
                InventoryEntity(
                    id = existing?.id ?: 0L,
                    productId = productId,
                    branchId = branchId,
                    quantity = newQuantity,
                    batchNumber = null,
                    expiryDate = existing?.expiryDate,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
            if (delta != 0.0) {
                inventoryMovementDao.insert(
                    InventoryMovementEntity(
                        productId = productId,
                        branchId = branchId,
                        movementType = MovementType.MANUAL.name,
                        quantityChange = delta,
                        referenceType = "IMPORT",
                        referenceId = job.id,
                        notes = importNote(job),
                        createdAt = now
                    )
                )
            }
        }
    }

    private suspend fun approveCounting(job: ImportJobEntity, rows: List<ImportRowEntity>, now: Long) {
        // One InventoryCountEntity per resolved branch — a count session is always scoped to a
        // single branch, exactly like the manual counting screen (see CountingRepositoryImpl).
        val byBranch = LinkedHashMap<Long, MutableList<Pair<ImportRowEntity, Map<String, String>>>>()
        rows.forEach { entity ->
            val fields = SimpleJson.decodeMap(entity.normalizedData)
            val branchId = resolveBranchId(fields, job, now)
            byBranch.getOrPut(branchId) { mutableListOf() }.add(entity to fields)
        }

        byBranch.forEach { (branchId, entries) ->
            val countId = countingDao.upsertCount(
                InventoryCountEntity(
                    branchId = branchId,
                    countDate = now,
                    status = CountStatus.COMPLETED.name,
                    notes = importNote(job),
                    createdAt = now,
                    updatedAt = now
                )
            )
            val items = mutableListOf<InventoryCountItemEntity>()
            entries.forEach { (entity, fields) ->
                val productId = resolveOrCreateProductId(entity, fields, now, updateMasterFieldsIfMatched = false)
                val counted = fields[ImportField.COUNTED_QUANTITY.name]?.toDoubleOrNull()
                    ?: error("قيمة \"${ImportField.COUNTED_QUANTITY.labelAr}\" مفقودة أو غير صالحة لأحد الصفوف")
                val existingStock = inventoryDao.getForProductAndBranch(productId, branchId)
                val systemQuantity = existingStock?.quantity ?: 0.0

                items += InventoryCountItemEntity(
                    countId = countId,
                    productId = productId,
                    systemQuantity = systemQuantity,
                    actualQuantity = counted,
                    createdAt = now,
                    updatedAt = now
                )

                val difference = counted - systemQuantity
                if (difference != 0.0) {
                    inventoryDao.upsert(
                        InventoryEntity(
                            id = existingStock?.id ?: 0L,
                            productId = productId,
                            branchId = branchId,
                            quantity = counted,
                            batchNumber = null,
                            expiryDate = existingStock?.expiryDate,
                            createdAt = existingStock?.createdAt ?: now,
                            updatedAt = now
                        )
                    )
                    inventoryMovementDao.insert(
                        InventoryMovementEntity(
                            productId = productId,
                            branchId = branchId,
                            movementType = MovementType.COUNT_ADJUSTMENT.name,
                            quantityChange = difference,
                            referenceType = "INVENTORY_COUNT",
                            referenceId = countId,
                            notes = importNote(job),
                            createdAt = now
                        )
                    )
                }
            }
            countingDao.insertItems(items)
        }
    }

    private suspend fun approvePurchaseRequests(job: ImportJobEntity, rows: List<ImportRowEntity>, now: Long) {
        val byBranch = LinkedHashMap<Long, MutableList<Pair<ImportRowEntity, Map<String, String>>>>()
        rows.forEach { entity ->
            val fields = SimpleJson.decodeMap(entity.normalizedData)
            val branchId = resolveBranchId(fields, job, now)
            byBranch.getOrPut(branchId) { mutableListOf() }.add(entity to fields)
        }

        byBranch.forEach { (branchId, entries) ->
            val requestId = purchaseDao.upsertRequest(
                PurchaseRequestEntity(
                    requestNumber = "IMP-${job.id}-$branchId-$now",
                    supplierId = job.defaultSupplierId,
                    branchId = branchId,
                    requestDate = now,
                    status = PurchaseStatus.SUBMITTED.name,
                    notes = importNote(job),
                    createdAt = now,
                    updatedAt = now
                )
            )
            val items = entries.map { (entity, fields) ->
                val productId = resolveOrCreateProductId(entity, fields, now, updateMasterFieldsIfMatched = false)
                val requested = fields[ImportField.REQUESTED_QUANTITY.name]?.toDoubleOrNull()
                    ?: error("قيمة \"${ImportField.REQUESTED_QUANTITY.labelAr}\" مفقودة أو غير صالحة لأحد الصفوف")
                val currentStock = fields[ImportField.CURRENT_STOCK.name]?.toDoubleOrNull()
                    ?: inventoryDao.getForProductAndBranch(productId, branchId)?.quantity
                    ?: 0.0
                PurchaseRequestItemEntity(
                    purchaseRequestId = requestId,
                    productId = productId,
                    currentStockSnapshot = currentStock,
                    requestedQuantity = requested,
                    createdAt = now,
                    updatedAt = now
                )
            }
            purchaseDao.insertItems(items)
        }
    }

    private suspend fun approveGoals(rows: List<ImportRowEntity>, now: Long) {
        // The spec's GOALS section requires only "product identification, target" — no period
        // column at all — so a one-calendar-month period starting now is a deliberate, documented
        // assumption (see README's Known limitations) rather than something inferred from data.
        val periodStart = startOfCurrentMonth(now)
        val periodEnd = endOfCurrentMonth(now)

        rows.forEach { entity ->
            val fields = SimpleJson.decodeMap(entity.normalizedData)
            val productId = resolveOrCreateProductId(entity, fields, now, updateMasterFieldsIfMatched = false)
            val target = fields[ImportField.TARGET.name]?.toDoubleOrNull()
                ?: error("قيمة \"${ImportField.TARGET.labelAr}\" مفقودة أو غير صالحة لأحد الصفوف")

            val goalId = goalDao.upsertGoal(
                GoalEntity(productId = productId, periodStart = periodStart, periodEnd = periodEnd, createdAt = now, updatedAt = now)
            )
            goalDao.insertCommissions(
                listOf(
                    CommissionEntity(
                        goalId = goalId,
                        groupOrder = 1,
                        targetQuantity = target,
                        commissionType = CommissionType.PERCENTAGE.name,
                        commissionValue = 0.0,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Shared approval helpers
    // ---------------------------------------------------------------------------------------

    /** Uses the row's already-resolved [ImportRowEntity.matchedProductId] when there is one
     *  (whether ProductMatcher found it, or a human set it via ChangeMatch during review) —
     *  never re-runs matching here, since Approval must save exactly what Review agreed to.
     *  Only PRODUCTS import updates an existing matched product's master fields
     *  ([updateMasterFieldsIfMatched]); the other four import types reference products without
     *  editing their master data. */
    private suspend fun resolveOrCreateProductId(
        entity: ImportRowEntity,
        fields: Map<String, String>,
        now: Long,
        updateMasterFieldsIfMatched: Boolean
    ): Long {
        entity.matchedProductId?.let { matchedId ->
            if (updateMasterFieldsIfMatched) {
                val existing = productDao.getById(matchedId) ?: return matchedId
                val categoryId = fields[ImportField.CATEGORY.name]?.let { findOrCreateCategoryId(it, now) } ?: existing.categoryId
                val unitId = fields[ImportField.UNIT.name]?.let { findOrCreateUnitId(it, now) } ?: existing.unitId
                val minStock = fields[ImportField.MIN_STOCK.name]?.toDoubleOrNull() ?: existing.minStock
                val reorderPoint = fields[ImportField.REORDER_POINT.name]?.toDoubleOrNull() ?: existing.reorderPoint
                val name = fields[ImportField.PRODUCT_NAME.name]?.takeIf { it.isNotBlank() } ?: existing.name
                productDao.update(
                    existing.copy(
                        name = name,
                        categoryId = categoryId,
                        unitId = unitId,
                        minStock = minStock,
                        reorderPoint = reorderPoint,
                        updatedAt = now
                    )
                )
            }
            return matchedId
        }

        // NEW_PRODUCT (or a PENDING/AMBIGUOUS row a human still chose to Accept without changing
        // its match — treated the same as a new product, since there is no other id to attach to).
        val name = fields[ImportField.PRODUCT_NAME.name]?.takeIf { it.isNotBlank() }
            ?: fields[ImportField.ITEM_NUMBER.name]
            ?: fields[ImportField.BARCODE.name]
            ?: "صنف مستورد"
        return productDao.upsert(
            ProductEntity(
                itemNumber = fields[ImportField.ITEM_NUMBER.name],
                barcode = fields[ImportField.BARCODE.name],
                name = name,
                categoryId = fields[ImportField.CATEGORY.name]?.let { findOrCreateCategoryId(it, now) },
                unitId = fields[ImportField.UNIT.name]?.let { findOrCreateUnitId(it, now) },
                minStock = fields[ImportField.MIN_STOCK.name]?.toDoubleOrNull() ?: 0.0,
                reorderPoint = fields[ImportField.REORDER_POINT.name]?.toDoubleOrNull() ?: 0.0,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun findOrCreateCategoryId(rawName: String, now: Long): Long? {
        val name = rawName.trim()
        if (name.isEmpty()) return null
        categoryDao.getByName(name)?.let { return it.id }
        return categoryDao.upsert(CategoryEntity(name = name, createdAt = now, updatedAt = now))
    }

    private suspend fun findOrCreateUnitId(rawName: String, now: Long): Long? {
        val name = rawName.trim()
        if (name.isEmpty()) return null
        unitDao.getByName(name)?.let { return it.id }
        return unitDao.upsert(UnitEntity(name = name, createdAt = now, updatedAt = now))
    }

    private suspend fun findOrCreateBranchId(rawName: String, now: Long): Long? {
        val name = rawName.trim()
        if (name.isEmpty()) return null
        branchDao.getByName(name)?.let { return it.id }
        return branchDao.upsert(BranchEntity(name = name, createdAt = now, updatedAt = now))
    }

    /** A mapped BRANCH column wins per-row (a file can legitimately mix branches); otherwise the
     *  job's [ImportJobEntity.defaultBranchId], chosen up front on the "اختر نوع البيانات" step.
     *  Neither present throws, which — inside [approveJob]'s transaction — rolls the WHOLE
     *  approval back rather than silently guessing a branch, per spec section 17. */
    private suspend fun resolveBranchId(fields: Map<String, String>, job: ImportJobEntity, now: Long): Long {
        fields[ImportField.BRANCH.name]?.let { name -> findOrCreateBranchId(name, now)?.let { return it } }
        job.defaultBranchId?.let { return it }
        error("تعذّر تحديد الفرع لأحد الصفوف — يرجى اختيار فرع افتراضي قبل التحليل أو إضافة عمود \"الفرع\" في الملف")
    }

    private fun importNote(job: ImportJobEntity): String =
        "مستورد من ملف: ${job.fileName ?: "غير معروف"}"

    private fun startOfCurrentMonth(epochMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfCurrentMonth(epochMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    // ---------------------------------------------------------------------------------------
    // Phase 3 — mapping templates (spec section 20)
    // ---------------------------------------------------------------------------------------

    override fun observeMappingTemplates(importType: ImportType): Flow<List<ImportMappingTemplate>> =
        importDao.observeTemplates(importType.name).map { list -> list.map { it.toDomain() } }

    override suspend fun saveMappingTemplate(
        name: String,
        importType: ImportType,
        headers: List<String>,
        mapping: Map<String, ImportField>
    ): Long {
        val now = System.currentTimeMillis()
        val fingerprint = ImportMappingTemplate.fingerprintOf(headers)
        val mappingJson = SimpleJson.encodeMap(
            mapping.entries.associate { (header, field) -> ArabicTextNormalizer.normalize(header) to field.name }
        )
        return importDao.insertTemplate(
            ImportMappingTemplateEntity(
                name = name,
                importType = importType.name,
                headerFingerprint = fingerprint,
                mappingJson = mappingJson,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun findBestMatchingTemplate(importType: ImportType, headers: List<String>): ImportMappingTemplate? =
        importDao.getTemplates(importType.name)
            .map { it.toDomain() }
            .map { template -> template to template.structureMatchRatio(headers) }
            .filter { (_, ratio) -> ratio >= ImportMappingTemplate.AUTO_OFFER_THRESHOLD }
            .maxByOrNull { (_, ratio) -> ratio }
            ?.first
}

/** Decides what gets persisted per field: text-like fields (name/category/unit/branch/notes)
 *  keep their human-readable [com.inventorysmartai.app.domain.importing.NormalizedValue.raw] text
 *  so a product is created/displayed with its real name rather than a lowercased match key;
 *  identifiers (barcode/item number) and numeric fields store their cleaned/parsed
 *  [com.inventorysmartai.app.domain.importing.NormalizedValue.normalized] form, which is what
 *  matching and arithmetic actually need. */
private fun NormalizedValue.forStorage(field: ImportField): String? = when {
    field == ImportField.ITEM_NUMBER || field == ImportField.BARCODE -> normalized
    field.isNumeric -> numeric?.toString() ?: normalized
    else -> raw
}
