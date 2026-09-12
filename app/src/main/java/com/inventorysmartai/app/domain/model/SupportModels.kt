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
    val createdAt: Long
)

data class ImportRow(
    val id: Long = 0L,
    val importJobId: Long,
    val rowIndex: Int,
    val rawData: String,
    val matchedProductId: Long? = null,
    val status: ImportRowStatus,
    val createdAt: Long
)

data class AuditLog(
    val id: Long = 0L,
    val entityType: String,
    val entityId: String,
    val action: AuditAction,
    val performedBy: String? = null,
    val details: String? = null,
    val createdAt: Long
)
