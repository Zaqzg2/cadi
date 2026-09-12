package com.inventorysmartai.app.domain.model

data class DashboardSummary(
    val totalItems: Int,
    val totalQuantity: Double,
    val lowStockCount: Int,
    val zeroStockCount: Int,
    val nearExpiryCount: Int,
    val expiredCount: Int,
    val goalsAchievementPercent: Double,
    val recentCounts: List<InventoryCount>,
    val recentPurchaseRequests: List<PurchaseRequest>,
    val recentSalesInvoices: List<SalesInvoice>,
    val alerts: List<DashboardAlert>
)

data class DashboardAlert(
    val id: String,
    val severity: AlertSeverity,
    val message: String
)
