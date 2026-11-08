package com.inventorysmartai.app.domain.dashboard

import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.domain.model.GoalAchievement
import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.domain.model.InventoryCount
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.stockops.StockOperation
import com.inventorysmartai.app.domain.stockops.StockOperationType
import java.util.Calendar

/*
 * أرقام «لوحات المعلومات» في صفحات الأهداف والجرد والمشتريات والمبيعات والتحويل. دوال صرفة بلا أي اعتماد على الواجهة كي
 * تُختبر وحدها، والشاشات تكتفي بعرض ما تحسبه هنا.
 */

/** أعلى [n] عناصر بالقيمة (أو بالقيمة المطلقة إن كانت الفروق سالبة وموجبة)، الأكبر أولًا، وتُهمل القيم الصفرية. */
fun topEntries(values: Map<String, Double>, n: Int, byAbsolute: Boolean = false): List<Pair<String, Double>> =
    values.entries
        .filter { it.value != 0.0 && it.value.isFinite() }
        .sortedByDescending { if (byAbsolute) kotlin.math.abs(it.value) else it.value }
        .take(n)
        .map { it.key to it.value }

/** نسبة [part] من [whole] بين 0 و100، و0 حين لا يوجد أصل (لا قسمة على صفر). */
fun percentOf(part: Double, whole: Double): Double =
    if (whole <= 0.0) 0.0 else (part / whole * 100.0).coerceIn(0.0, 100.0)

// ------------------------------------------------------------------------------------------------------------------
// الأهداف
// ------------------------------------------------------------------------------------------------------------------

data class GoalSummary(
    val count: Int,
    val totalTarget: Double,
    val achieved: Double,
    val remaining: Double,
    val overallPercent: Double,
    val completed: Int,
    val inProgress: Int,
    val notStarted: Int
)

/** مكتمل = بلغ 100% فأكثر، لم يبدأ = لا مبيعات محققة، وما بينهما قيد التنفيذ. */
fun goalSummary(achievements: List<GoalAchievement>): GoalSummary {
    val target = achievements.sumOf { it.totalTarget }
    val achieved = achievements.sumOf { it.achievedQuantity }
    val completed = achievements.count { it.achievementPercent >= 100.0 }
    val notStarted = achievements.count { it.achievementPercent < 100.0 && it.achievedQuantity <= 0.0 }
    return GoalSummary(
        count = achievements.size,
        totalTarget = target,
        achieved = achieved,
        remaining = achievements.sumOf { it.remainingQuantity },
        overallPercent = percentOf(achieved, target),
        completed = completed,
        inProgress = achievements.size - completed - notStarted,
        notStarted = notStarted
    )
}

// ------------------------------------------------------------------------------------------------------------------
// الجرد
// ------------------------------------------------------------------------------------------------------------------

data class CountingSummary(
    val total: Int,
    val completed: Int,
    /** مسودة أو جارٍ. */
    val open: Int,
    val cancelled: Int,
    /** أصناف بها فرق بين النظام والفعلي في عمليات الجرد المكتملة. */
    val itemsWithDifference: Int,
    /** مجموع النقص (قيمة موجبة) في الجرد المكتمل. */
    val shortage: Double,
    /** مجموع الزيادة في الجرد المكتمل. */
    val overage: Double
)

/** الفروق تُحسب من الجرد المكتمل فقط: المسودة لا تعني شيئًا بعد، والملغى لم يغيّر المخزون. */
fun countingSummary(counts: List<InventoryCount>): CountingSummary {
    val diffs = counts.filter { it.status == CountStatus.COMPLETED }.flatMap { it.items }.map { it.difference }
    return CountingSummary(
        total = counts.size,
        completed = counts.count { it.status == CountStatus.COMPLETED },
        open = counts.count { it.status == CountStatus.DRAFT || it.status == CountStatus.IN_PROGRESS },
        cancelled = counts.count { it.status == CountStatus.CANCELLED },
        itemsWithDifference = diffs.count { it != 0.0 },
        shortage = -diffs.filter { it < 0.0 }.sum(),
        overage = diffs.filter { it > 0.0 }.sum()
    )
}

