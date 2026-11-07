package com.inventorysmartai.app.domain.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverallGoalProgressTest {

    private fun goal(id: Long, productId: Long, start: Long, end: Long, target: Double = 100.0) =
        GoalTargetSnapshot(id = id, productId = productId, periodStart = start, periodEnd = end, targetQuantity = target)

    @Test
    fun `only goals whose period contains now are current`() {
        val lastMonth = goal(1, productId = 5, start = 0, end = 99)
        val thisMonth = goal(2, productId = 6, start = 100, end = 199)
        val nextMonth = goal(3, productId = 7, start = 200, end = 299)

        val current = OverallGoalProgress.currentGoals(listOf(lastMonth, thisMonth, nextMonth), now = 150)

        assertEquals(listOf(2L), current.map { it.id })
    }

    @Test
    fun `period edges are inclusive`() {
        val g = goal(1, 5, start = 100, end = 199)
        assertEquals(1, OverallGoalProgress.currentGoals(listOf(g), now = 100).size)
        assertEquals(1, OverallGoalProgress.currentGoals(listOf(g), now = 199).size)
        assertTrue(OverallGoalProgress.currentGoals(listOf(g), now = 200).isEmpty())
    }

    @Test
    fun `repeated rows of the same product count once - the newest`() {
        val older = goal(1, productId = 5, start = 100, end = 199, target = 50.0)
        val newer = goal(2, productId = 5, start = 100, end = 199, target = 80.0)

        val current = OverallGoalProgress.currentGoals(listOf(older, newer), now = 150)

        assertEquals(1, current.size)
        assertEquals(2L, current.single().id)
        assertEquals(80.0, current.single().targetQuantity, 0.0)
    }

    @Test
    fun `different products are all kept`() {
        val a = goal(1, productId = 5, start = 100, end = 199)
        val b = goal(2, productId = 6, start = 100, end = 199)
        assertEquals(2, OverallGoalProgress.currentGoals(listOf(a, b), now = 150).size)
    }

    @Test
    fun `no goals gives an empty list`() {
        assertTrue(OverallGoalProgress.currentGoals(emptyList(), now = 150).isEmpty())
    }

    @Test
    fun `percent is achieved over target`() {
        assertEquals(25.0, OverallGoalProgress.percent(totalTarget = 200.0, totalAchieved = 50.0), 0.0001)
    }

    @Test
    fun `percent is capped at 100 when the target is exceeded`() {
        assertEquals(100.0, OverallGoalProgress.percent(totalTarget = 100.0, totalAchieved = 250.0), 0.0)
    }

    @Test
    fun `percent is zero when there is no target - no division by zero`() {
        assertEquals(0.0, OverallGoalProgress.percent(totalTarget = 0.0, totalAchieved = 10.0), 0.0)
    }
}
