package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per (product, branch, batch) — the "current stock" / "branch stock" table.
 *
 * [batchNumber] is nullable for products that aren't batch-tracked. SQLite's UNIQUE index
 * treats every NULL as distinct from every other NULL, so the DB-level constraint alone would
 * happily let two "no batch" rows for the same product+branch coexist. InventoryDao's
 * `findByNaturalKey` query below handles that case explicitly (treating NULL batchNumber as one
 * canonical bucket) — see the spec note "إذا كان batchNumber فارغًا، تعامل معه بشكل صحيح".
 */
@Entity(
    tableName = "inventory",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["productId", "branchId", "batchNumber"], unique = true), Index("branchId")]
)
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Long,
    val branchId: Long,
    val quantity: Double = 0.0,
    val batchNumber: String? = null,
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
