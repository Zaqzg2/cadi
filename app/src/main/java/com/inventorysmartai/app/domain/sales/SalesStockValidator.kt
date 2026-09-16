package com.inventorysmartai.app.domain.sales

import com.inventorysmartai.app.domain.model.SalesInvoiceItem

/**
 * Pure "would this oversell anything" check, extracted out of SalesRepositoryImpl so the spec's
 * required "validate stock" step is unit-testable without a real Room database (see
 * SalesStockValidatorTest). [availableByProduct] is the current branch stock per productId —
 * the repository builds this map from InventoryDao before calling in.
 *
 * Returns a human-readable error per line that would oversell, empty when the invoice is safe to
 * complete. Deliberately returns *all* problems at once rather than failing fast on the first,
 * so the person fixing the invoice sees every affected line in one pass.
 */
object SalesStockValidator {
    fun validate(items: List<SalesInvoiceItem>, availableByProduct: Map<Long, Double>): List<String> =
        items.mapNotNull { item ->
            val available = availableByProduct[item.productId] ?: 0.0
            if (item.quantity > available) {
                val name = item.itemNameSnapshot ?: "#${item.productId}"
                "الكمية المتوفرة من \"$name\" هي $available فقط، والمطلوب ${item.quantity}"
            } else null
        }
}
