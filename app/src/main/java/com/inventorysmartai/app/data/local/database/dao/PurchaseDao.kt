package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.inventorysmartai.app.data.local.database.entity.PurchaseReceiptEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseReceiptItemEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestItemEntity
import com.inventorysmartai.app.data.local.database.relation.PurchaseRequestWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Transaction
    @Query("SELECT * FROM purchase_requests ORDER BY requestDate DESC")
    fun observeAllWithItems(): Flow<List<PurchaseRequestWithItems>>

    @Transaction
    @Query("SELECT * FROM purchase_requests ORDER BY requestDate DESC LIMIT :limit")
    fun observeRecentWithItems(limit: Int): Flow<List<PurchaseRequestWithItems>>

    @Transaction
    @Query("SELECT * FROM purchase_requests WHERE id = :requestId")
    fun observeWithItems(requestId: Long): Flow<PurchaseRequestWithItems?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequest(request: PurchaseRequestEntity): Long

    @Query("DELETE FROM purchase_request_items WHERE purchaseRequestId = :requestId")
    suspend fun clearItems(requestId: Long)

    @Insert
    suspend fun insertItems(items: List<PurchaseRequestItemEntity>): List<Long>

    @Update
    suspend fun updateItems(items: List<PurchaseRequestItemEntity>)

    @Query("SELECT * FROM purchase_request_items WHERE purchaseRequestId = :requestId")
    suspend fun getItems(requestId: Long): List<PurchaseRequestItemEntity>

    @Insert
    suspend fun insertReceipt(receipt: PurchaseReceiptEntity): Long

    @Insert
    suspend fun insertReceiptItems(items: List<PurchaseReceiptItemEntity>): List<Long>

    @Query("SELECT * FROM purchase_receipt_items WHERE purchaseReceiptId = :receiptId")
    suspend fun getReceiptItems(receiptId: Long): List<PurchaseReceiptItemEntity>
}
