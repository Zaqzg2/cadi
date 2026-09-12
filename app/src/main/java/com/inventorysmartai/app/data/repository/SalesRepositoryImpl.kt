package com.inventorysmartai.app.data.repository

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesRepositoryImpl @Inject constructor(
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
     * Saving a *new* invoice (id == 0) deducts stock and writes a SALE_OUT movement per line —
     * this is real inventory behavior, not just bookkeeping. Editing an existing invoice only
     * rewrites its lines; Phase 1 does not reverse/re-apply stock on edits (see delivery notes).
     */
    override suspend fun saveInvoice(invoice: SalesInvoice): Long {
        val now = System.currentTimeMillis()
        val isNew = invoice.id == 0L
        val invoiceId = salesDao.upsertInvoice(
            SalesInvoiceEntity(
                id = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                invoiceDate = invoice.invoiceDate,
                customerId = invoice.customerId,
                branchId = invoice.branchId,
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
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    discountPercent = item.discountPercent,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )

        if (isNew) {
            invoice.items.forEach { item ->
                val existingStock = inventoryDao.getForProductAndBranch(item.productId, invoice.branchId)
                inventoryDao.upsert(
                    InventoryEntity(
                        id = existingStock?.id ?: 0L,
                        productId = item.productId,
                        branchId = invoice.branchId,
                        quantity = (existingStock?.quantity ?: 0.0) - item.quantity,
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
        return invoiceId
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
        notes = invoice.notes,
        status = InvoiceStatus.valueOf(invoice.status),
        items = items.map { item ->
            SalesInvoiceItem(
                id = item.id,
                salesInvoiceId = item.salesInvoiceId,
                productId = item.productId,
                productName = productDao.getById(item.productId)?.name,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                discountPercent = item.discountPercent
            )
        }
    )
}
