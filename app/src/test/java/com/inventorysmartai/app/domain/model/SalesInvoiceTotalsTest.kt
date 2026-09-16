package com.inventorysmartai.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SalesInvoiceTotalsTest {

    @Test
    fun `line total applies the discount percent to quantity times price`() {
        val item = SalesInvoiceItem(productId = 1L, quantity = 2.0, unitPrice = 10.0, discountPercent = 10.0)
        // 2 * 10 = 20, less 10% = 18
        assertEquals(18.0, item.lineTotal, 0.001)
    }

    @Test
    fun `zero discount leaves the line total as quantity times price`() {
        val item = SalesInvoiceItem(productId = 1L, quantity = 3.0, unitPrice = 5.0)
        assertEquals(15.0, item.lineTotal, 0.001)
    }

    @Test
    fun `invoice total is the sum of every line total`() {
        val invoice = SalesInvoice(
            invoiceNumber = "INV-1", invoiceDate = 0L, customerId = null, branchId = 1L,
            items = listOf(
                SalesInvoiceItem(productId = 1L, quantity = 2.0, unitPrice = 10.0),
                SalesInvoiceItem(productId = 2L, quantity = 1.0, unitPrice = 25.0, discountPercent = 20.0)
            )
        )
        // 20 + (25 * 0.8 = 20) = 40
        assertEquals(40.0, invoice.total, 0.001)
    }

    @Test
    fun `discount total sums only the discounted amount, not the full line price`() {
        val invoice = SalesInvoice(
            invoiceNumber = "INV-1", invoiceDate = 0L, customerId = null, branchId = 1L,
            items = listOf(SalesInvoiceItem(productId = 1L, quantity = 2.0, unitPrice = 10.0, discountPercent = 25.0))
        )
        // full price 20, 25% off = 5 discount, total = 15
        assertEquals(5.0, invoice.discountTotal, 0.001)
        assertEquals(15.0, invoice.total, 0.001)
    }

    @Test
    fun `an invoice with no items totals zero, not an error`() {
        val invoice = SalesInvoice(invoiceNumber = "INV-1", invoiceDate = 0L, customerId = null, branchId = 1L)
        assertEquals(0.0, invoice.total, 0.001)
        assertEquals(0.0, invoice.discountTotal, 0.001)
    }
}
