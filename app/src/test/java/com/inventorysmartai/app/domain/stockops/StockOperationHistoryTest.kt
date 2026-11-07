package com.inventorysmartai.app.domain.stockops

import com.inventorysmartai.app.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockOperationHistoryTest {

    private val productNames = mapOf(10L to "حليب", 20L to "رز")
    private val branchNames = mapOf(1L to "الرئيسي", 2L to "الفرع الثاني")

    private fun group(movements: List<OperationMovement>) =
        StockOperationHistory.group(movements, productName = { productNames[it] }, branchName = { branchNames[it] })

    private fun move(
        productId: Long, branchId: Long, type: MovementType, change: Double,
        ref: String?, number: Long?, at: Long = 1000, notes: String? = null
    ) = OperationMovement(productId, branchId, type.name, change, ref, number, notes, at)

    @Test
    fun `a transfer reads its lines and source from the outgoing rows and its destination from the incoming row`() {
        val ops = group(
            listOf(
                move(10, 1, MovementType.TRANSFER_OUT, -4.0, "STOCK_TRANSFER", 7),
                move(10, 2, MovementType.TRANSFER_IN, 4.0, "STOCK_TRANSFER", 7),
                move(20, 1, MovementType.TRANSFER_OUT, -2.0, "STOCK_TRANSFER", 7),
                move(20, 2, MovementType.TRANSFER_IN, 2.0, "STOCK_TRANSFER", 7)
            )
        )

        val op = ops.single()
        assertEquals(StockOperationType.TRANSFER, op.type)
        assertEquals(7L, op.number)
        assertEquals("الرئيسي", op.branchName)
        assertEquals("الفرع الثاني", op.toBranchName)
        assertEquals(
            listOf(StockOperationLineView(10, "حليب", 4.0), StockOperationLineView(20, "رز", 2.0)),
            op.lines
        )
    }

    @Test
    fun `a return keeps the branch and has no destination`() {
        val op = group(listOf(move(10, 1, MovementType.RETURN_IN, 3.0, "SALES_RETURN", 2, notes = "العميل: محمد"))).single()

        assertEquals(StockOperationType.SALES_RETURN, op.type)
        assertEquals("الرئيسي", op.branchName)
        assertNull(op.toBranchId)
        assertEquals("العميل: محمد", op.notes)
        assertEquals(3.0, op.lines.single().quantity, 0.0)
    }

    @Test
    fun `quantities are shown positive even for outgoing movements`() {
        val op = group(listOf(move(10, 1, MovementType.RETURN_OUT, -5.0, "PURCHASE_RETURN", 1))).single()
        assertEquals(5.0, op.lines.single().quantity, 0.0)
    }

    @Test
    fun `the same number under two types is two operations`() {
        val ops = group(
            listOf(
                move(10, 1, MovementType.RETURN_IN, 1.0, "SALES_RETURN", 1),
                move(10, 1, MovementType.RETURN_OUT, -1.0, "PURCHASE_RETURN", 1)
            )
        )
        assertEquals(2, ops.size)
    }

    @Test
    fun `movements that are not operations are ignored`() {
        val ops = group(
            listOf(
                move(10, 1, MovementType.SALE_OUT, -1.0, "SALES_INVOICE", 5),
                move(10, 1, MovementType.MANUAL, 2.0, null, null),
                move(10, 1, MovementType.RETURN_IN, 1.0, "SALES_RETURN", null)
            )
        )
        assertTrue(ops.isEmpty())
    }

    @Test
    fun `newest first and the higher number first on a tie`() {
        val ops = group(
            listOf(
                move(10, 1, MovementType.RETURN_IN, 1.0, "SALES_RETURN", 1, at = 100),
                move(10, 1, MovementType.RETURN_IN, 1.0, "SALES_RETURN", 2, at = 300),
                move(10, 1, MovementType.RETURN_IN, 1.0, "SALES_RETURN", 3, at = 300)
            )
        )
        assertEquals(listOf(3L, 2L, 1L), ops.map { it.number })
    }

    private val sample = StockOperation(
        number = 7, type = StockOperationType.TRANSFER, createdAt = 1,
        branchId = 1, branchName = "الرئيسي", toBranchId = 2, toBranchName = "الفرع الثاني",
        lines = listOf(StockOperationLineView(10, "حليب السعودية", 4.0)), notes = "شحنة الأسبوع"
    )

    @Test
    fun `search finds an operation by product, branch, number, type or note`() {
        assertTrue(StockOperationHistory.matches(sample, "حليب"))
        assertTrue(StockOperationHistory.matches(sample, "الثاني"))
        assertTrue(StockOperationHistory.matches(sample, "7"))
        assertTrue(StockOperationHistory.matches(sample, "تحويل"))
        assertTrue(StockOperationHistory.matches(sample, "شحنة"))
    }

    @Test
    fun `every word of the search must match - and Arabic spelling variants are tolerated`() {
        assertTrue(StockOperationHistory.matches(sample, "حليب السعوديه"))
        assertTrue(!StockOperationHistory.matches(sample, "حليب رز"))
    }

    @Test
    fun `a blank search matches everything`() {
        assertTrue(StockOperationHistory.matches(sample, "   "))
    }

    @Test
    fun `an unknown product falls back to its id`() {
        val op = group(listOf(move(99, 1, MovementType.RETURN_IN, 1.0, "SALES_RETURN", 1))).single()
        assertEquals("صنف #99", op.lines.single().productName)
    }
}
