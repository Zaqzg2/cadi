package com.inventorysmartai.app.domain.repository

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

/** Phase 1: bookkeeping only. No parser/matcher is wired up yet — see domain/importing. */
interface ImportRepository {
    fun observeJobs(): Flow<List<ImportJob>>
    suspend fun createJob(sourceType: ImportSourceType, fileName: String?): Long
    suspend fun finalizeJob(jobId: Long, rows: List<ImportRow>)
}
