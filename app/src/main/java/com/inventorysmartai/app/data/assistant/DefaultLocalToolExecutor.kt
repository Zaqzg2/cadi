package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.domain.assistant.LocalToolExecutor
import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.model.PurchaseRequestItem
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.CountingRepository
import com.inventorysmartai.app.domain.repository.GoalRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import com.inventorysmartai.app.domain.repository.ReportsRepository
import com.inventorysmartai.app.domain.repository.SalesRepository
import com.inventorysmartai.app.domain.usecase.CalculateGoalAchievementUseCase
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One method per [com.inventorysmartai.app.domain.assistant.ToolExecutionSite.LOCAL] tool from
 * backend `gemini/ToolCatalog.kt` — tool names below are copied verbatim from there so the two
 * stay in lockstep without a translation table. Every "get..." method reads a `Flow` snapshot via
 * [kotlinx.coroutines.flow.first] (a tool call is a one-shot question, not a subscription) and
 * returns a JSON string built with `org.json` (already part of the Android SDK — no reason to add
 * a second JSON library alongside Moshi just for these ad-hoc, per-tool-different result shapes).
 * Never throws for "no rows found": an empty JSON array is a normal, valid answer Gemini can
 * report as "لا توجد نتائج" — only [execute] itself throws, for a genuinely unknown tool name.
 */
