package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceItemEntity
import com.inventorysmartai.app.data.local.database.relation.SalesInvoiceWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesDao {
    @Transaction
    @Query("SELECT * FROM sales_invoices ORDER BY invoiceDate DESC")
    fun observeAllWithItems(): Flow<List<SalesInvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM sales_invoices ORDER BY invoiceDate DESC LIMIT :limit")
    fun observeRecentWithItems(limit: Int): Flow<List<SalesInvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM sales_invoices WHERE id = :invoiceId")
    fun observeWithItems(invoiceId: Long): Flow<SalesInvoiceWithItems?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoice(invoice: SalesInvoiceEntity): Long

    @Query("DELETE FROM sales_invoice_items WHERE salesInvoiceId = :invoiceId")
    suspend fun clearItems(invoiceId: Long)

    @Insert
    suspend fun insertItems(items: List<SalesInvoiceItemEntity>)

    @Query("""
        SELECT COALESCE(SUM(sii.quantity), 0.0) FROM sales_invoice_items sii
        INNER JOIN sales_invoices si ON si.id = sii.salesInvoiceId
        WHERE sii.productId = :productId AND si.invoiceDate BETWEEN :fromDate AND :toDate
    """)
    suspend fun getSoldQuantity(productId: Long, fromDate: Long, toDate: Long): Double
}
