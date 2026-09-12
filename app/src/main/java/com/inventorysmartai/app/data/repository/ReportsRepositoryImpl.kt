package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.PurchaseDao
import com.inventorysmartai.app.data.local.database.dao.SalesDao
import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.ReportsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsRepositoryImpl @Inject constructor(
    private val productRepository: ProductRepository,
    private val salesDao: SalesDao,
    private val purchaseDao: PurchaseDao
) : ReportsRepository {

    override fun observeInventoryByStatus(): Flow<Map<InventoryStatus, Int>> =
        productRepository.observeProductsWithStock().map { list ->
            InventoryStatus.entries.associateWith { status -> list.count { it.status == status } }
        }

    override fun observeSalesTotalsByDay(daysBack: Int): Flow<List<Pair<Long, Double>>> =
        salesDao.observeAllWithItems().map { invoices ->
            val cutoff = startOfDaysAgo(daysBack)
            invoices
                .filter { it.invoice.invoiceDate >= cutoff }
                .groupBy { startOfDay(it.invoice.invoiceDate) }
                .toSortedMap()
                .map { (day, dayInvoices) -> day to dayInvoices.sumOf { inv -> inv.items.sumOf { it.quantity * it.unitPrice * (1 - it.discountPercent / 100.0) } } }
        }

    override fun observePurchaseTotalsByDay(daysBack: Int): Flow<List<Pair<Long, Double>>> =
        purchaseDao.observeAllWithItems().map { requests ->
            val cutoff = startOfDaysAgo(daysBack)
            requests
                .filter { it.request.requestDate >= cutoff }
                .groupBy { startOfDay(it.request.requestDate) }
                .toSortedMap()
                .map { (day, dayRequests) -> day to dayRequests.sumOf { req -> req.items.sumOf { it.requestedQuantity } } }
        }

    private fun startOfDay(epochMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun startOfDaysAgo(daysBack: Int): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysBack)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
