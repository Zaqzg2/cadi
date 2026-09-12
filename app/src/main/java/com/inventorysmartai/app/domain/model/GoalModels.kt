package com.inventorysmartai.app.domain.model

/**
 * One product's goal for a period. [groups] is a plain list — the UI may default to showing
 * three rows for a brand-new goal, but the model itself never assumes a fixed count.
 */
data class Goal(
    val id: Long = 0L,
    val productId: Long,
    val productName: String? = null,
    val periodStart: Long,
    val periodEnd: Long,
    val notes: String? = null,
    val groups: List<CommissionGroup> = emptyList()
)

/** One "group/tier": a target quantity paired with its commission — Group 1 / 2 / 3 / ... N. */
data class CommissionGroup(
    val id: Long = 0L,
    val goalId: Long = 0L,
    val groupOrder: Int,
    val targetQuantity: Double,
    val commissionType: CommissionType,
    val commissionValue: Double
)

/** Computed, not stored — see CalculateGoalAchievementUseCase. */
data class GoalAchievement(
    val totalTarget: Double,
    val achievedQuantity: Double,
    val remainingQuantity: Double,
    val achievementPercent: Double
)
