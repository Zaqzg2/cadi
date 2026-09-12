package com.inventorysmartai.app.domain.usecase

import com.inventorysmartai.app.domain.model.AlertSeverity
import com.inventorysmartai.app.domain.model.DashboardAlert
import com.inventorysmartai.app.domain.model.DashboardSummary
import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.repository.CountingRepository
import com.inventorysmartai.app.domain.repository.GoalRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import com.inventorysmartai.app.domain.repository.SalesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Combines five independent streams into the one model the Home dashboard renders. */
class GetDashboardSummaryUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val countingRepository: CountingRepository,
    private val purchaseRepository: PurchaseRepository,
    private val salesRepository: SalesRepository,
    private val goalRepository: GoalRepository
) {
    operator fun invoke(recentLimit: Int): Flow<DashboardSummary> =
        combine(
            productRepository.observeProductsWithStock(),
            countingRepository.observeRecentCounts(recentLimit),
            purchaseRepository.observeRecentRequests(recentLimit),
            salesRepository.observeRecentInvoices(recentLimit),
            goalRepository.observeOverallAchievementPercent()
        ) { productsWithStock, recentCounts, recentPurchases, recentSales, achievementPercent ->
            val lowStock = productsWithStock.count { it.status == InventoryStatus.LOW }
            val zeroStock = productsWithStock.count { it.status == InventoryStatus.ZERO }
            val nearExpiry = productsWithStock.count { it.status == InventoryStatus.NEAR_EXPIRY }
            val expired = productsWithStock.count { it.status == InventoryStatus.EXPIRED }

            val alerts = buildList {
                if (expired > 0) add(DashboardAlert("expired", AlertSeverity.CRITICAL, "$expired صنفاً منتهي الصلاحية"))
                if (zeroStock > 0) add(DashboardAlert("zero_stock", AlertSeverity.CRITICAL, "$zeroStock صنفاً بدون مخزون"))
                if (nearExpiry > 0) add(DashboardAlert("near_expiry", AlertSeverity.WARNING, "$nearExpiry صنفاً قريب من الانتهاء"))
                if (lowStock > 0) add(DashboardAlert("low_stock", AlertSeverity.WARNING, "$lowStock صنفاً منخفض المخزون"))
            }

            DashboardSummary(
                totalItems = productsWithStock.size,
                totalQuantity = productsWithStock.sumOf { it.totalQuantity },
                lowStockCount = lowStock,
                zeroStockCount = zeroStock,
                nearExpiryCount = nearExpiry,
                expiredCount = expired,
                goalsAchievementPercent = achievementPercent,
                recentCounts = recentCounts,
                recentPurchaseRequests = recentPurchases,
                recentSalesInvoices = recentSales,
                alerts = alerts
            )
        }
}
