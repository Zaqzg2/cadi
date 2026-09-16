package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs", indices = [Index(value = ["entityType", "entityId"])])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entityType: String,
    val entityId: String,
    val action: String, // AuditAction.name
    val performedBy: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val createdAt: Long
)
