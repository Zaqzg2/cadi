package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.GoalDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.SalesDao
import com.inventorysmartai.app.data.local.database.entity.CommissionEntity
import com.inventorysmartai.app.data.local.database.entity.GoalEntity
import com.inventorysmartai.app.data.local.database.relation.GoalWithCommissions
import com.inventorysmartai.app.domain.goals.GoalTargetSnapshot
import com.inventorysmartai.app.domain.goals.OverallGoalProgress
import com.inventorysmartai.app.domain.model.CommissionGroup
import com.inventorysmartai.app.domain.model.CommissionType
import com.inventorysmartai.app.domain.model.Goal
import com.inventorysmartai.app.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    /**
     * "نسبة تحقيق الأهداف" في الرئيسية: المحقق الفعلي (فواتير مؤكَّدة) ÷ المستهدف لأهداف **الشهر الجاري** فقط،
     * صفّ واحد لكل صنف. تُعاد الحسبة عند تغيّر الأهداف **وعند أي بيع مؤكَّد جديد** (كانت تتجمّد بعد آخر تعديل للأهداف).
     */
    override fun observeOverallAchievementPercent(): Flow<Double> =
        combine(goalDao.observeAllWithCommissions(), salesDao.observeConfirmedInvoiceCount()) { goals, _ -> goals }
            .map { goals ->
                val current = OverallGoalProgress.currentGoals(
                    goals = goals.map {
                        GoalTargetSnapshot(
                            id = it.goal.id,
                            productId = it.goal.productId,
                            periodStart = it.goal.periodStart,
                            periodEnd = it.goal.periodEnd,
                            targetQuantity = it.commissions.sumOf { c -> c.targetQuantity }
                        )
                    },
                    now = System.currentTimeMillis()
                )
                var totalTarget = 0.0
                var totalAchieved = 0.0
                for (g in current) {
                    totalTarget += g.targetQuantity
                    totalAchieved += salesDao.getSoldQuantity(g.productId, g.periodStart, g.periodEnd)
                }
                OverallGoalProgress.percent(totalTarget, totalAchieved)
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

    override suspend fun deleteGoal(goalId: Long) {
        goalDao.deleteGoal(goalId)
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
