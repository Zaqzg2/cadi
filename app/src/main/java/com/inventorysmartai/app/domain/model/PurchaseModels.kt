package com.inventorysmartai.app.domain.model

data class PurchaseRequest(
    val id: Long = 0L,
    val requestNumber: String,
    val supplierId: Long?,
    val supplierName: String? = null,
    val branchId: Long,
    val branchName: String? = null,
    val requestDate: Long,
    val status: PurchaseStatus,
    val notes: String? = null,
    val items: List<PurchaseRequestItem> = emptyList()
)

/** [receivedQuantity] doubles as the line-level record of a PurchaseReceipt event, so a
 *  receipt header (see [PurchaseReceipt]) doesn't need its own duplicate item rows. */
data class PurchaseRequestItem(
    val id: Long = 0L,
    val purchaseRequestId: Long = 0L,
    val productId: Long,
    val productName: String? = null,
    val currentStockSnapshot: Double,
    val requestedQuantity: Double,
    val receivedQuantity: Double = 0.0,
    val notes: String? = null
)

data class PurchaseReceipt(
    val id: Long = 0L,
    val purchaseRequestId: Long,
    val receiptNumber: String? = null,
    val receivedDate: Long,
    val receivedBy: String? = null,
    val notes: String? = null
)
