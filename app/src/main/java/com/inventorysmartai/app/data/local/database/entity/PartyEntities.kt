package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