@Singleton
class DefaultLocalToolExecutor @Inject constructor(
    private val productRepository: ProductRepository,
    private val catalogRepository: CatalogRepository,
    private val countingRepository: CountingRepository,
    private val goalRepository: GoalRepository,
    private val purchaseRepository: PurchaseRepository,
    private val salesRepository: SalesRepository,
    private val reportsRepository: ReportsRepository,
    private val calculateGoalAchievement: CalculateGoalAchievementUseCase
) : LocalToolExecutor {

    override fun requiresConfirmation(name: String): Boolean = name == "createPurchaseRequest"

    override suspend fun describeForConfirmation(name: String, argumentsJson: String): String {
        if (name != "createPurchaseRequest") return "تنفيذ العملية: $name"
        val args = JSONObject(argumentsJson)
        val items = args.optJSONArray("items") ?: JSONArray()
        val branchName = catalogRepository.observeBranches().first().firstOrNull { it.id == args.optLong("branchId") }?.name
            ?: "الفرع #${args.optLong("branchId")}"
        return "إنشاء طلب شراء لفرع \"$branchName\" يتضمن ${items.length()} صنف/أصناف"
    }

    override suspend fun execute(name: String, argumentsJson: String): String {
        val args = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        return when (name) {
            "getProducts" -> getProducts(args)
            "getProduct" -> getProduct(args)
            "searchProducts" -> searchProducts(args)
            "getInventory" -> getInventory(args)
            "getBranchInventory" -> getBranchInventory(args)
            "getLowStock" -> getByStatus(args, InventoryStatus.LOW)
            "getZeroStock" -> getByStatus(args, InventoryStatus.ZERO)
            "getExpiredItems" -> getByStatus(args, InventoryStatus.EXPIRED)
            "getNearExpiryItems" -> getByStatus(args, InventoryStatus.NEAR_EXPIRY)
            "getInventoryMovements" -> getInventoryMovements(args)
            "getCounting" -> getCounting(args)
            "getPurchaseRequests" -> getPurchaseRequests(args)
            "getPurchaseRequest" -> getPurchaseRequest(args)
            "getSalesInvoices" -> getSalesInvoices(args)
            "getSalesInvoice" -> getSalesInvoice(args)
            "getGoals" -> getGoals(args)
            "getGoalProgress" -> getGoalProgress(args)
            "compareBranches" -> compareBranches(args)
            "createPurchaseRequest" -> createPurchaseRequest(args)
            "createReport" -> createReport(args)
            else -> JSONObject().put("error", "أداة غير معروفة: $name").toString()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Products / inventory
    // ---------------------------------------------------------------------------------------

    private suspend fun getProducts(args: JSONObject): String {
        val categoryId = args.optLongOrNull("categoryId")
        val limit = args.optIntOrNull("limit") ?: 50
        val products = productRepository.observeProducts().first()
            .filter { categoryId == null || it.categoryId == categoryId }
            .take(limit)
        return JSONArray(products.map { it.toJson() }).toString()
    }

    private suspend fun getProduct(args: JSONObject): String {
        val productId = args.optLong("productId")
        val summary = productRepository.observeProductDetail(productId).first()
            ?: return JSONObject().put("found", false).toString()
        return summary.toJson().toString()
    }

    private suspend fun searchProducts(args: JSONObject): String {
        val query = args.optString("query").trim().lowercase(Locale.getDefault())
        val limit = args.optIntOrNull("limit") ?: 20
        val results = productRepository.observeProducts().first().filter { p ->
            p.name.lowercase(Locale.getDefault()).contains(query) ||
                p.itemNumber?.lowercase(Locale.getDefault())?.contains(query) == true ||
                p.barcode?.contains(query) == true
        }.take(limit)
        return JSONArray(results.map { it.toJson() }).toString()
    }

    private suspend fun getInventory(args: JSONObject): String {
        val productId = args.optLongOrNull("productId")
        val statusFilter = args.optString("status").takeIf { it.isNotBlank() }?.let { runCatching { InventoryStatus.valueOf(it) }.getOrNull() }
        val summaries = productRepository.observeProductsWithStock().first()
            .filter { productId == null || it.product.id == productId }
            .filter { statusFilter == null || it.status == statusFilter }
        return JSONArray(summaries.map { it.toJson() }).toString()
    }

    private suspend fun getBranchInventory(args: JSONObject): String {
        val branchId = args.optLong("branchId")
        val summaries = productRepository.observeProductsWithStock().first()
            .mapNotNull { summary ->
                val branchStock = summary.branchStocks.firstOrNull { it.branchId == branchId } ?: return@mapNotNull null
                JSONObject()
                    .put("productId", summary.product.id)
                    .put("productName", summary.product.name)
                    .put("quantity", branchStock.quantity)
                    .put("status", summary.status.name)
            }
        return JSONArray(summaries).toString()
    }

    private suspend fun getByStatus(args: JSONObject, status: InventoryStatus): String {
        val branchId = args.optLongOrNull("branchId")
        val summaries = productRepository.observeProductsWithStock().first()
            .filter { it.status == status }
            .filter { branchId == null || it.branchStocks.any { bs -> bs.branchId == branchId } }
        return JSONArray(summaries.map { it.toJson() }).toString()
    }

    private suspend fun getInventoryMovements(args: JSONObject): String {
        val productId = args.optLong("productId")
        val limit = args.optIntOrNull("limit") ?: 30
        val movements = productRepository.observeMovements(productId).first().take(limit)
        return JSONArray(
            movements.map { m ->
                JSONObject()
                    .put("id", m.id)
                    .put("type", m.movementType.name)
                    .put("quantityChange", m.quantityChange)
                    .put("branchId", m.branchId)
                    .put("createdAt", m.createdAt)
                    .put("notes", m.notes)
            }
        ).toString()
    }

    // ---------------------------------------------------------------------------------------
    // Counting / purchase requests / sales invoices / goals
    // ---------------------------------------------------------------------------------------

    private suspend fun getCounting(args: JSONObject): String {
        val countId = args.optLongOrNull("countId")
        if (countId != null) {
            val count = countingRepository.observeCount(countId).first() ?: return JSONObject().put("found", false).toString()
            return JSONObject()
                .put("id", count.id)
                .put("branchName", count.branchName)
                .put("countDate", count.countDate)
                .put("status", count.status.name)
                .put(
                    "items",
                    JSONArray(
                        count.items.map { item ->
                            JSONObject()
                                .put("productName", item.productName)
                                .put("systemQuantity", item.systemQuantity)
                                .put("actualQuantity", item.actualQuantity)
                                .put("difference", item.difference)
                        }
                    )
                ).toString()
        }
        val limit = args.optIntOrNull("limit") ?: 10
        val counts = countingRepository.observeRecentCounts(limit).first()
        return JSONArray(
            counts.map { c ->
                JSONObject().put("id", c.id).put("branchName", c.branchName).put("countDate", c.countDate).put("status", c.status.name)
                    .put("itemCount", c.items.size)
                    .put("completedFully", c.status == CountStatus.COMPLETED)
            }
        ).toString()
    }

    private suspend fun getPurchaseRequests(args: JSONObject): String {
        val branchId = args.optLongOrNull("branchId")
        val status = args.optString("status").takeIf { it.isNotBlank() }?.let { runCatching { PurchaseStatus.valueOf(it) }.getOrNull() }
        val limit = args.optIntOrNull("limit") ?: 20
        val requests = purchaseRepository.observeRecentRequests(limit * 3).first() // over-fetch, then filter — the DAO has no filtered query yet
            .filter { branchId == null || it.branchId == branchId }
            .filter { status == null || it.status == status }
            .take(limit)
        return JSONArray(requests.map { it.toSummaryJson() }).toString()
    }

    private suspend fun getPurchaseRequest(args: JSONObject): String {
        val requestId = args.optLong("requestId")
        val request = purchaseRepository.observeRequest(requestId).first() ?: return JSONObject().put("found", false).toString()
        return request.toDetailJson().toString()
    }

    private suspend fun getSalesInvoices(args: JSONObject): String {
        val branchId = args.optLongOrNull("branchId")
        val fromDate = args.optDoubleOrNull("fromDate")?.toLong()
        val toDate = args.optDoubleOrNull("toDate")?.toLong()
        val limit = args.optIntOrNull("limit") ?: 20
        val invoices = salesRepository.observeRecentInvoices(limit * 3).first()
            .filter { branchId == null || it.branchId == branchId }
            .filter { fromDate == null || it.invoiceDate >= fromDate }
            .filter { toDate == null || it.invoiceDate <= toDate }
            .take(limit)
        return JSONArray(
            invoices.map { inv ->
                JSONObject()
                    .put("id", inv.id)
                    .put("invoiceNumber", inv.invoiceNumber)
                    .put("invoiceDate", inv.invoiceDate)
                    .put("customerName", inv.customerName)
                    .put("total", inv.total)
                    .put("itemCount", inv.items.size)
            }
        ).toString()
    }

    private suspend fun getSalesInvoice(args: JSONObject): String {
        val invoiceId = args.optLong("invoiceId")
        val invoice = salesRepository.observeInvoice(invoiceId).first() ?: return JSONObject().put("found", false).toString()
        return invoice.toDetailJson().toString()
    }

    private suspend fun getGoals(args: JSONObject): String {
        val productId = args.optLongOrNull("productId")
        val goals = if (productId != null) {
            listOfNotNull(goalRepository.observeGoalForProduct(productId).first())
        } else {
            goalRepository.observeGoals().first()
        }
        return JSONArray(
            goals.map { g ->
                JSONObject()
                    .put("id", g.id)
                    .put("productId", g.productId)
                    .put("periodStart", g.periodStart)
                    .put("periodEnd", g.periodEnd)
                    .put("groupCount", g.groups.size)
                    .put("totalTarget", g.groups.sumOf { it.targetQuantity })
            }
        ).toString()
    }

    private suspend fun getGoalProgress(args: JSONObject): String {
        val goalId = args.optLong("goalId")
        val goal = goalRepository.observeGoal(goalId).first() ?: return JSONObject().put("found", false).toString()
        val achievement = calculateGoalAchievement(goal)
        return JSONObject()
            .put("goalId", goal.id)
            .put("productId", goal.productId)
            .put("target", achievement.totalTarget)
            .put("achieved", achievement.achievedQuantity)
            .put("remaining", achievement.remainingQuantity)
            .put("percentComplete", achievement.achievementPercent)
            .toString()
    }

    private suspend fun compareBranches(args: JSONObject): String {
        val branchIdsArray = args.optJSONArray("branchIds") ?: JSONArray()
        val branchIds = (0 until branchIdsArray.length()).map { branchIdsArray.getLong(it) }
        val productId = args.optLongOrNull("productId")
        val metric = args.optString("metric", "STOCK")

        val branches = catalogRepository.observeBranches().first().associateBy { it.id }

        if (metric == "SALES") {
            val invoices = salesRepository.observeInvoices().first()
            val results = branchIds.map { branchId ->
                val total = invoices.filter { it.branchId == branchId }.sumOf { it.total }
                JSONObject().put("branchId", branchId).put("branchName", branches[branchId]?.name).put("totalSales", total)
            }
            return JSONArray(results).toString()
        }

        val summaries = productRepository.observeProductsWithStock().first()
            .filter { productId == null || it.product.id == productId }
        val results = branchIds.map { branchId ->
            val quantity = summaries.sumOf { s -> s.branchStocks.firstOrNull { it.branchId == branchId }?.quantity ?: 0.0 }
            val lowOrZeroCount = summaries.count { s ->
                s.branchStocks.any { it.branchId == branchId } && (s.status == InventoryStatus.LOW || s.status == InventoryStatus.ZERO)
            }
            JSONObject()
                .put("branchId", branchId)
                .put("branchName", branches[branchId]?.name)
                .put("totalQuantity", quantity)
                .put("lowOrZeroStockProductCount", lowOrZeroCount)
        }
        return JSONArray(results).toString()
    }

    // ---------------------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------------------

    private suspend fun createPurchaseRequest(args: JSONObject): String {
        val branchId = args.optLong("branchId")
        val supplierId = args.optLongOrNull("supplierId")
        val itemsArray = args.optJSONArray("items") ?: JSONArray()
        val notes = args.optString("notes").takeIf { it.isNotBlank() }

        val items = (0 until itemsArray.length()).map { i ->
            val item = itemsArray.getJSONObject(i)
            val productId = item.optLong("productId")
            val product = productRepository.observeProductDetail(productId).first()
            PurchaseRequestItem(
                productId = productId,
                currentStockSnapshot = product?.totalQuantity ?: 0.0,
                requestedQuantity = item.optDouble("quantity")
            )
        }

        val request = PurchaseRequest(
            requestNumber = "AI-${System.currentTimeMillis()}",
            supplierId = supplierId,
            branchId = branchId,
            requestDate = System.currentTimeMillis(),
            status = PurchaseStatus.SUBMITTED,
            notes = notes,
            items = items
        )
        val id = purchaseRepository.saveRequest(request)
        return JSONObject().put("created", true).put("purchaseRequestId", id).toString()
    }

    private suspend fun createReport(args: JSONObject): String {
        val reportType = args.optString("reportType")
        val branchId = args.optLongOrNull("branchId")

        val markdown = when (reportType) {
            "INVENTORY" -> {
                val byStatus = reportsRepository.observeInventoryByStatus().first()
                buildString {
                    appendLine("# تقرير الجرد")
                    InventoryStatus.entries.forEach { status -> appendLine("- ${status.name}: ${byStatus[status] ?: 0}") }
                }
            }
            "EXPIRED_ITEMS" -> {
                val expired = productRepository.observeProductsWithStock().first().filter { it.status == InventoryStatus.EXPIRED }
                buildString {
                    appendLine("# تقرير الأصناف منتهية الصلاحية")
                    expired.forEach { appendLine("- ${it.product.name}") }
                    if (expired.isEmpty()) appendLine("لا توجد أصناف منتهية الصلاحية حاليًا.")
                }
            }
            "GOALS" -> {
                val goals = goalRepository.observeGoals().first()
                buildString {
                    appendLine("# تقرير الأهداف")
                    goals.forEach { g -> appendLine("- المنتج #${g.productId}: إجمالي الهدف ${g.groups.sumOf { it.targetQuantity }}") }
                }
            }
            "SALES" -> {
                val invoices = salesRepository.observeRecentInvoices(50).first().filter { branchId == null || it.branchId == branchId }
                buildString {
                    appendLine("# تقرير المبيعات")
                    appendLine("عدد الفواتير: ${invoices.size}")
                    appendLine("إجمالي المبيعات: ${invoices.sumOf { it.total }}")
                }
            }
            "PURCHASES" -> {
                val requests = purchaseRepository.observeRecentRequests(50).first().filter { branchId == null || it.branchId == branchId }
                buildString {
                    appendLine("# تقرير طلبات الشراء")
                    appendLine("عدد الطلبات: ${requests.size}")
                }
            }
            else -> "# تقرير\n(نوع التقرير \"$reportType\" غير مدعوم بعد)"
        }

        return JSONObject()
            .put("reportType", reportType)
            .put("title", "تقرير $reportType")
            .put("reportMarkdown", markdown)
            .toString()
    }

    // ---------------------------------------------------------------------------------------
    // JSON helpers
    // ---------------------------------------------------------------------------------------

    private fun Product.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("itemNumber", itemNumber)
        .put("barcode", barcode)
        .put("categoryId", categoryId)

    private fun ProductStockSummary.toJson(): JSONObject = JSONObject()
        .put("productId", product.id)
        .put("productName", product.name)
        .put("totalQuantity", totalQuantity)
        .put("status", status.name)
        .put("nearestExpiryDate", nearestExpiryDate)
        .put(
            "branchStocks",
            JSONArray(branchStocks.map { bs -> JSONObject().put("branchId", bs.branchId).put("quantity", bs.quantity) })
        )

    private fun PurchaseRequest.toSummaryJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("requestNumber", requestNumber)
        .put("branchName", branchName)
        .put("status", status.name)
        .put("itemCount", items.size)
        .put("requestDate", requestDate)

    private fun PurchaseRequest.toDetailJson(): JSONObject = toSummaryJson()
        .put(
            "items",
            JSONArray(
                items.map { item ->
                    JSONObject()
                        .put("productName", item.productName)
                        .put("currentStockSnapshot", item.currentStockSnapshot)
                        .put("requestedQuantity", item.requestedQuantity)
                        .put("approvedQuantity", item.approvedQuantity)
                }
            )
        )

    private fun SalesInvoice.toDetailJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("invoiceNumber", invoiceNumber)
        .put("invoiceDate", invoiceDate)
        .put("customerName", customerName)
        .put("total", total)
        .put(
            "items",
            JSONArray(
                items.map { item ->
                    JSONObject()
                        .put("productName", item.itemNameSnapshot ?: item.productName)
                        .put("quantity", item.quantity)
                        .put("unitPrice", item.unitPrice)
                        .put("lineTotal", item.lineTotal)
                }
            )
        )

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
}
