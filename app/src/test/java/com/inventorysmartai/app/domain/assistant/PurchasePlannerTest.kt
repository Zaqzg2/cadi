package com.inventorysmartai.app.domain.assistant

import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.ProductStockSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchasePlannerTest {

    private fun summary(
        id: Long,
        name: String,
        quantity: Double,
        min: Double = 0.0,
        reorder: Double = 0.0,
        status: InventoryStatus = InventoryStatus.AVAILABLE
    ) = ProductStockSummary(
        product = Product(id = id, itemNumber = null, barcode = null, name = name, categoryId = null, unitId = null, minStock = min, reorderPoint = reorder),
        branchStocks = emptyList(),
        totalQuantity = quantity,
        nearestExpiryDate = null,
        status = status
    )

    @Test
    fun `a product above its minimum is not short`() {
        assertTrue(PurchasePlanner.shortages(listOf(summary(1, "أ", quantity = 20.0, min = 10.0))).isEmpty())
    }

    @Test
    fun `a product at its minimum is short and is ordered up to twice the minimum`() {
        val line = PurchasePlanner.shortages(listOf(summary(1, "أ", quantity = 10.0, min = 10.0))).single()
        assertEquals(20.0, line.targetQuantity!!, 0.0001)
        assertEquals(10.0, line.suggestedQuantity!!, 0.0001)
    }

    @Test
    fun `the larger of the minimum and the reorder point is the trigger`() {
        val line = PurchasePlanner.shortages(listOf(summary(1, "أ", quantity = 12.0, min = 10.0, reorder = 15.0))).single()
        assertEquals(30.0, line.targetQuantity!!, 0.0001)
        assertEquals(18.0, line.suggestedQuantity!!, 0.0001)
    }

    @Test
    fun `zero stock with a minimum is ordered up to the target`() {
        val line = PurchasePlanner.shortages(listOf(summary(1, "أ", quantity = 0.0, min = 8.0))).single()
        assertEquals(16.0, line.suggestedQuantity!!, 0.0001)
    }

    @Test
    fun `zero stock with no thresholds is listed without a quantity and says why`() {
        val line = PurchasePlanner.shortages(listOf(summary(1, "أ", quantity = 0.0))).single()
        assertNull(line.suggestedQuantity)
        assertNull(line.targetQuantity)
        assertNotNull(line.note)
    }

    @Test
    fun `a product with stock but no thresholds is never short`() {
        assertTrue(PurchasePlanner.shortages(listOf(summary(1, "أ", quantity = 5.0))).isEmpty())
    }

    @Test
    fun `fractions are rounded up so the order never falls short`() {
        val line = PurchasePlanner.shortages(listOf(summary(1, "أ", quantity = 2.5, min = 4.0))).single()
        assertEquals(6.0, line.suggestedQuantity!!, 0.0001) // target 8 - 2.5 = 5.5 -> 6
    }

    @Test
    fun `the target factor is kept between one and ten`() {
        val stock = listOf(summary(1, "أ", quantity = 4.0, min = 10.0))
        assertEquals(6.0, PurchasePlanner.shortages(stock, targetFactor = 0.2).single().suggestedQuantity!!, 0.0001)
        assertEquals(96.0, PurchasePlanner.shortages(stock, targetFactor = 99.0).single().suggestedQuantity!!, 0.0001)
    }

    @Test
    fun `the most urgent come first - no stock, then the lowest share of the minimum`() {
        val lines = PurchasePlanner.shortages(
            listOf(
                summary(1, "قريب", quantity = 9.0, min = 10.0),
                summary(2, "نصف", quantity = 5.0, min = 10.0),
                summary(3, "صفر", quantity = 0.0, min = 10.0)
            )
        )
        assertEquals(listOf(3L, 2L, 1L), lines.map { it.productId })
    }

    @Test
    fun `expired stock carries a warning`() {
        val line = PurchasePlanner.shortages(listOf(summary(1, "أ", quantity = 3.0, min = 10.0, status = InventoryStatus.EXPIRED))).single()
        assertTrue(line.note!!.contains("منتهية"))
    }

    @Test
    fun `the limit keeps only the most urgent`() {
        val lines = PurchasePlanner.shortages(
            listOf(summary(1, "أ", 9.0, 10.0), summary(2, "ب", 1.0, 10.0), summary(3, "ج", 5.0, 10.0)),
            limit = 2
        )
        assertEquals(listOf(2L, 3L), lines.map { it.productId })
    }

    @Test
    fun `the rule text names the factor that was used`() {
        assertTrue(PurchasePlanner.ruleDescription(2.0).contains("2 ×"))
        assertTrue(PurchasePlanner.ruleDescription(1.5).contains("1.5 ×"))
    }
}
