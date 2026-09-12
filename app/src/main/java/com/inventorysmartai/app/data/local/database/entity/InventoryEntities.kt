package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per (product, branch) — the "current stock" / "branch stock" table. */
@Entity(
    tableName = "inventory",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["productId", "branchId"], unique = true), Index("branchId")]
)
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Long,
    val branchId: Long,
    val quantity: Double = 0.0,
    val expiryDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/** An audit trail of every stock change — sale, purchase receipt, count adjustment, manual. */
@Entity(
    tableName = "inventory_movements",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("productId"), Index("branchId")]
)
data class InventoryMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Long,
    val branchId: Long,
    val movementType: String, // MovementType.name
    val quantityChange: Double,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val notes: String? = null,
    val createdAt: Long
)
