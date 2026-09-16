package com.inventorysmartai.app.domain.sales

import com.inventorysmartai.app.domain.model.SalesInvoiceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesStockValidatorTest {

    private fun item(productId: Long, quantity: Double, name: String = "منتج") = SalesInvoiceItem(
        productId = productId, itemNameSnapshot = name, quantity = quantity, unitPrice = 10.0
    )

    @Test
    fun `no problems when every line has enough stock`() {
        val problems = SalesStockValidator.validate(
            items = listOf(item(1L, 5.0), item(2L, 3.0)),
            availableByProduct = mapOf(1L to 10.0, 2L to 3.0)
        )
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `flags a line that would oversell`() {
        val problems = SalesStockValidator.validate(
            items = listOf(item(1L, 20.0, name = "أرز بسمتي")),
            availableByProduct = mapOf(1L to 5.0)
        )
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("أرز بسمتي"))
    }

    @Test
    fun `a product with no stock row at all is treated as zero available`() {
        val problems = SalesStockValidator.validate(
            items = listOf(item(1L, 1.0)),
            availableByProduct = emptyMap()
        )
        assertEquals(1, problems.size)
    }

    @Test
    fun `reports every oversold line in one pass, not just the first`() {
        val problems = SalesStockValidator.validate(
            items = listOf(item(1L, 20.0), item(2L, 30.0), item(3L, 1.0)),
            availableByProduct = mapOf(1L to 5.0, 2L to 5.0, 3L to 5.0)
        )
        assertEquals(2, problems.size)
    }

    @Test
    fun `selling exactly the available quantity is allowed`() {
        val problems = SalesStockValidator.validate(
            items = listOf(item(1L, 5.0)),
            availableByProduct = mapOf(1L to 5.0)
        )
        assertTrue(problems.isEmpty())
    }
}
