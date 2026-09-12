package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.GoalDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.SalesDao
import com.inventorysmartai.app.data.local.database.entity.CommissionEntity
import com.inventorysmartai.app.data.local.database.entity.GoalEntity
import com.inventorysmartai.app.data.local.database.relation.GoalWithCommissions
import com.inventorysmartai.app.domain.model.CommissionGroup
import com.inventorysmartai.app.domain.model.CommissionType
import com.inventorysmartai.app.domain.model.Goal
import com.inventorysmartai.app.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao,
    private val salesDao: SalesDao,
    private val productDao: ProductDao
) : GoalRepository {

    override fun observeGoals(): Flow<List<Goal>> =
        goalDao.observeAllWithCommissions().map { list -> list.map { it.toDomain(productDao) } }

    override fun observeGoal(goalId: Long): Flow<Goal?> =
        goalDao.observeWithCommissions(goalId).map { it?.toDomain(productDao) }

    override fun observeGoalForProduct(productId: Long): Flow<Goal?> =
        goalDao.observeLatestForProduct(productId).map { it?.toDomain(productDao) }

    /** Sums achieved-vs-target across every goal — the real sales data behind "نسبة تحقيق
     *  الأهداف" on the dashboard, not a placeholder percentage. */
    override fun observeOverallAchievementPercent(): Flow<Double> =
        goalDao.observeAllWithCommissions().map { goals ->
            if (goals.isEmpty()) return@map 0.0
            var totalTarget = 0.0
            var totalAchieved = 0.0
            for (g in goals) {
                val target = g.commissions.sumOf { it.targetQuantity }
                val achieved = salesDao.getSoldQuantity(g.goal.productId, g.goal.periodStart, g.goal.periodEnd)
                totalTarget += target
                totalAchieved += achieved
            }
            if (totalTarget <= 0.0) 0.0 else (totalAchieved / totalTarget * 100).coerceIn(0.0, 100.0)
        }

    override suspend fun saveGoal(goal: Goal): Long {
        val now = System.currentTimeMillis()
        val goalId = goalDao.upsertGoal(
            GoalEntity(
                id = goal.id,
                productId = goal.productId,
                periodStart = goal.periodStart,
                periodEnd = goal.periodEnd,
                notes = goal.notes,
                createdAt = now,
                updatedAt = now
            )
        )
        goalDao.clearCommissions(goalId)
        goalDao.insertCommissions(
            goal.groups.map { group ->
                CommissionEntity(
                    goalId = goalId,
                    groupOrder = group.groupOrder,
                    targetQuantity = group.targetQuantity,
                    commissionType = group.commissionType.name,
                    commissionValue = group.commissionValue,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
        return goalId
    }
}

private suspend fun GoalWithCommissions.toDomain(productDao: ProductDao): Goal {
    return Goal(
        id = goal.id,
        productId = goal.productId,
        productName = productDao.getById(goal.productId)?.name,
        periodStart = goal.periodStart,
        periodEnd = goal.periodEnd,
        notes = goal.notes,
        groups = commissions.sortedBy { it.groupOrder }.map {
            CommissionGroup(
                id = it.id,
                goalId = it.goalId,
                groupOrder = it.groupOrder,
                targetQuantity = it.targetQuantity,
                commissionType = CommissionType.valueOf(it.commissionType),
                commissionValue = it.commissionValue
            )
        }
    )
}
