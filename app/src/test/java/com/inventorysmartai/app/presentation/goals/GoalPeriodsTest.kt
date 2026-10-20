package com.inventorysmartai.app.presentation.goals

import com.inventorysmartai.app.domain.model.Goal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Calendar

class GoalPeriodsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun goal(id: Long, productId: Long, start: Long, name: String? = null) =
        Goal(id = id, productId = productId, productName = name, periodStart = start, periodEnd = start)

    // ------------------------------------------------------------------ monthKey

    @Test
    fun `moments in the same month share a key whatever the day or hour`() {
        assertEquals(
            monthKey(at(2026, Calendar.OCTOBER, 1, 0, 5)),
            monthKey(at(2026, Calendar.OCTOBER, 31, 23, 59))
        )
    }

    @Test
    fun `neighbouring months and years have different keys`() {
        assertNotEquals(monthKey(at(2026, Calendar.OCTOBER, 15)), monthKey(at(2026, Calendar.NOVEMBER, 15)))
        // December -> January across the year boundary is exactly one step
        assertEquals(monthKey(at(2025, Calendar.DECEMBER, 15)) + 1, monthKey(at(2026, Calendar.JANUARY, 15)))
    }

    // ------------------------------------------------------------------ monthBounds

    @Test
    fun `a month starts at midnight of the first day, not at the hour the goal was saved`() {
        val (start, _) = monthBounds(at(2026, Calendar.OCTOBER, 19, 21, 44))
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0, 0), start)
    }

    @Test
    fun `a month ends one millisecond before the next one begins`() {
        val (_, end) = monthBounds(at(2026, Calendar.OCTOBER, 19, 21, 44))
        assertEquals(at(2026, Calendar.NOVEMBER, 1, 0, 0) - 1, end)
    }

    @Test
    fun `the bounds stay inside one month and the next instant is the following month`() {
        val (start, end) = monthBounds(at(2026, Calendar.FEBRUARY, 10))
        assertEquals(monthKey(start), monthKey(end))
        assertEquals(monthKey(start) + 1, monthKey(end + 1))
    }

    @Test
    fun `bounds are the same whichever moment of the month they are computed from`() {
        assertEquals(
            monthBounds(at(2026, Calendar.OCTOBER, 1, 0, 1)),
            monthBounds(at(2026, Calendar.OCTOBER, 30, 23, 0))
        )
    }

    // ------------------------------------------------------------------ latestPerProductMonth

    @Test
    fun `repeated saves of the same product and month collapse into the newest row`() {
        val older = goal(id = 1, productId = 5, start = at(2026, Calendar.OCTOBER, 1, 10, 0), name = "حليب")
        val newer = goal(id = 2, productId = 5, start = at(2026, Calendar.OCTOBER, 1, 21, 0), name = "حليب")

        val shown = listOf(older, newer).latestPerProductMonth()

        assertEquals(listOf(2L), shown.map { it.id })
    }

    @Test
    fun `different products and different months are kept apart`() {
        val milkOct = goal(1, 5, at(2026, Calendar.OCTOBER, 1), "حليب")
        val milkNov = goal(2, 5, at(2026, Calendar.NOVEMBER, 1), "حليب")
        val riceOct = goal(3, 6, at(2026, Calendar.OCTOBER, 1), "رز")

        val shown = listOf(milkOct, milkNov, riceOct).latestPerProductMonth()

        assertEquals(3, shown.size)
    }

    @Test
    fun `the list is newest month first, then by product name`() {
        val rice = goal(1, 6, at(2026, Calendar.OCTOBER, 1), "رز")
        val milk = goal(2, 5, at(2026, Calendar.OCTOBER, 1), "حليب")
        val sugarNov = goal(3, 7, at(2026, Calendar.NOVEMBER, 1), "سكر")

        val shown = listOf(rice, milk, sugarNov).latestPerProductMonth()

        // November first; inside October the names are compared as text: «حليب» (ح) before «رز» (ر)
        assertEquals(listOf(3L, 2L, 1L), shown.map { it.id })
    }
}