/** صافي فرق كل صنف (الفعلي − النظام) عبر الجرد المكتمل، باسم الصنف. */
fun countingDifferencesByProduct(counts: List<InventoryCount>): Map<String, Double> =
    counts.filter { it.status == CountStatus.COMPLETED }
        .flatMap { it.items }
        .groupBy { it.productName ?: "صنف" }
        .mapValues { (_, items) -> items.sumOf { it.difference } }

// ------------------------------------------------------------------------------------------------------------------
// المشتريات
// ------------------------------------------------------------------------------------------------------------------

private val OPEN_PURCHASE_STATUSES = setOf(
    PurchaseStatus.DRAFT, PurchaseStatus.SUBMITTED, PurchaseStatus.APPROVED, PurchaseStatus.PARTIALLY_APPROVED, PurchaseStatus.ORDERED
)

fun isOpenPurchase(status: PurchaseStatus): Boolean = status in OPEN_PURCHASE_STATUSES

data class PurchaseSummary(
    val total: Int,
    val open: Int,
    val received: Int,
    /** ملغى أو مرفوض. */
    val closedWithoutReceipt: Int,
    /** المطلوب والمستلم يُحسبان على الطلبات الفعّالة فقط (دون الملغى والمرفوض). */
    val requestedQuantity: Double,
    val receivedQuantity: Double,
    val receivedPercent: Double
)

fun purchaseSummary(requests: List<PurchaseRequest>): PurchaseSummary {
    val active = requests.filter { it.status != PurchaseStatus.CANCELLED && it.status != PurchaseStatus.REJECTED }
    val requested = active.sumOf { r -> r.items.sumOf { it.requestedQuantity } }
    val received = active.sumOf { r -> r.items.sumOf { it.receivedQuantity } }
    return PurchaseSummary(
        total = requests.size,
        open = requests.count { isOpenPurchase(it.status) },
        received = requests.count { it.status == PurchaseStatus.RECEIVED },
        closedWithoutReceipt = requests.count { it.status == PurchaseStatus.CANCELLED || it.status == PurchaseStatus.REJECTED },
        requestedQuantity = requested,
        receivedQuantity = received,
        receivedPercent = percentOf(received, requested)
    )
}

/** الكمية المطلوبة لكل مورد (الطلبات الفعّالة فقط). */
fun requestedBySupplier(requests: List<PurchaseRequest>): Map<String, Double> =
    requests.filter { it.status != PurchaseStatus.CANCELLED && it.status != PurchaseStatus.REJECTED }
        .groupBy { it.supplierName?.takeIf { name -> name.isNotBlank() } ?: "بدون مورد" }
        .mapValues { (_, list) -> list.sumOf { r -> r.items.sumOf { it.requestedQuantity } } }

/** الكمية المطلوبة لكل صنف (الطلبات الفعّالة فقط). */
fun requestedByProduct(requests: List<PurchaseRequest>): Map<String, Double> =
    requests.filter { it.status != PurchaseStatus.CANCELLED && it.status != PurchaseStatus.REJECTED }
        .flatMap { it.items }
        .groupBy { it.productName ?: "صنف" }
        .mapValues { (_, items) -> items.sumOf { it.requestedQuantity } }

// ------------------------------------------------------------------------------------------------------------------
// المبيعات
// ------------------------------------------------------------------------------------------------------------------

data class SalesSummary(
    val invoices: Int,
    val confirmed: Int,
    val drafts: Int,
    val cancelled: Int,
    /** إجمالي الفواتير المؤكدة فقط: المسودة والملغاة ليست مبيعات. */
    val revenue: Double,
    val averageInvoice: Double,
    val customers: Int
)

