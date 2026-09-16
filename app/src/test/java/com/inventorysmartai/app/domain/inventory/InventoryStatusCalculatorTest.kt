package com.inventorysmartai.app.domain.inventory

import com.inventorysmartai.app.domain.model.InventoryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryStatusCalculatorTest {

    private val now = 1_700_000_000_000L
    private val oneDayMillis = 24L * 60 * 60 * 1000

    @Test
    fun `zero quantity is ZERO even when above an expiry window`() {
        val status = InventoryStatusCalculator.compute(
            hasExpiry = false, minStock = 5.0, totalQuantity = 0.0,
            nearestExpiryDate = null, nearExpiryWindowDays = 30, now = now
        )
        assertEquals(InventoryStatus.ZERO, status)
    }

    @Test
    fun `quantity at or below minStock is LOW`() {
        val status = InventoryStatusCalculator.compute(
            hasExpiry = false, minStock = 10.0, totalQuantity = 10.0,
            nearestExpiryDate = null, nearExpiryWindowDays = 30, now = now
        )
        assertEquals(InventoryStatus.LOW, status)
    }

    @Test
    fun `quantity above minStock with no expiry tracking is AVAILABLE`() {
        val status = InventoryStatusCalculator.compute(
            hasExpiry = false, minStock = 10.0, totalQuantity = 50.0,
            nearestExpiryDate = null, nearExpiryWindowDays = 30, now = now
        )
        assertEquals(InventoryStatus.AVAILABLE, status)
    }

    @Test
    fun `expiry date in the past is EXPIRED even with healthy quantity`() {
        val status = InventoryStatusCalculator.compute(
            hasExpiry = true, minStock = 5.0, totalQuantity = 100.0,
            nearestExpiryDate = now - oneDayMillis, nearExpiryWindowDays = 30, now = now
        )
        assertEquals(InventoryStatus.EXPIRED, status)
    }

    @Test
    fun `expiry within the near-expiry window is NEAR_EXPIRY when stock is otherwise healthy`() {
        val status = InventoryStatusCalculator.compute(
            hasExpiry = true, minStock = 5.0, totalQuantity = 100.0,
            nearestExpiryDate = now + 10 * oneDayMillis, nearExpiryWindowDays = 30, now = now
        )
        assertEquals(InventoryStatus.NEAR_EXPIRY, status)
    }

    @Test
    fun `expiry beyond the near-expiry window with healthy stock is AVAILABLE`() {
        val status = InventoryStatusCalculator.compute(
            hasExpiry = true, minStock = 5.0, totalQuantity = 100.0,
            nearestExpiryDate = now + 90 * oneDayMillis, nearExpiryWindowDays = 30, now = now
        )
        assertEquals(InventoryStatus.AVAILABLE, status)
    }

    @Test
    fun `zero quantity outranks a near-expiry date on a different batch`() {
        val status = InventoryStatusCalculator.compute(
            hasExpiry = true, minStock = 5.0, totalQuantity = 0.0,
            nearestExpiryDate = now + 10 * oneDayMillis, nearExpiryWindowDays = 30, now = now
        )
        assertEquals(InventoryStatus.ZERO, status)
    }
}
