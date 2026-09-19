package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema-complete for Phase 2's counting requirements ("acceptedRows != number of unique
 * products" — each of these is tracked independently). Phase 3 wires up the real Excel/CSV
 * pipeline that actually writes rows here (see data/importing and ImportRepositoryImpl); the
 * Phase 3 additions below are all nullable/defaulted so nothing that already constructs these
 * entities with named arguments needs to change.
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
    val updatedAt: Long,
    // --- Phase 3 additions ---
    val importType: String? = null, // ImportType.name
    val sheetName: String? = null,
    val defaultBranchId: Long? = null,
    val defaultSupplierId: Long? = null,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val rejectedRows: Int? = null
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
    val createdAt: Long,
    // --- Phase 3 additions ---
    val errorCode: String? = null, // ImportErrorCode.name
    val warningsJson: String? = null,
    val edited: Boolean = false
)

/** Phase 3, spec section 20 ("Import Templates"). One saved column mapping for a given
 *  [com.inventorysmartai.app.domain.importing.ImportType], offered again on a future import
 *  whose headers sufficiently match (see ImportMappingTemplate.structureMatchRatio) — never
 *  applied automatically below that threshold. */
@Entity(tableName = "import_mapping_templates")
data class ImportMappingTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val importType: String, // ImportType.name
    val headerFingerprint: String,
    val mappingJson: String,
    val createdAt: Long,
    val updatedAt: Long
)
