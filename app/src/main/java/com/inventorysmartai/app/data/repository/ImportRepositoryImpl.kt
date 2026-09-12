package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.ImportDao
import com.inventorysmartai.app.data.local.database.entity.ImportJobEntity
import com.inventorysmartai.app.domain.model.ImportJob
import com.inventorysmartai.app.domain.model.ImportJobStatus
import com.inventorysmartai.app.domain.model.ImportSourceType
import com.inventorysmartai.app.domain.repository.ImportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 1: bookkeeping only — creates a job row so the Data Center screen has something real
 *  to list, but nothing parses or matches rows yet (see domain/importing for the future contracts). */
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
}

private fun ImportJobEntity.toDomain() = ImportJob(
    id = id,
    sourceType = ImportSourceType.valueOf(sourceType),
    fileName = fileName,
    status = ImportJobStatus.valueOf(status),
    totalRows = totalRows,
    processedRows = processedRows,
    createdAt = createdAt
)
