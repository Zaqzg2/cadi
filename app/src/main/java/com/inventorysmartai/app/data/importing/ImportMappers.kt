package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.local.database.entity.ImportJobEntity
import com.inventorysmartai.app.data.local.database.entity.ImportMappingTemplateEntity
import com.inventorysmartai.app.data.local.database.entity.ImportRowEntity
import com.inventorysmartai.app.domain.importing.ImportMappingTemplate
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.model.ImportErrorCode
import com.inventorysmartai.app.domain.model.ImportJob
import com.inventorysmartai.app.domain.model.ImportJobStatus
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.model.ImportRowStatus
import com.inventorysmartai.app.domain.model.ImportSourceType

/** Entity <-> domain mapping for the import pipeline, shared between ImportRepositoryImpl and
 *  DefaultImportReviewManager so both read the same enum-parsing rules. Enum values stored as
 *  `.name` strings are parsed leniently (`runCatching { valueOf(...) }.getOrNull()`) wherever the
 *  column was added after the row could already exist (importType/errorCode) — a null there just
 *  means "not set", never a crash. */

fun ImportJobEntity.toDomain(): ImportJob = ImportJob(
    id = id,
    sourceType = ImportSourceType.valueOf(sourceType),
    fileName = fileName,
    status = ImportJobStatus.valueOf(status),
    totalRows = totalRows,
    processedRows = processedRows,
    acceptedRows = acceptedRows,
    matchedRows = matchedRows,
    newProductRows = newProductRows,
    duplicateRows = duplicateRows,
    errorRows = errorRows,
    createdAt = createdAt,
    completedAt = completedAt,
    importType = importType?.let { runCatching { ImportType.valueOf(it) }.getOrNull() },
    sheetName = sheetName,
    defaultBranchId = defaultBranchId,
    defaultSupplierId = defaultSupplierId,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
    rejectedRows = rejectedRows
)

fun ImportRowEntity.toDomain(): ImportRow = ImportRow(
    id = id,
    importJobId = importJobId,
    rowIndex = rowIndex,
    rawData = rawData,
    normalizedData = normalizedData,
    matchedProductId = matchedProductId,
    suggestedProductId = suggestedProductId,
    confidence = confidence,
    status = ImportRowStatus.valueOf(status),
    errorMessage = errorMessage,
    createdAt = createdAt,
    errorCode = errorCode?.let { runCatching { ImportErrorCode.valueOf(it) }.getOrNull() },
    warningsJson = warningsJson,
    edited = edited
)

fun ImportMappingTemplateEntity.toDomain(): ImportMappingTemplate = ImportMappingTemplate(
    id = id,
    name = name,
    importType = ImportType.valueOf(importType),
    headerFingerprint = headerFingerprint,
    mappingJson = mappingJson,
    createdAt = createdAt,
    updatedAt = updatedAt
)
