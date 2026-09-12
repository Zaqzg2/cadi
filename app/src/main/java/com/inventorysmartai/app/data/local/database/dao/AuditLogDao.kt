package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inventorysmartai.app.data.local.database.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC")
    fun observeFor(entityType: String, entityId: String): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insert(log: AuditLogEntity): Long
}
