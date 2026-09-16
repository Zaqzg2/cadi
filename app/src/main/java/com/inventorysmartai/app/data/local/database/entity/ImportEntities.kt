package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema-complete for Phase 2's counting requirements ("acceptedRows != number of unique
 * products" — each of these is tracked independently). No parser/matcher writes real rows
 * here yet; that's the file-import pipeline, still out of scope for this phase (Gemini/OCR/
 * external APIs are explicitly excluded here).
 */
@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceType: String, // ImportSourceType.name
    val fileName: String? = null,
    val status: String, // ImportJobStatus.name
    val totalRows: Int? = null,
    val processedRows: Int? = null,
    val acceptedRows: Int? = null,
    val matchedRows: Int? = null,
    val newProductRows: Int? = null,
    val duplicateRows: Int? = null,
    val errorRows: Int? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
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
    val normalizedData: String? = null,
    val matchedProductId: Long? = null,
    val suggestedProductId: Long? = null,
    val confidence: Double? = null,
    val status: String, // ImportRowStatus.name
    val errorMessage: String? = null,
    val createdAt: Long
)
