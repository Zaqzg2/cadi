package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory")
    fun observeAll(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE productId = :productId")
    fun observeByProduct(productId: Long): Flow<List<InventoryEntity>>

    /** The default (non-batch-tracked) stock row for this product+branch. Sales, counting and
     *  goal/dashboard math all read/write through this row; batch-specific rows created via
     *  receiving (see [findByNaturalKey]) are tracked separately and shown on Product Details,
     *  but batch-aware "sell from this specific batch" logic is out of scope for this phase. */
    @Query("SELECT * FROM inventory WHERE productId = :productId AND branchId = :branchId AND batchNumber IS NULL LIMIT 1")
    suspend fun getForProductAndBranch(productId: Long, branchId: Long): InventoryEntity?

    /**
     * Batch-aware lookup used when receiving stock with a specific batch number. NULL batch
     * numbers are treated as one canonical bucket (SQLite's UNIQUE index alone would treat every
     * NULL as distinct, which is wrong here — see the entity's own doc comment).
     */
    @Query(
        """SELECT * FROM inventory WHERE productId = :productId AND branchId = :branchId
           AND (batchNumber = :batchNumber OR (batchNumber IS NULL AND :batchNumber IS NULL)) LIMIT 1"""
    )
    suspend fun findByNaturalKey(productId: Long, branchId: Long, batchNumber: String?): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(inventory: InventoryEntity): Long
}

@Dao
interface InventoryMovementDao {
    @Query("SELECT * FROM inventory_movements WHERE productId = :productId ORDER BY createdAt DESC")
    fun observeByProduct(productId: Long): Flow<List<InventoryMovementEntity>>

    @Insert
    suspend fun insert(movement: InventoryMovementEntity): Long
}
