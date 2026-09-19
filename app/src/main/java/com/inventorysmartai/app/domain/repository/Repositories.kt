package com.inventorysmartai.app.domain.repository

import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportMappingTemplate
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.PipelineAnalysisResult
import com.inventorysmartai.app.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * All repository contracts live in one file since each is a short set of method signatures —
 * the implementations (one class per area, real logic) live under data/repository/.
 */

interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
    fun observeProductsWithStock(): Flow<List<ProductStockSummary>>
    fun observeProductDetail(productId: Long): Flow<ProductStockSummary?>
    fun observeMovements(productId: Long): Flow<List<InventoryMovement>>
    suspend fun getByBarcode(barcode: String): Product?
    suspend fun getByItemNumber(itemNumber: String): Product?
    suspend fun upsert(product: Product): Long
}

interface CatalogRepository {
    fun observeBranches(): Flow<List<Branch>>
    fun observeCategories(): Flow<List<Category>>
    fun observeUnits(): Flow<List<UnitOfMeasure>>
    suspend fun upsertBranch(branch: Branch): Long
    suspend fun upsertCategory(category: Category): Long
    suspend fun upsertUnit(unit: UnitOfMeasure): Long
    suspend fun deleteBranch(id: Long)
    suspend fun deleteCategory(id: Long)
    suspend fun deleteUnit(id: Long)
}

interface PartyRepository {
    fun observeCustomers(): Flow<List<Customer>>
    fun observeSuppliers(): Flow<List<Supplier>>
    suspend fun upsertCustomer(customer: Customer): Long
    suspend fun upsertSupplier(supplier: Supplier): Long
}

interface CountingRepository {
    fun observeCounts(): Flow<List<InventoryCount>>
    fun observeRecentCounts(limit: Int): Flow<List<InventoryCount>>
    fun observeCount(countId: Long): Flow<InventoryCount?>
    suspend fun saveCount(count: InventoryCount): Long
}

interface GoalRepository {
    fun observeGoals(): Flow<List<Goal>>
    fun observeGoal(goalId: Long): Flow<Goal?>
    fun observeGoalForProduct(productId: Long): Flow<Goal?>
    fun observeOverallAchievementPercent(): Flow<Double>
    suspend fun saveGoal(goal: Goal): Long
}

interface PurchaseRepository {
    fun observeRequests(): Flow<List<PurchaseRequest>>
    fun observeRecentRequests(limit: Int): Flow<List<PurchaseRequest>>
    fun observeRequest(requestId: Long): Flow<PurchaseRequest?>
    suspend fun saveRequest(request: PurchaseRequest): Long
    suspend fun receive(receipt: PurchaseReceipt, lines: List<PurchaseReceiptLine>)
}

interface SalesRepository {
    fun observeInvoices(): Flow<List<SalesInvoice>>
    fun observeRecentInvoices(limit: Int): Flow<List<SalesInvoice>>
    fun observeInvoice(invoiceId: Long): Flow<SalesInvoice?>
    suspend fun saveInvoice(invoice: SalesInvoice): Long
    suspend fun getSoldQuantity(productId: Long, fromDate: Long, toDate: Long): Double
}

interface ReportsRepository {
    fun observeInventoryByStatus(): Flow<Map<InventoryStatus, Int>>
    fun observeSalesTotalsByDay(daysBack: Int): Flow<List<Pair<Long, Double>>>
    fun observePurchaseTotalsByDay(daysBack: Int): Flow<List<Pair<Long, Double>>>
}

interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    fun observeUseArabicIndicDigits(): Flow<Boolean>
    suspend fun setUseArabicIndicDigits(value: Boolean)
    fun observeNearExpiryWindowDays(): Flow<Int>
    suspend fun setNearExpiryWindowDays(days: Int)
    fun observeDefaultLowStockThreshold(): Flow<Double>
    suspend fun setDefaultLowStockThreshold(value: Double)
    fun observeIsDemoDataLoaded(): Flow<Boolean>
    suspend fun loadDemoData()
}

