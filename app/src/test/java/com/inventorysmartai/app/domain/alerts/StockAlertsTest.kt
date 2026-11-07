package com.inventorysmartai.app.domain.alerts

import com.inventorysmartai.app.domain.model.InventoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StockAlertsTest {

    // ------------------------------------------------------------------ counts

    @Test
    fun `counts group the products by status and ignore available ones`() {
        val counts = StockAlertCounts.from(
            listOf(
                InventoryStatus.EXPIRED, InventoryStatus.EXPIRED,
                InventoryStatus.NEAR_EXPIRY,
                InventoryStatus.ZERO,
                InventoryStatus.LOW, InventoryStatus.LOW, InventoryStatus.LOW,
                InventoryStatus.AVAILABLE, InventoryStatus.AVAILABLE
            )
        )
        assertEquals(StockAlertCounts(expired = 2, nearExpiry = 1, zeroStock = 1, lowStock = 3), counts)
        assertEquals(7, counts.total)
    }

    // ------------------------------------------------------------------ message

    @Test
    fun `nothing to report means no notification`() {
        assertNull(StockAlertMessageBuilder.build(StockAlertCounts.from(listOf(InventoryStatus.AVAILABLE))))
        assertNull(StockAlertMessageBuilder.build(StockAlertCounts(0, 0, 0, 0)))
    }

    @Test
    fun `the body lists only the non empty groups`() {
        val message = StockAlertMessageBuilder.build(StockAlertCounts(expired = 0, nearExpiry = 4, zeroStock = 0, lowStock = 2))
        assertNotNull(message)
        assertEquals("4 قريبة الانتهاء • 2 مخزونها منخفض", message?.body)
    }

    @Test
    fun `expired or out of stock makes the alert urgent`() {
        assertEquals("تنبيه مخزون عاجل", StockAlertMessageBuilder.build(StockAlertCounts(1, 0, 0, 0))?.title)
        assertEquals("تنبيه مخزون عاجل", StockAlertMessageBuilder.build(StockAlertCounts(0, 0, 1, 0))?.title)
    }

    @Test
    fun `near expiry and low stock alone are not urgent`() {
        assertEquals("تنبيه مخزون", StockAlertMessageBuilder.build(StockAlertCounts(0, 3, 0, 5))?.title)
    }

    @Test
    fun `groups appear in order of seriousness`() {
        val body = StockAlertMessageBuilder.build(StockAlertCounts(expired = 1, nearExpiry = 2, zeroStock = 3, lowStock = 4))?.body.orEmpty()
        assertTrue(body.indexOf("منتهية") < body.indexOf("قريبة"))
        assertTrue(body.indexOf("قريبة") < body.indexOf("نفد"))
        assertTrue(body.indexOf("نفد") < body.indexOf("منخفض"))
    }

    // ------------------------------------------------------------------ schedule

    private val zone = ZoneId.of("UTC")
    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 19, hour, minute, 0, 0, zone)

    @Test
    fun `the next run is later today when the hour has not come yet`() {
        // 07:30 -> 09:00 = 1.5 hours
        assertEquals(90L * 60 * 1000, StockAlertSchedule.delayMillisUntilNext(9, at(7, 30)))
    }

    @Test
    fun `the next run is tomorrow when the hour already passed`() {
        // 10:00 -> 09:00 next day = 23 hours
        assertEquals(23L * 60 * 60 * 1000, StockAlertSchedule.delayMillisUntilNext(9, at(10)))
    }

    @Test
    fun `exactly at the hour the next run is a full day away`() {
        assertEquals(24L * 60 * 60 * 1000, StockAlertSchedule.delayMillisUntilNext(9, at(9)))
    }

    @Test
    fun `an out of range hour is clamped instead of crashing`() {
        assertTrue(StockAlertSchedule.delayMillisUntilNext(99, at(7)) > 0)
        assertTrue(StockAlertSchedule.delayMillisUntilNext(-5, at(7)) > 0)
    }
}
