package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.ImportDao
import com.inventorysmartai.app.data.local.database.entity.ImportJobEntity
import com.inventorysmartai.app.data.local.database.entity.ImportRowEntity
import com.inventorysmartai.app.domain.model.ImportJob
import com.inventorysmartai.app.domain.model.ImportJobStatus
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.model.ImportSourceType
import com.inventorysmartai.app.domain.model.aggregateImportRowCounts
import com.inventorysmartai.app.domain.repository.ImportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 1/2: bookkeeping only — creates a job row so the Data Center screen has something real
 *  to list. finalizeJob lets a future pipeline (still out of scope here — no OCR/AI parsing)
 *  persist its rows and roll the per-status counts up onto the job in one place. */
@Singleton
class ImportRepositoryImpl @Inject constructor(
    private val importDao: ImportDao
) : ImportRepository {

    override fun observeJobs(): Flow<List<ImportJob>> =
        importDao.observeJobs().map { list -> list.map { it.toDomain() } }

    override suspend fun createJob(sourceType: ImportSourceType, fileName: String?): Long {
        val now = System.currentTimeMillis()
        return importDao.insertJob(
            ImportJobEntity(
                sourceType = sourceType.name,
                fileName = fileName,
                status = ImportJobStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun finalizeJob(jobId: Long, rows: List<ImportRow>) {
        val now = System.currentTimeMillis()
        importDao.insertRows(rows.map { it.toEntity(jobId) })
        val counts = aggregateImportRowCounts(rows)
        importDao.updateJobCounts(
            jobId = jobId,
            status = ImportJobStatus.COMPLETED.name,
            totalRows = counts.totalRows,
            processedRows = counts.processedRows,
            acceptedRows = counts.acceptedRows,
            matchedRows = counts.matchedRows,
            newProductRows = counts.newProductRows,
            duplicateRows = counts.duplicateRows,
            errorRows = counts.errorRows,
            completedAt = now,
            updatedAt = now
        )
    }
}

private fun ImportRow.toEntity(jobId: Long) = ImportRowEntity(
    importJobId = jobId,
    rowIndex = rowIndex,
    rawData = rawData,
    normalizedData = normalizedData,
    matchedProductId = matchedProductId,
    suggestedProductId = suggestedProductId,
    confidence = confidence,
    status = status.name,
    errorMessage = errorMessage,
    createdAt = createdAt
)

private fun ImportJobEntity.toDomain() = ImportJob(
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
    completedAt = completedAt
)
