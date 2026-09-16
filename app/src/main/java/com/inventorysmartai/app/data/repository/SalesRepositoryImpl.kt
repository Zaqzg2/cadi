package com.inventorysmartai.app.data.repository

import androidx.room.withTransaction
import com.inventorysmartai.app.data.local.database.InventorySmartDatabase
import com.inventorysmartai.app.data.local.database.dao.CustomerDao
import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.SalesDao
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryMovementEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceItemEntity
import com.inventorysmartai.app.data.local.database.relation.SalesInvoiceWithItems
import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.domain.model.MovementType
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.model.SalesInvoiceItem
import com.inventorysmartai.app.domain.repository.SalesRepository
import com.inventorysmartai.app.domain.sales.SalesStockValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown by [SalesRepositoryImpl.saveInvoice] when completing an invoice would take a
 *  product's branch stock below zero. Caught by the ViewModel and shown to the user — never
 *  silently allowed, per the spec's "validate stock" step for completing a sale. */
class InsufficientStockException(message: String) : Exception(message)

@Singleton
class SalesRepositoryImpl @Inject constructor(
    private val database: InventorySmartDatabase,
    private val salesDao: SalesDao,
    private val productDao: ProductDao,
    private val customerDao: CustomerDao,
    private val inventoryDao: InventoryDao,
    private val inventoryMovementDao: InventoryMovementDao
) : SalesRepository {

    override fun observeInvoices(): Flow<List<SalesInvoice>> =
        salesDao.observeAllWithItems().map { list -> list.map { it.toDomain(productDao, customerDao) } }

    override fun observeRecentInvoices(limit: Int): Flow<List<SalesInvoice>> =
        salesDao.observeRecentWithItems(limit).map { list -> list.map { it.toDomain(productDao, customerDao) } }

    override fun observeInvoice(invoiceId: Long): Flow<SalesInvoice?> =
        salesDao.observeWithItems(invoiceId).map { it?.toDomain(productDao, customerDao) }

    /**
     * DRAFT saves just persist the lines — no stock effect, so drafting an invoice never touches
     * inventory. Stock is only validated and applied when the invoice *becomes* CONFIRMED (a new
     * invoice saved directly as CONFIRMED, or an existing DRAFT transitioning to CONFIRMED) —
     * re-saving an already-CONFIRMED invoice does not re-apply the movement a second time.
     * Everything here — the invoice write, the item write, the stock check, the inventory update
     * and the movement — runs inside one DB transaction.
     */
    override suspend fun saveInvoice(invoice: SalesInvoice): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val previousStatus = if (invoice.id != 0L) salesDao.getById(invoice.id)?.status?.let(InvoiceStatus::valueOf) else null
        val isCompleting = invoice.status == InvoiceStatus.CONFIRMED && previousStatus != InvoiceStatus.CONFIRMED

        if (isCompleting) {
            // Validate stock BEFORE writing anything — a rejected invoice must leave no trace.
            val availableByProduct = invoice.items.associate { item ->
                item.productId to (inventoryDao.getForProductAndBranch(item.productId, invoice.branchId)?.quantity ?: 0.0)
            }
            val problems = SalesStockValidator.validate(invoice.items, availableByProduct)
            if (problems.isNotEmpty()) throw InsufficientStockException(problems.joinToString("\n"))
        }

        val discountTotal = invoice.discountTotal
        val invoiceTotal = invoice.total
        val finalBalance = invoice.previousBalance?.let { it + invoiceTotal }

        val invoiceId = salesDao.upsertInvoice(
            SalesInvoiceEntity(
                id = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                invoiceDate = invoice.invoiceDate,
                customerId = invoice.customerId,
                branchId = invoice.branchId,
                warehouse = invoice.warehouse,
                currency = invoice.currency,
                previousBalance = invoice.previousBalance,
                invoiceTotal = invoiceTotal,
                discountTotal = discountTotal,
                finalBalance = finalBalance,
                notes = invoice.notes,
                status = invoice.status.name,
                createdAt = now,
                updatedAt = now
            )
        )
        salesDao.clearItems(invoiceId)
        salesDao.insertItems(
            invoice.items.map { item ->
                SalesInvoiceItemEntity(
                    salesInvoiceId = invoiceId,
                    productId = item.productId,
                    itemNumberSnapshot = item.itemNumberSnapshot,
                    itemNameSnapshot = item.itemNameSnapshot ?: item.productName ?: "",
                    unitSnapshot = item.unitSnapshot,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    discountPercent = item.discountPercent,
                    total = item.lineTotal,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )

        if (isCompleting) {
            invoice.items.forEach { item ->
                val existingStock = inventoryDao.getForProductAndBranch(item.productId, invoice.branchId)
                inventoryDao.upsert(
                    InventoryEntity(
                        id = existingStock?.id ?: 0L,
                        productId = item.productId,
                        branchId = invoice.branchId,
                        quantity = (existingStock?.quantity ?: 0.0) - item.quantity,
                        batchNumber = null,
                        expiryDate = existingStock?.expiryDate,
                        createdAt = existingStock?.createdAt ?: now,
                        updatedAt = now
                    )
                )
                inventoryMovementDao.insert(
                    InventoryMovementEntity(
                        productId = item.productId,
                        branchId = invoice.branchId,
                        movementType = MovementType.SALE_OUT.name,
                        quantityChange = -item.quantity,
                        referenceType = "SALES_INVOICE",
                        referenceId = invoiceId,
                        notes = invoice.notes,
                        createdAt = now
                    )
                )
            }
        }
        invoiceId
    }

    override suspend fun getSoldQuantity(productId: Long, fromDate: Long, toDate: Long): Double =
        salesDao.getSoldQuantity(productId, fromDate, toDate)
}

private suspend fun SalesInvoiceWithItems.toDomain(productDao: ProductDao, customerDao: CustomerDao): SalesInvoice {
    val customerName = invoice.customerId?.let { customerDao.getById(it)?.name }
    return SalesInvoice(
        id = invoice.id,
        invoiceNumber = invoice.invoiceNumber,
        invoiceDate = invoice.invoiceDate,
        customerId = invoice.customerId,
        customerName = customerName,
        branchId = invoice.branchId,
        warehouse = invoice.warehouse,
        currency = invoice.currency,
        previousBalance = invoice.previousBalance,
        finalBalance = invoice.finalBalance,
        notes = invoice.notes,
        status = InvoiceStatus.valueOf(invoice.status),
        items = items.map { item ->
            SalesInvoiceItem(
                id = item.id,
                salesInvoiceId = item.salesInvoiceId,
                productId = item.productId,
                productName = productDao.getById(item.productId)?.name,
                itemNumberSnapshot = item.itemNumberSnapshot,
                itemNameSnapshot = item.itemNameSnapshot,
                unitSnapshot = item.unitSnapshot,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                discountPercent = item.discountPercent
            )
        }
    )
}
