package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_counts",
    foreignKeys = [ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("branchId")]
)
data class InventoryCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val branchId: Long,
    val countDate: Long,
    val status: String, // CountStatus.name
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Line items for a count session. Not explicitly named in the original entity list, but
 * required by "Items / System quantity / Actual quantity / Difference" needing its own table
 * per the "proper Room relationships, not one table" instruction — the same shape already
 * used explicitly for PurchaseRequestItem and SalesInvoiceItem.
 */
@Entity(
    tableName = "inventory_count_items",
    foreignKeys = [
        ForeignKey(entity = InventoryCountEntity::class, parentColumns = ["id"], childColumns = ["countId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("countId"), Index("productId")]
)
data class InventoryCountItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val countId: Long,
    val productId: Long,
    val systemQuantity: Double,
    val actualQuantity: Double,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
