package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inventorysmartai.app.data.local.database.entity.CustomerEntity
import com.inventorysmartai.app.data.local.database.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity): Long

    /** Edit in place. NEVER edit through [upsert] (REPLACE): the old row is deleted first, and the
     *  invoices' customerId foreign key is SET_NULL — every invoice of the customer would lose them. */
    @Update
    suspend fun update(customer: CustomerEntity)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("SELECT COUNT(*) FROM sales_invoices WHERE customerId = :id")
    suspend fun invoiceCount(id: Long): Int

    @Query("SELECT * FROM customers WHERE isActive = :active ORDER BY name ASC")
    fun observeByActive(active: Boolean): Flow<List<CustomerEntity>>
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getById(id: Long): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(supplier: SupplierEntity): Long

    /** See [CustomerDao.update]: purchase requests/receipts reference suppliers with SET_NULL. */
    @Update
    suspend fun update(supplier: SupplierEntity)

    @Delete
    suspend fun delete(supplier: SupplierEntity)

    @Query(
        """SELECT (SELECT COUNT(*) FROM purchase_requests WHERE supplierId = :id) +
                  (SELECT COUNT(*) FROM purchase_receipts WHERE supplierId = :id)"""
    )
    suspend fun documentCount(id: Long): Int

    @Query("SELECT * FROM suppliers WHERE isActive = :active ORDER BY name ASC")
    fun observeByActive(active: Boolean): Flow<List<SupplierEntity>>
}
