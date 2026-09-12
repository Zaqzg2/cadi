package com.inventorysmartai.app.domain.model

data class SalesInvoice(
    val id: Long = 0L,
    val invoiceNumber: String,
    val invoiceDate: Long,
    val customerId: Long?,
    val customerName: String? = null,
    val branchId: Long,
    val branchName: String? = null,
    val notes: String? = null,
    val status: InvoiceStatus = InvoiceStatus.CONFIRMED,
    val items: List<SalesInvoiceItem> = emptyList()
) {
    val total: Double get() = items.sumOf { it.lineTotal }
}

data class SalesInvoiceItem(
    val id: Long = 0L,
    val salesInvoiceId: Long = 0L,
    val productId: Long,
    val productName: String? = null,
    val quantity: Double,
    val unitPrice: Double,
    val discountPercent: Double = 0.0
) {
    val lineTotal: Double get() = quantity * unitPrice * (1 - discountPercent / 100.0)
}
