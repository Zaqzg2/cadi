package com.inventorysmartai.app.domain.inventory

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.domain.model.SortOrder

/**
 * Every way the inventory list can be narrowed, as one plain value with no Android or ViewModel dependency.
 *
 * It exists so the Inventory screen and the assistant's `showInventory` tool apply EXACTLY the same rules: when the assistant
 * says "7 أصناف" and then opens the list, the list must show the same 7. The screen's ViewModel keeps its own UI state and maps
 * it onto this class; the tool builds it straight from the model's arguments.
 *
 * [quantityBelow] / [quantityAbove] bound the stock quantity: strictly by default ("أقل من 10" excludes 10), or including the
 * limit when [inclusiveBounds] is true. With a [branchId] the quantity is that branch's stock; otherwise the total over all branches.
 */
data class InventoryListFilter(
    val searchQuery: String = "",
    val itemNumberQuery: String = "",
    val barcodeQuery: String = "",
    val status: InventoryStatusFilter = InventoryStatusFilter.ALL,
    val branchId: Long? = null,
    val categoryId: Long? = null,
    val quantityBelow: Double? = null,
    val quantityAbove: Double? = null,
    val inclusiveBounds: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME_ASC
) {
    val hasQuantityBounds: Boolean get() = quantityBelow != null || quantityAbove != null

    /** The stock quantity this filter looks at for [summary] (one branch's, or the total). */
    fun quantityOf(summary: ProductStockSummary): Double {
        val branch = branchId ?: return summary.totalQuantity
        return summary.branchStocks.filter { it.branchId == branch }.sumOf { it.quantity }
    }

    fun matchesQuantity(quantity: Double): Boolean {
        val below = quantityBelow
        if (below != null) {
            val ok = if (inclusiveBounds) quantity <= below else quantity < below
            if (!ok) return false
        }
        val above = quantityAbove
        if (above != null) {
            val ok = if (inclusiveBounds) quantity >= above else quantity > above
            if (!ok) return false
        }
        return true
    }

    fun filterAndSort(products: List<ProductStockSummary>): List<ProductStockSummary> {
        var result = products
        if (status != InventoryStatusFilter.ALL) {
            result = result.filter { it.status.name == status.name }
        }
        if (searchQuery.isNotBlank()) {
            // Plain text match first (what the box always did), plus the Arabic-normalised one so "السعوديه" finds "السعودية".
            val normalizedQuery = ArabicTextNormalizer.normalize(searchQuery)
            result = result.filter { summary ->
                val name = summary.product.name
                name.contains(searchQuery, ignoreCase = true) ||
                    (normalizedQuery.isNotEmpty() && ArabicTextNormalizer.normalize(name).contains(normalizedQuery))
            }
        }
        if (itemNumberQuery.isNotBlank()) {
            result = result.filter { it.product.itemNumber?.contains(itemNumberQuery, ignoreCase = true) == true }
        }
        if (barcodeQuery.isNotBlank()) {
            result = result.filter { it.product.barcode?.contains(barcodeQuery, ignoreCase = true) == true }
        }
        if (branchId != null) {
            result = result.filter { summary -> summary.branchStocks.any { it.branchId == branchId } }
        }
        if (categoryId != null) {
            result = result.filter { it.product.categoryId == categoryId }
        }
        if (hasQuantityBounds) {
            result = result.filter { matchesQuantity(quantityOf(it)) }
        }
        return when (sortOrder) {
            SortOrder.NAME_ASC -> result.sortedBy { it.product.name }
            SortOrder.NAME_DESC -> result.sortedByDescending { it.product.name }
            SortOrder.QUANTITY_ASC -> result.sortedBy { quantityOf(it) }
            SortOrder.QUANTITY_DESC -> result.sortedByDescending { quantityOf(it) }
        }
    }

    /** A short Arabic phrase for the active quantity bounds ("الرصيد أقل من 10"), or null when there are none — shown as a removable chip. */
    fun quantityLabelAr(): String? {
        val below = quantityBelow
        val above = quantityAbove
        val belowText = below?.let { (if (inclusiveBounds) "أقل من أو يساوي " else "أقل من ") + plainNumber(it) }
        val aboveText = above?.let { (if (inclusiveBounds) "أكبر من أو يساوي " else "أكبر من ") + plainNumber(it) }
        return when {
            belowText != null && aboveText != null -> "الرصيد $aboveText و$belowText"
            belowText != null -> "الرصيد $belowText"
            aboveText != null -> "الرصيد $aboveText"
            else -> null
        }
    }

    companion object {
        /** "10" instead of "10.0"; no digit grouping, so the text parses back to the same number. */
        fun plainNumber(value: Double): String =
            if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble()) value.toLong().toString() else value.toString()
    }
}
