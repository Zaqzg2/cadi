package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goals",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("productId")]
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * One "group/tier" row per Goal — this is how the model stays flexible instead of hard-coding
 * three groups: a Goal simply has as many CommissionEntity rows as it needs.
 */
@Entity(
    tableName = "commissions",
    foreignKeys = [ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("goalId")]
)
data class CommissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val goalId: Long,
    val groupOrder: Int,
    val targetQuantity: Double,
    val commissionType: String, // CommissionType.name
    val commissionValue: Double,
    val createdAt: Long,
    val updatedAt: Long
)
