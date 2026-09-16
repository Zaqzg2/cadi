package com.inventorysmartai.app.domain.model

/**
 * [total]/[discountTotal] are computed from [items] here in the domain layer, then persisted as
 * stored columns on SalesInvoiceEntity at save time (see SalesRepositoryImpl) — the invoice
 * itself is the single source of truth while being edited, but once saved the stored values are
 * what reports/history read, so they stay correct even if prices or line items change later.
 */
data class SalesInvoice(
    val id: Long = 0L,
    val invoiceNumber: String,
    val invoiceDate: Long,
    val customerId: Long?,
    val customerName: String? = null,
    val branchId: Long,
    val branchName: String? = null,
    val warehouse: String? = null,
    val currency: String = "",
    val previousBalance: Double? = null,
    val finalBalance: Double? = null,
    val notes: String? = null,
    val status: InvoiceStatus = InvoiceStatus.CONFIRMED,
    val items: List<SalesInvoiceItem> = emptyList()
) {
    val discountTotal: Double get() = items.sumOf { it.quantity * it.unitPrice * (it.discountPercent / 100.0) }
    val total: Double get() = items.sumOf { it.lineTotal }
}

/**
 * [itemNumberSnapshot]/[itemNameSnapshot]/[unitSnapshot] are captured from the Product at the
 * moment the line is added (see SalesDetailViewModel.onAddProduct) and never re-read from it —
 * required by the spec so a historical invoice keeps showing exactly what was sold even after
 * the product is renamed, re-coded, or deactivated.
 */
data class SalesInvoiceItem(
    val id: Long = 0L,
    val salesInvoiceId: Long = 0L,
    val productId: Long,
    val productName: String? = null,
    val itemNumberSnapshot: String? = null,
    val itemNameSnapshot: String? = null,
    val unitSnapshot: String? = null,
    val quantity: Double,
    val unitPrice: Double,
    val discountPercent: Double = 0.0
) {
    val lineTotal: Double get() = quantity * unitPrice * (1 - discountPercent / 100.0)
}