interface AttachmentRepository {
    fun observeAttachments(ownerType: AttachmentOwnerType, ownerId: Long): Flow<List<Attachment>>
    suspend fun addAttachment(attachment: Attachment): Long
    suspend fun deleteAttachment(id: Long)
}

/**
 * Phase 1/2 shipped [createJob]/[finalizeJob] as bookkeeping only. Phase 3 wires up the real
 * pipeline for EXCEL/CSV x PRODUCTS/INVENTORY/COUNTING/PURCHASE_REQUESTS/GOALS (see
 * domain/importing and [com.inventorysmartai.app.data.repository.ImportRepositoryImpl]) —
 * [createJob]/[finalizeJob] are kept unchanged, still used as-is for the PDF/IMAGE/CAMERA/BARCODE
 * placeholders, which remain out of scope (no OCR/AI parsing here) exactly as before.
 */
interface ImportRepository {
    fun observeJobs(): Flow<List<ImportJob>>
    fun observeJob(jobId: Long): Flow<ImportJob?>
    suspend fun getJob(jobId: Long): ImportJob?

    suspend fun createJob(sourceType: ImportSourceType, fileName: String?): Long
    suspend fun finalizeJob(jobId: Long, rows: List<ImportRow>)

    /** Creates a job for the real EXCEL/CSV pipeline, carrying the extra context the spec's
     *  "اختر نوع البيانات" step gathers before analysis even starts. */
    suspend fun startImportJob(
        sourceType: ImportSourceType,
        fileName: String?,
        fileSizeBytes: Long?,
        mimeType: String?,
        importType: ImportType,
        sheetName: String?,
        defaultBranchId: Long?,
        defaultSupplierId: Long?
    ): Long

    /** Persists one [PipelineAnalysisResult] as pending [ImportRow]s and moves the job to
     *  REVIEW_REQUIRED — this is what the Review screen (backed by
     *  [com.inventorysmartai.app.domain.importing.ImportReviewManager]) reads afterwards; nothing
     *  here touches production tables yet. Re-analyzing the same job (e.g. after the sheet or
     *  column mapping changes) replaces the previously persisted pending rows rather than
     *  appending to them. */
    suspend fun persistAnalysis(jobId: Long, analysis: PipelineAnalysisResult)

    /** Step 12 (Approval): the ONE place production tables are actually written, inside a single
     *  database transaction per the spec's "transactional save" requirement (section 17). Rolls
     *  back entirely on failure ("اعتماد الاستيراد" is all-or-nothing per job) and leaves the job
     *  available for retry rather than leaving it half-applied. */
    suspend fun approveJob(jobId: Long): ImportApprovalResult

    suspend fun cancelJob(jobId: Long)

    // --- Import mapping templates (spec section 20) ---
    fun observeMappingTemplates(importType: ImportType): Flow<List<ImportMappingTemplate>>
    suspend fun saveMappingTemplate(
        name: String,
        importType: ImportType,
        headers: List<String>,
        mapping: Map<String, ImportField>
    ): Long

    /** The best saved template for this file's headers, or null if nothing clears
     *  [ImportMappingTemplate.AUTO_OFFER_THRESHOLD] — "do not automatically apply it if the
     *  structure does not match sufficiently". */
    suspend fun findBestMatchingTemplate(importType: ImportType, headers: List<String>): ImportMappingTemplate?
}

/** Outcome of [ImportRepository.approveJob]. Only rows a human marked ACCEPTED during review are
 *  ever written, and they are written together in ONE transaction: [status] is COMPLETED when
 *  every row in the job ended up ACCEPTED and the transaction succeeded, PARTIALLY_COMPLETED when
 *  the transaction succeeded but some rows were REJECTED/ignored/left as errors by the human
 *  (a genuinely normal outcome — the spec's own worked example, "Total rows: 120 / Accepted
 *  rows: 108", is exactly this), or FAILED when the transaction itself threw — in which case it
 *  rolled back completely and [savedRows] is 0, per the spec's "rollback, show error, keep
 *  import available for retry" (section 17). */
data class ImportApprovalResult(
    val jobId: Long,
    val status: ImportJobStatus,
    val savedRows: Int,
    val failedRows: Int,
    val errorMessage: String? = null
)
