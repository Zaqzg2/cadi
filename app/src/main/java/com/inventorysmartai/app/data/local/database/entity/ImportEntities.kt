package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Bookkeeping only in Phase 1 — no parser/matcher writes real rows here yet. */
@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceType: String, // ImportSourceType.name
    val fileName: String? = null,
    val status: String, // ImportJobStatus.name
    val totalRows: Int? = null,
    val processedRows: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "import_rows",
    foreignKeys = [ForeignKey(entity = ImportJobEntity::class, parentColumns = ["id"], childColumns = ["importJobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("importJobId")]
)
data class ImportRowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val importJobId: Long,
    val rowIndex: Int,
    val rawData: String,
    val matchedProductId: Long? = null,
    val status: String, // ImportRowStatus.name
    val createdAt: Long
)
