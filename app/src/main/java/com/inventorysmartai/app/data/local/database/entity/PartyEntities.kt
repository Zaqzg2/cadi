package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["customerNumber"], unique = true)]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val customerNumber: String? = null,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val openingBalance: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["supplierNumber"], unique = true)]
)
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val supplierNumber: String? = null,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
