package com.inventorysmartai.app.domain.model

data class Product(
    val id: Long = 0L,
    val itemNumber: String?,
    val barcode: String?,
    val name: String,
    val alternateNames: String? = null,
    val categoryId: Long?,
    val categoryName: String? = null,
    val unitId: Long?,
    val unitName: String? = null,
    val minStock: Double = 0.0,
    val reorderPoint: Double = 0.0,
    val hasExpiry: Boolean = false,
    val defaultPrice: Double? = null,
    val isActive: Boolean = true
)

/** One branch+batch stock line for a product (the "Branch stock" section of Product Details). */
data class BranchStock(
    val branchId: Long,
    val branchName: String,
    val quantity: Double,
    val batchNumber: String? = null,
    val expiryDate: Long?
)

data class InventoryMovement(
    val id: Long = 0L,
    val productId: Long,
    val branchId: Long,
    val branchName: String? = null,
    val movementType: MovementType,
    val quantityChange: Double,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val notes: String? = null,
    val createdAt: Long
)

/**
 * A product with its stock rolled up across branches plus a computed [status] — the shape both
 * the Inventory list and the Home dashboard aggregation work from, so status logic lives once.
 */
data class ProductStockSummary(
    val product: Product,
    val branchStocks: List<BranchStock>,
    val totalQuantity: Double,
    val nearestExpiryDate: Long?,
    val status: InventoryStatus
)
