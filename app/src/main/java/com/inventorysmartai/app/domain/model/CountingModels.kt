package com.inventorysmartai.app.domain.model

data class InventoryCount(
    val id: Long = 0L,
    val branchId: Long,
    val branchName: String? = null,
    val countDate: Long,
    val status: CountStatus,
    val notes: String? = null,
    val items: List<InventoryCountItem> = emptyList()
)

data class InventoryCountItem(
    val id: Long = 0L,
    val countId: Long = 0L,
    val productId: Long,
    val productName: String? = null,
    val systemQuantity: Double,
    val actualQuantity: Double,
    val notes: String? = null
) {
    val difference: Double get() = actualQuantity - systemQuantity
}
