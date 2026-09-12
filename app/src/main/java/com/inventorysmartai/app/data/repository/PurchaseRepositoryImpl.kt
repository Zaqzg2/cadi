package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.PurchaseDao
import com.inventorysmartai.app.data.local.database.dao.SupplierDao
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryMovementEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseReceiptEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestItemEntity
import com.inventorysmartai.app.data.local.database.relation.PurchaseRequestWithItems
import com.inventorysmartai.app.domain.model.MovementType
import com.inventorysmartai.app.domain.model.PurchaseReceipt
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.model.PurchaseRequestItem
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepositoryImpl @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao,
    private val supplierDao: SupplierDao,
    private val inventoryDao: InventoryDao,
    private val inventoryMovementDao: InventoryMovementDao
) : PurchaseRepository {

    override fun observeRequests(): Flow<List<PurchaseRequest>> =
        purchaseDao.observeAllWithItems().map { list -> list.map { it.toDomain(productDao, supplierDao) } }

    override fun observeRecentRequests(limit: Int): Flow<List<PurchaseRequest>> =
        purchaseDao.observeRecentWithItems(limit).map { list -> list.map { it.toDomain(productDao, supplierDao) } }

    override fun observeRequest(requestId: Long): Flow<PurchaseRequest?> =
        purchaseDao.observeWithItems(requestId).map { it?.toDomain(productDao, supplierDao) }

    override suspend fun saveRequest(request: PurchaseRequest): Long {
        val now = System.currentTimeMillis()
        val requestId = purchaseDao.upsertRequest(
            PurchaseRequestEntity(
                id = request.id,
                requestNumber = request.requestNumber,
                supplierId = request.supplierId,
                branchId = request.branchId,
                requestDate = request.requestDate,
                status = request.status.name,
                notes = request.notes,
                createdAt = now,
                updatedAt = now
            )
        )
        purchaseDao.clearItems(requestId)
        purchaseDao.insertItems(
            request.items.map { item ->
                PurchaseRequestItemEntity(
                    purchaseRequestId = requestId,
                    productId = item.productId,
                    currentStockSnapshot = item.currentStockSnapshot,
                    requestedQuantity = item.requestedQuantity,
                    receivedQuantity = item.receivedQuantity,
                    notes = item.notes,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
        return requestId
    }

    /**
     * Logs a receiving event and — this is real behavior, not a stub — actually moves stock:
     * each product's branch inventory is increased by the received quantity and an
     * InventoryMovement(PURCHASE_IN) row is written, so Inventory/Reports reflect it immediately.
     */
    override suspend fun receive(receipt: PurchaseReceipt, receivedQuantities: Map<Long, Double>) {
        val now = System.currentTimeMillis()
        val current = purchaseDao.observeWithItems(receipt.purchaseRequestId).first() ?: return
        val branchId = current.request.branchId

        purchaseDao.insertReceipt(
            PurchaseReceiptEntity(
                purchaseRequestId = receipt.purchaseRequestId,
                receiptNumber = receipt.receiptNumber,
                receivedDate = receipt.receivedDate,
                receivedBy = receipt.receivedBy,
                notes = receipt.notes,
                createdAt = now
            )
        )

        val updatedItems = current.items.map { item ->
            val receivedNow = receivedQuantities[item.productId] ?: 0.0
            item.copy(receivedQuantity = item.receivedQuantity + receivedNow, updatedAt = now)
        }
        purchaseDao.updateItems(updatedItems)

        receivedQuantities.forEach { (productId, receivedNow) ->
            if (receivedNow <= 0.0) return@forEach
            val existingStock = inventoryDao.getForProductAndBranch(productId, branchId)
            inventoryDao.upsert(
                InventoryEntity(
                    id = existingStock?.id ?: 0L,
                    productId = productId,
                    branchId = branchId,
                    quantity = (existingStock?.quantity ?: 0.0) + receivedNow,
                    expiryDate = existingStock?.expiryDate,
                    createdAt = existingStock?.createdAt ?: now,
                    updatedAt = now
                )
            )
            inventoryMovementDao.insert(
                InventoryMovementEntity(
                    productId = productId,
                    branchId = branchId,
                    movementType = MovementType.PURCHASE_IN.name,
                    quantityChange = receivedNow,
                    referenceType = "PURCHASE_REQUEST",
                    referenceId = receipt.purchaseRequestId,
                    notes = receipt.notes,
                    createdAt = now
                )
            )
        }
    }
}

private suspend fun PurchaseRequestWithItems.toDomain(productDao: ProductDao, supplierDao: SupplierDao): PurchaseRequest {
    val supplierName = request.supplierId?.let { supplierDao.getById(it)?.name }
    return PurchaseRequest(
        id = request.id,
        requestNumber = request.requestNumber,
        supplierId = request.supplierId,
        supplierName = supplierName,
        branchId = request.branchId,
        requestDate = request.requestDate,
        status = PurchaseStatus.valueOf(request.status),
        notes = request.notes,
        items = items.map { item ->
            PurchaseRequestItem(
                id = item.id,
                purchaseRequestId = item.purchaseRequestId,
                productId = item.productId,
                productName = productDao.getById(item.productId)?.name,
                currentStockSnapshot = item.currentStockSnapshot,
                requestedQuantity = item.requestedQuantity,
                receivedQuantity = item.receivedQuantity,
                notes = item.notes
            )
        }
    )
}
