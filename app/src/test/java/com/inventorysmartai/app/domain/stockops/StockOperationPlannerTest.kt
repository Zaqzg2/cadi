package com.inventorysmartai.app.domain.stockops

import com.inventorysmartai.app.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockOperationPlannerTest {

    private fun transfer(vararg lines: StockOperationLine, from: Long = 1, to: Long? = 2) =
        StockOperationRequest(StockOperationType.TRANSFER, branchId = from, toBranchId = to, lines = lines.toList())

    private fun salesReturn(vararg lines: StockOperationLine) =
        StockOperationRequest(StockOperationType.SALES_RETURN, branchId = 1, lines = lines.toList())

    private fun purchaseReturn(vararg lines: StockOperationLine) =
        StockOperationRequest(StockOperationType.PURCHASE_RETURN, branchId = 1, lines = lines.toList())

    private fun rejection(block: () -> Unit): String? = try { block(); null } catch (e: InvalidStockOperationException) { e.message }

    // ---------------------------------------------------------------- validation

    @Test
    fun `an operation without lines is rejected`() {
        assertNotNull(rejection { StockOperationPlanner.validated(salesReturn()) })
    }

    @Test
    fun `zero and negative quantities are rejected`() {
        assertNotNull(rejection { StockOperationPlanner.validated(salesReturn(StockOperationLine(10, 0.0))) })
        assertNotNull(rejection { StockOperationPlanner.validated(salesReturn(StockOperationLine(10, -3.0))) })
        assertNotNull(rejection { StockOperationPlanner.validated(salesReturn(StockOperationLine(10, Double.NaN))) })
    }

    @Test
    fun `a transfer needs a destination and it must differ from the source`() {
        assertNotNull(rejection { StockOperationPlanner.validated(transfer(StockOperationLine(10, 1.0), to = null)) })
        assertNotNull(rejection { StockOperationPlanner.validated(transfer(StockOperationLine(10, 1.0), from = 1, to = 1)) })
        assertNull(rejection { StockOperationPlanner.validated(transfer(StockOperationLine(10, 1.0), from = 1, to = 2)) })
    }

    @Test
    fun `the same product on two lines becomes one line - first appearance keeps its place`() {
        val valid = StockOperationPlanner.validated(
            salesReturn(StockOperationLine(10, 2.0), StockOperationLine(20, 5.0), StockOperationLine(10, 3.0))
        )
        assertEquals(listOf(StockOperationLine(10, 5.0), StockOperationLine(20, 5.0)), valid.lines)
    }

    // ---------------------------------------------------------------- movements

    @Test
    fun `a transfer takes from the source and adds to the destination for every line`() {
        val plan = StockOperationPlanner.movements(transfer(StockOperationLine(10, 4.0), StockOperationLine(20, 1.5), from = 1, to = 2))
        assertEquals(
            listOf(
                PlannedMovement(10, 1, MovementType.TRANSFER_OUT, -4.0),
                PlannedMovement(10, 2, MovementType.TRANSFER_IN, 4.0),
                PlannedMovement(20, 1, MovementType.TRANSFER_OUT, -1.5),
                PlannedMovement(20, 2, MovementType.TRANSFER_IN, 1.5)
            ),
            plan
        )
    }

    @Test
    fun `a transfer is stock neutral overall`() {
        val plan = StockOperationPlanner.movements(transfer(StockOperationLine(10, 7.0), StockOperationLine(20, 2.0)))
        assertEquals(0.0, plan.sumOf { it.delta }, 0.0)
    }

    @Test
    fun `a sales return brings stock back into the branch`() {
        val plan = StockOperationPlanner.movements(salesReturn(StockOperationLine(10, 3.0)))
        assertEquals(listOf(PlannedMovement(10, 1, MovementType.RETURN_IN, 3.0)), plan)
    }

    @Test
    fun `a purchase return takes stock out of the branch`() {
        val plan = StockOperationPlanner.movements(purchaseReturn(StockOperationLine(10, 3.0)))
        assertEquals(listOf(PlannedMovement(10, 1, MovementType.RETURN_OUT, -3.0)), plan)
    }

    // ---------------------------------------------------------------- availability

    @Test
    fun `only transfers and purchase returns need stock to leave the branch`() {
        val line = StockOperationLine(10, 3.0)
        assertEquals(listOf(line), StockOperationPlanner.outgoing(transfer(line)))
        assertEquals(listOf(line), StockOperationPlanner.outgoing(purchaseReturn(line)))
        assertTrue(StockOperationPlanner.outgoing(salesReturn(line)).isEmpty())
    }

    @Test
    fun `a shortage is reported when the branch has less than requested`() {
        val shortages = StockOperationPlanner.shortages(
            outgoing = listOf(StockOperationLine(10, 5.0), StockOperationLine(20, 2.0)),
            available = mapOf(10L to 3.0, 20L to 9.0)
        )
        assertEquals(listOf(StockShortage(productId = 10, requested = 5.0, available = 3.0)), shortages)
    }

    @Test
    fun `asking for exactly what is available is allowed`() {
        val shortages = StockOperationPlanner.shortages(listOf(StockOperationLine(10, 5.0)), mapOf(10L to 5.0))
        assertTrue(shortages.isEmpty())
    }

    @Test
    fun `a product with no stock row counts as zero available`() {
        val shortages = StockOperationPlanner.shortages(listOf(StockOperationLine(10, 1.0)), emptyMap())
        assertEquals(listOf(StockShortage(10, 1.0, 0.0)), shortages)
    }

    // ---------------------------------------------------------------- notes + stored reference types

    @Test
    fun `notes join the party and the user note`() {
        val request = salesReturn(StockOperationLine(10, 1.0)).copy(partyLabel = "العميل: محمد", notes = "  تالف  ")
        assertEquals("العميل: محمد — تالف", StockOperationPlanner.notesFor(request))
    }

    @Test
    fun `notes are null when there is nothing to record`() {
        assertNull(StockOperationPlanner.notesFor(salesReturn(StockOperationLine(10, 1.0)).copy(partyLabel = " ", notes = "")))
    }

    /** InventoryMovementDao.observeStockOperationMovements spells these three values inside its SQL — they must not drift. */
    @Test
    fun `the stored reference types match the ones the DAO query lists`() {
        assertEquals("STOCK_TRANSFER", StockOperationType.TRANSFER.referenceType)
        assertEquals("SALES_RETURN", StockOperationType.SALES_RETURN.referenceType)
        assertEquals("PURCHASE_RETURN", StockOperationType.PURCHASE_RETURN.referenceType)
    }

    @Test
    fun `an unknown reference type is not an operation`() {
        assertNull(StockOperationType.fromReference("SALES_INVOICE"))
        assertNull(StockOperationType.fromReference(null))
        assertEquals(StockOperationType.TRANSFER, StockOperationType.fromReference("STOCK_TRANSFER"))
    }
}
