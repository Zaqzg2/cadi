package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index("categoryId"),
        Index("unitId"),
        Index(value = ["barcode"]),
        Index(value = ["itemNumber"], unique = true)
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemNumber: String,
    val barcode: String?,
    val name: String,
    val categoryId: Long?,
    val unitId: Long?,
    val minStock: Double = 0.0,
    val reorderPoint: Double = 0.0,
    val hasExpiry: Boolean = false,
    val defaultPrice: Double? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
