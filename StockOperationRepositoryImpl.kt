package com.inventorysmartai.app.data.repository

import androidx.room.withTransaction
import com.inventorysmartai.app.data.local.database.InventorySmartDatabase
import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryMovementEntity
import com.inventorysmartai.app.domain.model.MovementType
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.StockOperationRepository
import com.inventorysmartai.app.domain.stockops.OperationMovement
import com.inventorysmartai.app.domain.stockops.StockOperation
import com.inventorysmartai.app.domain.stockops.StockOperationHistory
import com.inventorysmartai.app.domain.stockops.StockOperationPlanner
import com.inventorysmartai.app.domain.stockops.StockOperationRequest
import com.inventorysmartai.app.domain.stockops.StockOperationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * يطبّق التحويل والمرتجعات على الأرصدة بنفس أسلوب فاتورة البيع: التحقق من الرصيد أولًا ثم الكتابة كلها داخل معاملة واحدة،
 * وعبر صف الرصيد الافتراضي (بلا دفعة) الذي تقرأه المبيعات والجرد ولوحة التحكم. المنطق نفسه (تخطيط الحركات وكشف النقص)
 * في [StockOperationPlanner] وهو مغطّى بالاختبارات؛ هنا الكتابة فقط.
 */
@Singleton
class StockOperationRepositoryImpl @Inject constructor(
    private val database: InventorySmartDatabase,
    private val inventoryDao: InventoryDao,
    private val movementDao: InventoryMovementDao,
    private val productDao: ProductDao,
    private val productRepository: ProductRepository,
    private val catalogRepository: CatalogRepository
) : StockOperationRepository {

    override fun observeOperations(): Flow<List<StockOperation>> =
        combine(
            movementDao.observeStockOperationMovements(),
            productRepository.observeProducts(),
            catalogRepository.observeBranches()
        ) { movements, products, branches ->
            val productNames = products.associate { it.id to it.name }
            val branchNames = branches.associate { it.id to it.name }
            StockOperationHistory.group(
                movements = movements.map { it.toOperationMovement() },
                productName = { productNames[it] },
                branchName = { branchNames[it] }
            )
        }

    override suspend fun execute(request: StockOperationRequest): Long = database.withTransaction {
        // Throws InvalidStockOperationException before anything is touched.
        val valid = StockOperationPlanner.validated(request)
        val movements = StockOperationPlanner.movements(valid)
        val now = System.currentTimeMillis()

        // 1) enough stock for what leaves the branch — validate BEFORE writing so a rejected operation leaves no trace.
        val outgoing = StockOperationPlanner.outgoing(valid)
        val available = outgoing.associate { line ->
            line.productId to (inventoryDao.getForProductAndBranch(line.productId, valid.branchId)?.quantity ?: 0.0)
        }
        val shortages = StockOperationPlanner.shortages(outgoing, available)
        if (shortages.isNotEmpty()) {
            // joinToString's lambda is not inline, so the suspend lookup happens in map (inline) first.
            val lines = shortages.map { s ->
                val name = productDao.getById(s.productId)?.name ?: "صنف #${s.productId}"
                "$name: المتاح ${plain(s.available)} والمطلوب ${plain(s.requested)}"
            }
            val message = lines.joinToString("\n")
            throw InsufficientStockException(message)
        }

        // 2) a transfer carries the nearest expiry along, so the destination does not lose the shelf-life of what it received.
        val carriedExpiry: Map<Long, Long?> =
            if (valid.type == StockOperationType.TRANSFER) {
                valid.lines.associate { it.productId to inventoryDao.getForProductAndBranch(it.productId, valid.branchId)?.expiryDate }
            } else emptyMap()

        // 3) apply every movement and record it under one operation number.
        val number = movementDao.maxReferenceId(valid.type.referenceType) + 1
        val notes = StockOperationPlanner.notesFor(valid)
        for (m in movements) {
            val row = inventoryDao.getForProductAndBranch(m.productId, m.branchId)
            val incomingExpiry = if (m.type == MovementType.TRANSFER_IN) carriedExpiry[m.productId] else null
            val expiry = listOfNotNull(row?.expiryDate, incomingExpiry).minOrNull()
            val updated = if (row != null) {
                row.copy(quantity = row.quantity + m.delta, expiryDate = expiry ?: row.expiryDate, updatedAt = now)
            } else {
                InventoryEntity(
                    productId = m.productId,
                    branchId = m.branchId,
                    quantity = m.delta,
                    expiryDate = expiry,
                    createdAt = now,
                    updatedAt = now
                )
            }
            inventoryDao.upsert(updated)
            movementDao.insert(
                InventoryMovementEntity(
                    productId = m.productId,
                    branchId = m.branchId,
                    movementType = m.type.name,
                    quantityChange = m.delta,
                    referenceType = valid.type.referenceType,
                    referenceId = number,
                    notes = notes,
                    createdAt = now
                )
            )
        }
        number
    }

    private fun InventoryMovementEntity.toOperationMovement() = OperationMovement(
        productId = productId,
        branchId = branchId,
        movementType = movementType,
        quantityChange = quantityChange,
        referenceType = referenceType,
        referenceId = referenceId,
        notes = notes,
        createdAt = createdAt
    )

    private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