fun salesSummary(invoices: List<SalesInvoice>): SalesSummary {
    val confirmed = invoices.filter { it.status == InvoiceStatus.CONFIRMED }
    val revenue = confirmed.sumOf { it.total }
    return SalesSummary(
        invoices = invoices.size,
        confirmed = confirmed.size,
        drafts = invoices.count { it.status == InvoiceStatus.DRAFT },
        cancelled = invoices.count { it.status == InvoiceStatus.CANCELLED },
        revenue = revenue,
        averageInvoice = if (confirmed.isEmpty()) 0.0 else revenue / confirmed.size,
        customers = confirmed.mapNotNull { it.customerName?.takeIf { name -> name.isNotBlank() } }.toSet().size
    )
}

/** بداية اليوم (00:00:00.000) الذي تقع فيه اللحظة [epochMillis]. */
fun startOfDay(epochMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * إجمالي المبيعات المؤكدة في كل يوم من آخر [days] يومًا تنتهي باليوم الذي فيه [now] (الأقدم أولًا). الأيام بلا مبيعات تظهر
 * بصفر ليبقى الرسم متصلًا زمنيًا. الزوج: (بداية اليوم، الإجمالي).
 */
fun dailySalesTotals(invoices: List<SalesInvoice>, days: Int, now: Long): List<Pair<Long, Double>> {
    require(days > 0) { "days must be positive" }
    val byDay = invoices.filter { it.status == InvoiceStatus.CONFIRMED }
        .groupBy { startOfDay(it.invoiceDate) }
        .mapValues { (_, list) -> list.sumOf { it.total } }
    return (days - 1 downTo 0).map { back ->
        val day = Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
            add(Calendar.DAY_OF_YEAR, -back)
        }.timeInMillis
        day to (byDay[day] ?: 0.0)
    }
}

/** مبيعات كل عميل (المؤكدة فقط). العميل النقدي بلا اسم يُجمع تحت «عميل نقدي». */
fun salesByCustomer(invoices: List<SalesInvoice>): Map<String, Double> =
    invoices.filter { it.status == InvoiceStatus.CONFIRMED }
        .groupBy { it.customerName?.takeIf { name -> name.isNotBlank() } ?: "عميل نقدي" }
        .mapValues { (_, list) -> list.sumOf { it.total } }

/** الكمية المباعة لكل صنف (الفواتير المؤكدة فقط). */
fun soldQuantityByProduct(invoices: List<SalesInvoice>): Map<String, Double> =
    invoices.filter { it.status == InvoiceStatus.CONFIRMED }
        .flatMap { it.items }
        .groupBy { it.productName ?: it.itemNameSnapshot ?: "صنف" }
        .mapValues { (_, items) -> items.sumOf { it.quantity } }

// ------------------------------------------------------------------------------------------------------------------
// التحويل والمرتجعات
// ------------------------------------------------------------------------------------------------------------------

data class StockOpsSummary(
    val total: Int,
    val transfers: Int,
    val salesReturns: Int,
    val purchaseReturns: Int,
    val totalQuantity: Double
)

fun stockOpsSummary(operations: List<StockOperation>): StockOpsSummary = StockOpsSummary(
    total = operations.size,
    transfers = operations.count { it.type == StockOperationType.TRANSFER },
    salesReturns = operations.count { it.type == StockOperationType.SALES_RETURN },
    purchaseReturns = operations.count { it.type == StockOperationType.PURCHASE_RETURN },
    totalQuantity = operations.sumOf { op -> op.lines.sumOf { it.quantity } }
)

/** الكمية المتحركة لكل صنف عبر كل العمليات. */
fun movedQuantityByProduct(operations: List<StockOperation>): Map<String, Double> =
    operations.flatMap { it.lines }
        .groupBy { it.productName }
        .mapValues { (_, lines) -> lines.sumOf { it.quantity } }

/** عدد العمليات التي مرّ بها كل فرع (مُرسِلًا أو مستلِمًا). */
fun operationsByBranch(operations: List<StockOperation>): Map<String, Double> =
    operations.flatMap { op -> listOfNotNull(op.branchName, op.toBranchName).distinct() }
        .groupingBy { it }
        .eachCount()
        .mapValues { it.value.toDouble() }
