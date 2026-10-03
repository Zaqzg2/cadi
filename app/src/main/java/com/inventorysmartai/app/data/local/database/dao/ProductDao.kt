package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inventorysmartai.app.data.local.database.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE itemNumber = :itemNumber LIMIT 1")
    suspend fun getByItemNumber(itemNumber: String): ProductEntity?

    @Query("SELECT * FROM products WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)

    /** Lines in counts / purchase requests / receipts / sales invoices that point at this product.
     *  Those foreign keys are RESTRICT, so a product with any reference cannot be hard-deleted. */
    @Query(
        """SELECT
            (SELECT COUNT(*) FROM inventory_count_items WHERE productId = :id) +
            (SELECT COUNT(*) FROM purchase_request_items WHERE productId = :id) +
            (SELECT COUNT(*) FROM purchase_receipt_items WHERE productId = :id) +
            (SELECT COUNT(*) FROM sales_invoice_items WHERE productId = :id)"""
    )
    suspend fun documentReferenceCount(id: Long): Int

    @Query("UPDATE products SET isActive = :active, updatedAt = :now WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, now: Long)
}
