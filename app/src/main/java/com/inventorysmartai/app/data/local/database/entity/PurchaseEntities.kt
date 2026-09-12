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

/** [receivedQuantity] is updated when a PurchaseReceipt is logged — no separate item-receipt
 *  table is needed since a receipt is just an event against these same lines. */
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
    val receivedQuantity: Double = 0.0,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/** Records the receiving event itself; line-level received quantities live on the item rows. */
@Entity(
    tableName = "purchase_receipts",
    foreignKeys = [ForeignKey(entity = PurchaseRequestEntity::class, parentColumns = ["id"], childColumns = ["purchaseRequestId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("purchaseRequestId")]
)
data class PurchaseReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val purchaseRequestId: Long,
    val receiptNumber: String? = null,
    val receivedDate: Long,
    val receivedBy: String? = null,
    val notes: String? = null,
    val createdAt: Long
)
