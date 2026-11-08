package com.inventorysmartai.app.domain.dashboard

import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.domain.model.GoalAchievement
import com.inventorysmartai.app.domain.model.InventoryCount
import com.inventorysmartai.app.domain.model.InventoryCountItem
import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.model.PurchaseRequestItem
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.model.SalesInvoiceItem
import com.inventorysmartai.app.domain.stockops.StockOperation
import com.inventorysmartai.app.domain.stockops.StockOperationLineView
import com.inventorysmartai.app.domain.stockops.StockOperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DashboardStatsTest {

    private fun achievement(target: Double, achieved: Double) = GoalAchievement(
        totalTarget = target,
        achievedQuantity = achieved,
        remainingQuantity = (target - achieved).coerceAtLeast(0.0),
        achievementPercent = if (target <= 0.0) 0.0 else achieved / target * 100.0
    )

    @Test
    fun `goal summary splits completed in-progress and not-started`() {
        val s = goalSummary(listOf(achievement(100.0, 120.0), achievement(100.0, 40.0), achievement(50.0, 0.0)))
        assertEquals(3, s.count)
        assertEquals(1, s.completed)
        assertEquals(1, s.inProgress)
        assertEquals(1, s.notStarted)
        assertEquals(250.0, s.totalTarget, 0.001)
        assertEquals(160.0, s.achieved, 0.001)
    }

    @Test
    fun `overall percent is capped at 100 and zero without a target`() {
        assertEquals(100.0, goalSummary(listOf(achievement(10.0, 50.0))).overallPercent, 0.001)
        assertEquals(0.0, goalSummary(emptyList()).overallPercent, 0.001)
    }

    private fun count(status: CountStatus, vararg diffs: Double) = InventoryCount(
        branchId = 1L, countDate = 0L, status = status,
        items = diffs.mapIndexed { i, d ->
            InventoryCountItem(productId = i.toLong(), productName = "P$i", systemQuantity = 10.0, actualQuantity = 10.0 + d)
        }
    )

    @Test
    fun `counting differences come from completed counts only`() {
        val s = countingSummary(
            listOf(count(CountStatus.COMPLETED, -3.0, 0.0, 5.0), count(CountStatus.DRAFT, -100.0), count(CountStatus.CANCELLED, 50.0))
        )
        assertEquals(3, s.total)
        assertEquals(1, s.completed)
        assertEquals(1, s.open)
        assertEquals(1, s.cancelled)
        assertEquals(2, s.itemsWithDifference)
        assertEquals(3.0, s.shortage, 0.001)
        assertEquals(5.0, s.overage, 0.001)
    }

    @Test
    fun `differences by product sum across completed counts`() {
        val diffs = countingDifferencesByProduct(listOf(count(CountStatus.COMPLETED, -2.0), count(CountStatus.COMPLETED, -3.0)))
        assertEquals(-5.0, diffs["P0"]!!, 0.001)
    }

    private fun request(status: PurchaseStatus, supplier: String?, requested: Double, received: Double = 0.0) = PurchaseRequest(
        requestNumber = "R", supplierId = null, supplierName = supplier, branchId = 1L, requestDate = 0L, status = status,
        items = listOf(PurchaseRequestItem(productId = 1L, productName = "A", currentStockSnapshot = 0.0, requestedQuantity = requested, receivedQuantity = received))
    )

    @Test
    fun `purchase summary ignores cancelled and rejected quantities`() {
        val s = purchaseSummary(
            listOf(
                request(PurchaseStatus.RECEIVED, "S1", 10.0, 10.0),
                request(PurchaseStatus.ORDERED, "S1", 20.0, 5.0),
                request(PurchaseStatus.CANCELLED, "S2", 99.0),
                request(PurchaseStatus.REJECTED, "S2", 99.0),
                request(PurchaseStatus.DRAFT, null, 10.0)
            )
        )
        assertEquals(5, s.total)
        assertEquals(2, s.open)
        assertEquals(1, s.received)
        assertEquals(2, s.closedWithoutReceipt)
        assertEquals(40.0, s.requestedQuantity, 0.001)
        assertEquals(15.0, s.receivedQuantity, 0.001)
        assertEquals(37.5, s.receivedPercent, 0.001)
    }

    @Test
    fun `requested by supplier groups a missing supplier under a label`() {
        val m = requestedBySupplier(listOf(request(PurchaseStatus.DRAFT, null, 4.0), request(PurchaseStatus.DRAFT, " ", 6.0)))
        assertEquals(10.0, m["بدون مورد"]!!, 0.001)
    }

    private fun invoice(status: InvoiceStatus, date: Long, customer: String?, qty: Double, price: Double) = SalesInvoice(
        invoiceNumber = "I", invoiceDate = date, customerId = null, customerName = customer, branchId = 1L, status = status,
        items = listOf(SalesInvoiceItem(productId = 1L, productName = "X", quantity = qty, unitPrice = price))
    )

    @Test
    fun `sales revenue counts confirmed invoices only`() {
        val s = salesSummary(
            listOf(
                invoice(InvoiceStatus.CONFIRMED, 0L, "A", 1.0, 100.0),
                invoice(InvoiceStatus.CONFIRMED, 0L, "B", 1.0, 50.0),
                invoice(InvoiceStatus.DRAFT, 0L, "C", 1.0, 1000.0),
                invoice(InvoiceStatus.CANCELLED, 0L, "D", 1.0, 1000.0)
            )
        )
        assertEquals(4, s.invoices)
        assertEquals(2, s.confirmed)
        assertEquals(150.0, s.revenue, 0.001)
        assertEquals(75.0, s.averageInvoice, 0.001)
        assertEquals(2, s.customers)
        assertEquals(1, s.drafts)
        assertEquals(1, s.cancelled)
    }

    @Test
    fun `daily totals cover every day and put zero on quiet days`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 10, 15, 30, 0) }.timeInMillis
        val today = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 10, 9, 0, 0) }.timeInMillis
        val twoDaysAgo = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 8, 22, 0, 0) }.timeInMillis
        val tooOld = Calendar.getInstance().apply { set(2026, Calendar.FEBRUARY, 1, 12, 0, 0) }.timeInMillis
        val totals = dailySalesTotals(
            listOf(
                invoice(InvoiceStatus.CONFIRMED, today, "A", 1.0, 30.0),
                invoice(InvoiceStatus.CONFIRMED, today, "A", 1.0, 20.0),
                invoice(InvoiceStatus.CONFIRMED, twoDaysAgo, "A", 1.0, 7.0),
                invoice(InvoiceStatus.CONFIRMED, tooOld, "A", 1.0, 999.0),
                invoice(InvoiceStatus.CANCELLED, today, "A", 1.0, 999.0)
            ),
            days = 5, now = now
        )
        assertEquals(5, totals.size)
        assertEquals(50.0, totals.last().second, 0.001)
        assertEquals(7.0, totals[2].second, 0.001)
        assertEquals(0.0, totals[3].second, 0.001)
        assertEquals(57.0, totals.sumOf { it.second }, 0.001)
        assertTrue(totals.zipWithNext().all { (a, b) -> a.first < b.first })
    }

    @Test
    fun `sales by customer labels an unnamed customer as cash`() {
        val m = salesByCustomer(listOf(invoice(InvoiceStatus.CONFIRMED, 0L, null, 2.0, 10.0)))
        assertEquals(20.0, m["عميل نقدي"]!!, 0.001)
    }

    private fun op(type: StockOperationType, from: String?, to: String?, vararg lines: Pair<String, Double>) = StockOperation(
        number = 1L, type = type, createdAt = 0L, branchId = 1L, branchName = from, toBranchId = 2L, toBranchName = to,
        lines = lines.mapIndexed { i, (name, q) -> StockOperationLineView(i.toLong(), name, q) }, notes = null
    )

    @Test
    fun `stock operation summary counts by type and sums quantity`() {
        val s = stockOpsSummary(
            listOf(
                op(StockOperationType.TRANSFER, "A", "B", "x" to 3.0, "y" to 2.0),
                op(StockOperationType.SALES_RETURN, "A", null, "x" to 1.0),
                op(StockOperationType.PURCHASE_RETURN, "B", null, "z" to 4.0)
            )
        )
        assertEquals(3, s.total)
        assertEquals(1, s.transfers)
        assertEquals(1, s.salesReturns)
        assertEquals(1, s.purchaseReturns)
        assertEquals(10.0, s.totalQuantity, 0.001)
    }

    @Test
    fun `branch activity counts a branch once per operation`() {
        val m = operationsByBranch(listOf(op(StockOperationType.TRANSFER, "A", "A", "x" to 1.0), op(StockOperationType.TRANSFER, "A", "B", "x" to 1.0)))
        assertEquals(2.0, m["A"]!!, 0.001)
        assertEquals(1.0, m["B"]!!, 0.001)
    }

    @Test
    fun `top entries sorts descending drops zeros and can rank by absolute value`() {
        val values = mapOf("a" to 5.0, "b" to -9.0, "c" to 0.0, "d" to 2.0)
        assertEquals(listOf("a", "d"), topEntries(values, 2).map { it.first })
        assertEquals(listOf("b", "a"), topEntries(values, 2, byAbsolute = true).map { it.first })
    }
}
