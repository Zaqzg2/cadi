package com.inventorysmartai.app.domain.usecase

import com.inventorysmartai.app.domain.model.Goal
import com.inventorysmartai.app.domain.model.GoalAchievement
import com.inventorysmartai.app.domain.repository.SalesRepository
import javax.inject.Inject

/**
 * Achieved quantity comes from real sales data (sum of SalesInvoiceItem quantities for this
 * product within the goal's period) — not a placeholder number. Target is the sum of every
 * group's target, however many groups exist.
 */
class CalculateGoalAchievementUseCase @Inject constructor(
    private val salesRepository: SalesRepository
) {
    suspend operator fun invoke(goal: Goal): GoalAchievement {
        val totalTarget = goal.groups.sumOf { it.targetQuantity }
        val achievedQuantity = salesRepository.getSoldQuantity(
            productId = goal.productId,
            fromDate = goal.periodStart,
            toDate = goal.periodEnd
        )
        val remaining = (totalTarget - achievedQuantity).coerceAtLeast(0.0)
        val percent = if (totalTarget > 0) {
            (achievedQuantity / totalTarget * 100).coerceIn(0.0, 100.0)
        } else 0.0

        return GoalAchievement(
            totalTarget = totalTarget,
            achievedQuantity = achievedQuantity,
            remainingQuantity = remaining,
            achievementPercent = percent
        )
    }
}
