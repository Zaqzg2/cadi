package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.inventorysmartai.app.data.local.database.entity.CommissionEntity
import com.inventorysmartai.app.data.local.database.entity.GoalEntity
import com.inventorysmartai.app.data.local.database.relation.GoalWithCommissions
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Transaction
    @Query("SELECT * FROM goals ORDER BY periodStart DESC")
    fun observeAllWithCommissions(): Flow<List<GoalWithCommissions>>

    @Transaction
    @Query("SELECT * FROM goals WHERE id = :goalId")
    fun observeWithCommissions(goalId: Long): Flow<GoalWithCommissions?>

    @Transaction
    @Query("SELECT * FROM goals WHERE productId = :productId ORDER BY periodStart DESC LIMIT 1")
    fun observeLatestForProduct(productId: Long): Flow<GoalWithCommissions?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: GoalEntity): Long

    @Query("DELETE FROM commissions WHERE goalId = :goalId")
    suspend fun clearCommissions(goalId: Long)

    @Insert
    suspend fun insertCommissions(commissions: List<CommissionEntity>)
}
