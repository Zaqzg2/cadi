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

    @Query("SELECT * FROM inventory WHERE productId = :productId AND branchId = :branchId LIMIT 1")
    suspend fun getForProductAndBranch(productId: Long, branchId: Long): InventoryEntity?

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
