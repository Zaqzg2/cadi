package com.inventorysmartai.app.data.repository

import androidx.room.withTransaction
import com.inventorysmartai.app.data.local.database.InventorySmartDatabase
import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.PurchaseDao
import com.inventorysmartai.app.data.local.database.dao.SupplierDao
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryMovementEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseReceiptEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseReceiptItemEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestItemEntity
import com.inventorysmartai.app.data.local.database.relation.PurchaseRequestWithItems
import com.inventorysmartai.app.domain.model.MovementType
import com.inventorysmartai.app.domain.model.PurchaseReceipt
import com.inventorysmartai.app.domain.model.PurchaseReceiptLine
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
    private val database: InventorySmartDatabase,
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

    override suspend fun saveRequest(request: PurchaseRequest): Long = database.withTransaction {
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
                    approvedQuantity = item.approvedQuantity,
                    receivedQuantity = item.receivedQuantity,
                    notes = item.notes,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
        requestId
    }

    /**
     * Logs a receiving event and — this is real behavior, not a stub — actually moves stock:
     * each line's branch inventory is increased (batch-aware when [PurchaseReceiptLine.batchNumber]
     * is given) and an InventoryMovement(PURCHASE_IN) row is written, so Inventory/Reports reflect
     * it immediately. The whole thing runs in one DB transaction — a crash partway through must
     * never leave a receipt logged without its stock effect, or vice versa.
     */
    override suspend fun receive(receipt: PurchaseReceipt, lines: List<PurchaseReceiptLine>): Unit = database.withTransaction {
        val now = System.currentTimeMillis()
        val requestId = receipt.purchaseRequestId
        val current = requestId?.let { purchaseDao.observeWithItems(it).first() }
        val branchId = receipt.branchId

        val receiptId = purchaseDao.insertReceipt(
            PurchaseReceiptEntity(
                purchaseRequestId = requestId,
                supplierId = receipt.supplierId ?: current?.request?.supplierId,
                branchId = branchId,
                receiptNumber = receipt.receiptNumber,
                receivedDate = receipt.receivedDate,
                receivedBy = receipt.receivedBy,
                notes = receipt.notes,
                createdAt = now
            )
        )

        purchaseDao.insertReceiptItems(
            lines.filter { it.quantity > 0.0 }.map { line ->
                PurchaseReceiptItemEntity(
                    purchaseReceiptId = receiptId,
                    productId = line.productId,
                    quantity = line.quantity,
                    unitCost = line.unitCost,
                    batchNumber = line.batchNumber,
                    expiryDate = line.expiryDate,
                    createdAt = now
                )
            }
        )

        if (current != null) {
            val receivedByProduct = lines.filter { it.quantity > 0.0 }.groupBy { it.productId }
                .mapValues { (_, group) -> group.sumOf { it.quantity } }
            val updatedItems = current.items.map { item ->
                val receivedNow = receivedByProduct[item.productId] ?: 0.0
                item.copy(receivedQuantity = item.receivedQuantity + receivedNow, updatedAt = now)
            }
            purchaseDao.updateItems(updatedItems)
        }

        lines.forEach { line ->
            if (line.quantity <= 0.0) return@forEach
            val existingStock = inventoryDao.findByNaturalKey(line.productId, branchId, line.batchNumber)
            inventoryDao.upsert(
                InventoryEntity(
                    id = existingStock?.id ?: 0L,
                    productId = line.productId,
                    branchId = branchId,
                    quantity = (existingStock?.quantity ?: 0.0) + line.quantity,
                    batchNumber = line.batchNumber,
                    expiryDate = line.expiryDate ?: existingStock?.expiryDate,
                    createdAt = existingStock?.createdAt ?: now,
                    updatedAt = now
                )
            )
            inventoryMovementDao.insert(
                InventoryMovementEntity(
                    productId = line.productId,
                    branchId = branchId,
                    movementType = MovementType.PURCHASE_IN.name,
                    quantityChange = line.quantity,
                    referenceType = "PURCHASE_RECEIPT",
                    referenceId = receiptId,
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
                approvedQuantity = item.approvedQuantity,
                receivedQuantity = item.receivedQuantity,
                notes = item.notes
            )
        }
    )
}
