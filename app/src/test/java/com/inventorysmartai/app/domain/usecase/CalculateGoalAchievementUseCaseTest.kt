package com.inventorysmartai.app.domain.usecase

import com.inventorysmartai.app.domain.model.CommissionGroup
import com.inventorysmartai.app.domain.model.CommissionType
import com.inventorysmartai.app.domain.model.Goal
import com.inventorysmartai.app.fakes.FakeSalesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateGoalAchievementUseCaseTest {

    private fun goal(vararg targets: Double) = Goal(
        productId = 1L,
        periodStart = 0L,
        periodEnd = 1L,
        groups = targets.mapIndexed { index, target ->
            CommissionGroup(groupOrder = index + 1, targetQuantity = target, commissionType = CommissionType.FIXED, commissionValue = 10.0)
        }
    )

    @Test
    fun `target is the sum across every group, however many there are`() = runBlocking {
        val useCase = CalculateGoalAchievementUseCase(FakeSalesRepository(soldQuantity = 0.0))
        val result = useCase(goal(100.0, 50.0, 25.0))
        assertEquals(175.0, result.totalTarget, 0.001)
    }

    @Test
    fun `achievement percent is achieved over target, capped at 100`() = runBlocking {
        val useCase = CalculateGoalAchievementUseCase(FakeSalesRepository(soldQuantity = 150.0))
        val result = useCase(goal(100.0))
        assertEquals(100.0, result.achievementPercent, 0.001)
        assertEquals(0.0, result.remainingQuantity, 0.001)
    }

    @Test
    fun `partial achievement computes remaining and percent correctly`() = runBlocking {
        val useCase = CalculateGoalAchievementUseCase(FakeSalesRepository(soldQuantity = 40.0))
        val result = useCase(goal(100.0, 60.0)) // total target 160
        assertEquals(40.0, result.achievedQuantity, 0.001)
        assertEquals(120.0, result.remainingQuantity, 0.001)
        assertEquals(25.0, result.achievementPercent, 0.001)
    }

    @Test
    fun `zero target never divides by zero`() = runBlocking {
        val useCase = CalculateGoalAchievementUseCase(FakeSalesRepository(soldQuantity = 10.0))
        val result = useCase(goal()) // no groups at all
        assertEquals(0.0, result.totalTarget, 0.001)
        assertEquals(0.0, result.achievementPercent, 0.001)
    }
}
