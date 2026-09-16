package com.inventorysmartai.app.domain.model

data class Attachment(
    val id: Long = 0L,
    val ownerType: AttachmentOwnerType,
    val ownerId: Long,
    val fileName: String,
    val filePath: String,
    val mimeType: String? = null,
    val createdAt: Long
)

data class ImportJob(
    val id: Long = 0L,
    val sourceType: ImportSourceType,
    val fileName: String? = null,
    val status: ImportJobStatus,
    val totalRows: Int? = null,
    val processedRows: Int? = null,
    val acceptedRows: Int? = null,
    val matchedRows: Int? = null,
    val newProductRows: Int? = null,
    val duplicateRows: Int? = null,
    val errorRows: Int? = null,
    val createdAt: Long,
    val completedAt: Long? = null
)

data class ImportRow(
    val id: Long = 0L,
    val importJobId: Long,
    val rowIndex: Int,
    val rawData: String,
    val normalizedData: String? = null,
    val matchedProductId: Long? = null,
    val suggestedProductId: Long? = null,
    val confidence: Double? = null,
    val status: ImportRowStatus,
    val errorMessage: String? = null,
    val createdAt: Long
)

/**
 * Pure aggregation used to build [ImportJob]'s row-count columns from a batch of rows once the
 * import pipeline actually classifies them — kept separate from any DAO/Room so it's plain-JUnit
 * testable (see ImportRowAggregationTest). Deliberately counts "accepted" independently from
 * "matched an existing product" per the spec's explicit warning that the two are not the same
 * number (a row can be accepted as a brand-new product, matching nothing).
 */
fun aggregateImportRowCounts(rows: List<ImportRow>): ImportJobRowCounts = ImportJobRowCounts(
    totalRows = rows.size,
    processedRows = rows.count { it.status != ImportRowStatus.PENDING },
    acceptedRows = rows.count { it.status == ImportRowStatus.ACCEPTED },
    matchedRows = rows.count { it.status == ImportRowStatus.MATCHED },
    newProductRows = rows.count { it.status == ImportRowStatus.NEW_PRODUCT },
    duplicateRows = rows.count { it.status == ImportRowStatus.DUPLICATE },
    errorRows = rows.count { it.status == ImportRowStatus.ERROR }
)

data class ImportJobRowCounts(
    val totalRows: Int,
    val processedRows: Int,
    val acceptedRows: Int,
    val matchedRows: Int,
    val newProductRows: Int,
    val duplicateRows: Int,
    val errorRows: Int
)

data class AuditLog(
    val id: Long = 0L,
    val entityType: String,
    val entityId: String,
    val action: AuditAction,
    val performedBy: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val createdAt: Long
)
