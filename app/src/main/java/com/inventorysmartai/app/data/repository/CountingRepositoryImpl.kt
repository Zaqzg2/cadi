package com.inventorysmartai.app.data.repository

import androidx.room.withTransaction
import com.inventorysmartai.app.data.local.database.InventorySmartDatabase
import com.inventorysmartai.app.data.local.database.dao.CountingDao
import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.data.local.database.entity.InventoryCountEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountItemEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryMovementEntity
import com.inventorysmartai.app.data.local.database.relation.InventoryCountWithItems
import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.domain.model.InventoryCount
import com.inventorysmartai.app.domain.model.InventoryCountItem
import com.inventorysmartai.app.domain.model.MovementType
import com.inventorysmartai.app.domain.repository.CountingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountingRepositoryImpl @Inject constructor(
    private val database: InventorySmartDatabase,
    private val countingDao: CountingDao,
    private val inventoryDao: InventoryDao,
    private val inventoryMovementDao: InventoryMovementDao
) : CountingRepository {

    override fun observeCounts(): Flow<List<InventoryCount>> =
        countingDao.observeAllWithItems().map { list -> list.map { it.toDomain() } }

    override fun observeRecentCounts(limit: Int): Flow<List<InventoryCount>> =
        countingDao.observeRecentWithItems(limit).map { list -> list.map { it.toDomain() } }

    override fun observeCount(countId: Long): Flow<InventoryCount?> =
        countingDao.observeWithItems(countId).map { it?.toDomain() }

    /**
     * Saving a COMPLETED count is real behavior, not bookkeeping: any item whose actual count
     * differs from the system quantity has its branch stock corrected and a
     * COUNT_ADJUSTMENT movement written, exactly like Purchases/Sales already do for their
     * own stock-affecting actions. Runs in one DB transaction so the count record and its stock
     * corrections can never end up out of sync if the process is interrupted partway through.
     */
    override suspend fun saveCount(count: InventoryCount): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val countId = countingDao.upsertCount(
            InventoryCountEntity(
                id = count.id,
                branchId = count.branchId,
                countDate = count.countDate,
                status = count.status.name,
                notes = count.notes,
                createdAt = now,
                updatedAt = now
            )
        )
        countingDao.clearItems(countId)
        countingDao.insertItems(
            count.items.map { item ->
                InventoryCountItemEntity(
                    countId = countId,
                    productId = item.productId,
                    systemQuantity = item.systemQuantity,
                    actualQuantity = item.actualQuantity,
                    notes = item.notes,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )

        if (count.status == CountStatus.COMPLETED) {
            count.items.filter { it.difference != 0.0 }.forEach { item ->
                val existingStock = inventoryDao.getForProductAndBranch(item.productId, count.branchId)
                inventoryDao.upsert(
                    InventoryEntity(
                        id = existingStock?.id ?: 0L,
                        productId = item.productId,
                        branchId = count.branchId,
                        quantity = item.actualQuantity,
                        batchNumber = null,
                        expiryDate = existingStock?.expiryDate,
                        createdAt = existingStock?.createdAt ?: now,
                        updatedAt = now
                    )
                )
                inventoryMovementDao.insert(
                    InventoryMovementEntity(
                        productId = item.productId,
                        branchId = count.branchId,
                        movementType = MovementType.COUNT_ADJUSTMENT.name,
                        quantityChange = item.difference,
                        referenceType = "INVENTORY_COUNT",
                        referenceId = countId,
                        notes = item.notes,
                        createdAt = now
                    )
                )
            }
        }
        countId
    }
}

private fun InventoryCountWithItems.toDomain() = InventoryCount(
    id = count.id,
    branchId = count.branchId,
    countDate = count.countDate,
    status = CountStatus.valueOf(count.status),
    notes = count.notes,
    items = items.map {
        InventoryCountItem(
            id = it.id,
            countId = it.countId,
            productId = it.productId,
            systemQuantity = it.systemQuantity,
            actualQuantity = it.actualQuantity,
            notes = it.notes
        )
    }
)
