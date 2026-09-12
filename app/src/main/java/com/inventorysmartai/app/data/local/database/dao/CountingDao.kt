package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.inventorysmartai.app.data.local.database.entity.InventoryCountEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountItemEntity
import com.inventorysmartai.app.data.local.database.relation.InventoryCountWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface CountingDao {
    @Transaction
    @Query("SELECT * FROM inventory_counts ORDER BY countDate DESC")
    fun observeAllWithItems(): Flow<List<InventoryCountWithItems>>

    @Transaction
    @Query("SELECT * FROM inventory_counts ORDER BY countDate DESC LIMIT :limit")
    fun observeRecentWithItems(limit: Int): Flow<List<InventoryCountWithItems>>

    @Transaction
    @Query("SELECT * FROM inventory_counts WHERE id = :countId")
    fun observeWithItems(countId: Long): Flow<InventoryCountWithItems?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCount(count: InventoryCountEntity): Long

    @Query("DELETE FROM inventory_count_items WHERE countId = :countId")
    suspend fun clearItems(countId: Long)

    @Insert
    suspend fun insertItems(items: List<InventoryCountItemEntity>)
}
