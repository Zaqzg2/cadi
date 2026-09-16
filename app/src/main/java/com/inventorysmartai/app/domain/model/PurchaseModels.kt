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

/** [receivedQuantity] is a running total kept in sync from PurchaseReceiptItem rows (see
 *  [PurchaseReceiptLine]) so list screens can show "3 of 10 received" without summing receipts.
 *  [approvedQuantity] stays null until an approval decision is made, and can differ from
 *  [requestedQuantity] when the request is PARTIALLY_APPROVED. */
data class PurchaseRequestItem(
    val id: Long = 0L,
    val purchaseRequestId: Long = 0L,
    val productId: Long,
    val productName: String? = null,
    val currentStockSnapshot: Double,
    val requestedQuantity: Double,
    val approvedQuantity: Double? = null,
    val receivedQuantity: Double = 0.0,
    val notes: String? = null
)

/** The receiving event header. [purchaseRequestId] is nullable to leave room for a future
 *  ad-hoc receipt not tied to a request (not wired up yet — see the "Phase 2" section of README.md). */
data class PurchaseReceipt(
    val id: Long = 0L,
    val purchaseRequestId: Long?,
    val supplierId: Long? = null,
    val branchId: Long,
    val receiptNumber: String? = null,
    val receivedDate: Long,
    val receivedBy: String? = null,
    val notes: String? = null
)

/**
 * One line of a receiving event. [unitCost]/[batchNumber]/[expiryDate] are all nullable — the
 * current receiving screen only collects [quantity], so callers that don't have the other three
 * yet should simply pass null; the data layer is ready for a future UI that does collect them.
 */
data class PurchaseReceiptLine(
    val productId: Long,
    val quantity: Double,
    val unitCost: Double? = null,
    val batchNumber: String? = null,
    val expiryDate: Long? = null
)
