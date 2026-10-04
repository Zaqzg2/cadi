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

    @Query("SELECT * FROM purchase_requests WHERE id = :id")
    suspend fun getRequestById(id: Long): PurchaseRequestEntity?

    /** Status-only update: does NOT touch the lines (re-saving the lines is what used to wipe the
     *  received quantities that `receive()` had just written). */
    @Query("UPDATE purchase_requests SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long)

    @Query("DELETE FROM purchase_requests WHERE id = :id")
    suspend fun deleteRequest(id: Long)

    /** Receipt lines recorded against this request. >0 means stock was already added through it. */
    @Query(
        """SELECT COUNT(*) FROM purchase_receipt_items pri
           INNER JOIN purchase_receipts pr ON pr.id = pri.purchaseReceiptId
           WHERE pr.purchaseRequestId = :requestId"""
    )
    suspend fun receiptLineCount(requestId: Long): Int

    /** The TRUE received total per product, summed from the receipts themselves. */
    @Query(
        """SELECT pri.productId AS productId, SUM(pri.quantity) AS total FROM purchase_receipt_items pri
           INNER JOIN purchase_receipts pr ON pr.id = pri.purchaseReceiptId
           WHERE pr.purchaseRequestId = :requestId GROUP BY pri.productId"""
    )
    suspend fun receivedByProduct(requestId: Long): List<ProductQuantity>
}

data class ProductQuantity(val productId: Long, val total: Double)
