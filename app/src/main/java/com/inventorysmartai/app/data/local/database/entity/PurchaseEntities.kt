package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_requests",
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("supplierId"), Index("branchId"), Index(value = ["requestNumber"], unique = true)]
)
data class PurchaseRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val requestNumber: String,
    val supplierId: Long?,
    val branchId: Long,
    val requestDate: Long,
    val status: String, // PurchaseStatus.name
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/** [receivedQuantity] is a running total kept in sync from PurchaseReceiptItem rows so list
 *  screens don't need to join/sum receipts just to show "3 of 10 received". [approvedQuantity]
 *  is null until an approval decision is made, and can differ from [requestedQuantity] when the
 *  request is PARTIALLY_APPROVED. */
@Entity(
    tableName = "purchase_request_items",
    foreignKeys = [
        ForeignKey(entity = PurchaseRequestEntity::class, parentColumns = ["id"], childColumns = ["purchaseRequestId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("purchaseRequestId"), Index("productId")]
)
data class PurchaseRequestItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val purchaseRequestId: Long,
    val productId: Long,
    val currentStockSnapshot: Double,
    val requestedQuantity: Double,
    val approvedQuantity: Double? = null,
    val receivedQuantity: Double = 0.0,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Records the receiving event itself. [purchaseRequestId] is nullable to leave room for a future
 * direct/ad-hoc receipt not tied to a prior request — the current receiving screen still always
 * ties a receipt to a request, so that path isn't wired up yet, but the schema no longer blocks
 * it. [supplierId]/[branchId] are denormalized snapshots so a receipt is self-describing even if
 * it's ever detached from a request.
 */
@Entity(
    tableName = "purchase_receipts",
    foreignKeys = [
        ForeignKey(entity = PurchaseRequestEntity::class, parentColumns = ["id"], childColumns = ["purchaseRequestId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("purchaseRequestId"), Index("supplierId"), Index("branchId")]
)
data class PurchaseReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val purchaseRequestId: Long?,
    val supplierId: Long?,
    val branchId: Long,
    val receiptNumber: String? = null,
    val receivedDate: Long,
    val receivedBy: String? = null,
    val notes: String? = null,
    val createdAt: Long
)

/** Per-line detail of a receiving event — cost, batch and expiry belong to the *receipt*, not the
 *  request, since two receipts against the same request line can carry different batches. */
@Entity(
    tableName = "purchase_receipt_items",
    foreignKeys = [
        ForeignKey(entity = PurchaseReceiptEntity::class, parentColumns = ["id"], childColumns = ["purchaseReceiptId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("purchaseReceiptId"), Index("productId")]
)
data class PurchaseReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val purchaseReceiptId: Long,
    val productId: Long,
    val quantity: Double,
    val unitCost: Double? = null,
    val batchNumber: String? = null,
    val expiryDate: Long? = null,
    val createdAt: Long
)
