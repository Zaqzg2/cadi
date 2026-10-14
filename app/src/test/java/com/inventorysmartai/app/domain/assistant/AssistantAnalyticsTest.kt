package com.inventorysmartai.app.domain.assistant

import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.model.SalesInvoiceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantAnalyticsTest {

    private val utc = ZoneOffset.UTC
    private fun millis(year: Int, month: Int, day: Int): Long = LocalDate.of(year, month, day).atStartOfDay(utc).toInstant().toEpochMilli()

    private fun invoice(
        branchId: Long,
        date: Long,
        lines: List<Pair<Long, Double>>,
        status: InvoiceStatus = InvoiceStatus.CONFIRMED
    ) = SalesInvoice(
        invoiceNumber = "T",
        invoiceDate = date,
        customerId = null,
        branchId = branchId,
        status = status,
        items = lines.map { (productId, quantity) -> SalesInvoiceItem(productId = productId, quantity = quantity, unitPrice = 1.0) }
    )

    private fun goal(id: Long, productId: Long, name: String, target: Double, achieved: Double, start: Long = 0L, end: Long = 1_000L) =
        GoalProgress(goalId = id, productId = productId, productName = name, target = target, achieved = achieved, periodStart = start, periodEnd = end)

    // ------------------------------------------------------------------ windows

    @Test
    fun `a month window covers the whole calendar month`() {
        val window = AssistantAnalytics.monthWindow("2026-10", now = 0L, zone = utc)!!
        assertEquals("2026-10", window.label)
        assertEquals(millis(2026, 10, 1), window.from)
        assertEquals(millis(2026, 11, 1) - 1, window.to)
        assertTrue(window.contains(millis(2026, 10, 31) + 86_399_000L))
        assertFalse(window.contains(millis(2026, 11, 1)))
    }

    @Test
    fun `december rolls over into the next year`() {
        val window = AssistantAnalytics.monthWindow("2026-12", now = 0L, zone = utc)!!
        assertEquals(millis(2027, 1, 1) - 1, window.to)
    }

    @Test
    fun `no month means the month that contains now`() {
        val window = AssistantAnalytics.monthWindow(null, now = millis(2026, 10, 14), zone = utc)!!
        assertEquals("2026-10", window.label)
        assertEquals("2026-10", AssistantAnalytics.monthWindow("  ", now = millis(2026, 10, 14), zone = utc)!!.label)
    }

    @Test
    fun `a month that is not YYYY-MM is refused`() {
        assertNull(AssistantAnalytics.monthWindow("2026-13", now = 0L, zone = utc))
        assertNull(AssistantAnalytics.monthWindow("october", now = 0L, zone = utc))
    }

    @Test
    fun `explicit dates make a window, open on the missing side, and a backwards one is refused`() {
        assertNull(AssistantAnalytics.explicitWindow(null, null))
        assertNull(AssistantAnalytics.explicitWindow(10L, 5L))
        val openEnded = AssistantAnalytics.explicitWindow(100L, null)!!
        assertEquals(100L, openEnded.from)
        assertEquals(Long.MAX_VALUE, openEnded.to)
        assertEquals(0L, AssistantAnalytics.explicitWindow(null, 100L)!!.from)
    }

    @Test
    fun `a goal overlaps a window when their periods touch`() {
        val window = TimeWindow(100L, 200L, "w")
        assertTrue(AssistantAnalytics.overlaps(150L, 160L, window)) // inside
        assertTrue(AssistantAnalytics.overlaps(50L, 120L, window)) // starts before
        assertTrue(AssistantAnalytics.overlaps(180L, 300L, window)) // ends after
        assertTrue(AssistantAnalytics.overlaps(0L, 1000L, window)) // contains it
        assertFalse(AssistantAnalytics.overlaps(0L, 99L, window))
        assertFalse(AssistantAnalytics.overlaps(201L, 300L, window))
    }

    // ------------------------------------------------------------------ goals

    @Test
    fun `goal progress is not capped at one hundred percent`() {
        val over = goal(1, 1, "أ", target = 100.0, achieved = 130.0)
        assertEquals(130.0, over.percent, 0.0001)
        assertTrue(over.met)
        assertEquals(0.0, over.remaining, 0.0001)
        val under = goal(2, 2, "ب", target = 100.0, achieved = 40.0)
        assertFalse(under.met)
        assertEquals(60.0, under.remaining, 0.0001)
    }

    @Test
    fun `goals are ranked worst first and can be limited to the unmet ones`() {
        val goals = listOf(
            goal(1, 1, "جيد", 100.0, 120.0),
            goal(2, 2, "ضعيف", 100.0, 20.0),
            goal(3, 3, "وسط", 100.0, 70.0)
        )
        assertEquals(listOf(2L, 3L, 1L), AssistantAnalytics.rankGoals(goals, onlyUnmet = false).map { it.goalId })
        assertEquals(listOf(2L, 3L), AssistantAnalytics.rankGoals(goals, onlyUnmet = true).map { it.goalId })
    }

    // ------------------------------------------------------------------ sales and inbound

    @Test
    fun `only confirmed invoices inside the window and branch count as sales`() {
        val window = TimeWindow(100L, 200L, "w")
        val invoices = listOf(
            invoice(1, 150L, listOf(1L to 5.0, 2L to 1.0)),
            invoice(1, 160L, listOf(1L to 2.0)),
            invoice(2, 170L, listOf(1L to 10.0)),
            invoice(1, 180L, listOf(1L to 99.0), InvoiceStatus.DRAFT), // moved no stock
            invoice(1, 190L, listOf(1L to 99.0), InvoiceStatus.CANCELLED),
            invoice(1, 500L, listOf(1L to 99.0)) // outside the window
        )
        val all = AssistantAnalytics.soldByProduct(invoices, window)
        assertEquals(17.0, all[1L]!!, 0.0001)
        assertEquals(1.0, all[2L]!!, 0.0001)
        val branchOne = AssistantAnalytics.soldByProduct(invoices, window, branchId = 1L)
        assertEquals(7.0, branchOne[1L]!!, 0.0001)
        val byBranch = AssistantAnalytics.soldByBranch(invoices, window)
        assertEquals(8.0, byBranch[1L]!!, 0.0001)
        assertEquals(10.0, byBranch[2L]!!, 0.0001)
    }

    @Test
    fun `inbound lines add up per product and per branch`() {
        val lines = listOf(InboundLine(1, 1, 10.0), InboundLine(1, 2, 5.0), InboundLine(2, 1, 3.0))
        assertEquals(15.0, AssistantAnalytics.inboundByProduct(lines)[1L]!!, 0.0001)
        assertEquals(10.0, AssistantAnalytics.inboundByProduct(lines, branchId = 1L)[1L]!!, 0.0001)
        assertNull(AssistantAnalytics.inboundByProduct(lines, branchId = 2L)[2L])
        assertEquals(13.0, AssistantAnalytics.inboundByBranch(lines)[1L]!!, 0.0001)
    }

    @Test
    fun `flow rows merge inbound, sales and targets, most sold first`() {
        val rows = AssistantAnalytics.flowRows(
            inbound = mapOf(1L to 50.0, 3L to 7.0),
            sold = mapOf(1L to 20.0, 2L to 40.0),
            targets = mapOf(2L to 100.0),
            names = mapOf(1L to "أ", 2L to "ب")
        )
        assertEquals(listOf(2L, 1L, 3L), rows.map { it.productId })
        val first = rows[0]
        assertEquals(0.0, first.inbound, 0.0001)
        assertEquals(40.0, first.sold, 0.0001)
        assertEquals(100.0, first.goalTarget!!, 0.0001)
        assertEquals(-40.0, first.net, 0.0001)
        assertEquals(30.0, rows[1].net, 0.0001)
        assertNull(rows[1].goalTarget)
        assertEquals("صنف #3", rows[2].productName) // not in the names map
    }

    // ------------------------------------------------------------------ branches

    @Test
    fun `branches are ranked by their contribution to the goals`() {
        val goals = listOf(goal(1, productId = 1, name = "أ", target = 100.0, achieved = 0.0, start = 0L, end = 1_000L))
        val invoices = listOf(
            invoice(1, 500L, listOf(1L to 30.0)),
            invoice(2, 500L, listOf(1L to 10.0)),
            invoice(2, 500L, listOf(9L to 500.0)), // a product without a goal does not count
            invoice(1, 500L, listOf(1L to 50.0), InvoiceStatus.DRAFT), // a draft does not count
            invoice(1, 2_000L, listOf(1L to 70.0)) // outside the goal's period
        )
        val rows = AssistantAnalytics.branchContributions(goals, invoices, mapOf(1L to "الأول", 2L to "الثاني", 3L to "الثالث"))
        assertEquals(listOf(1L, 2L, 3L), rows.map { it.branchId })
        assertEquals(30.0, rows[0].contributionPercent, 0.0001)
        assertEquals(10.0, rows[1].contributionPercent, 0.0001)
        assertEquals(0.0, rows[2].contributionPercent, 0.0001) // a branch that sold nothing is still listed
        assertEquals(75.0, rows[0].shareOfSoldPercent, 0.0001)
        assertEquals(25.0, rows[1].shareOfSoldPercent, 0.0001)
    }

    @Test
    fun `with no goals every branch is listed at zero`() {
        val rows = AssistantAnalytics.branchContributions(emptyList(), emptyList(), mapOf(1L to "أ", 2L to "ب"))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.contributionPercent == 0.0 && it.shareOfSoldPercent == 0.0 })
    }

    @Test
    fun `rounding helpers keep one or two decimals`() {
        assertEquals(33.3, AssistantAnalytics.round1(33.3333), 0.0001)
        assertEquals(0.67, AssistantAnalytics.round2(0.66666), 0.0001)
    }
}
