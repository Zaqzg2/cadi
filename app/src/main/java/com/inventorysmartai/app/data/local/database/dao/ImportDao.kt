package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.inventorysmartai.app.data.local.database.entity.ImportJobEntity
import com.inventorysmartai.app.data.local.database.entity.ImportMappingTemplateEntity
import com.inventorysmartai.app.data.local.database.entity.ImportRowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_jobs ORDER BY createdAt DESC")
    fun observeJobs(): Flow<List<ImportJobEntity>>

    @Query("SELECT * FROM import_jobs WHERE id = :jobId")
    fun observeJob(jobId: Long): Flow<ImportJobEntity?>

    @Query("SELECT * FROM import_jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: Long): ImportJobEntity?

    @Insert
    suspend fun insertJob(job: ImportJobEntity): Long

    @Insert
    suspend fun insertRows(rows: List<ImportRowEntity>)

    @Query("SELECT * FROM import_rows WHERE importJobId = :jobId ORDER BY rowIndex ASC")
    suspend fun getRowsForJob(jobId: Long): List<ImportRowEntity>

    @Query("SELECT * FROM import_rows WHERE importJobId = :jobId ORDER BY rowIndex ASC")
    fun observeRowsForJob(jobId: Long): Flow<List<ImportRowEntity>>

    @Query("SELECT * FROM import_rows WHERE id = :rowId")
    suspend fun getRowById(rowId: Long): ImportRowEntity?

    @Update
    suspend fun updateRow(row: ImportRowEntity)

    /** Re-analyzing a job (a different sheet, or a changed column mapping) replaces its pending
     *  rows rather than appending to them — see ImportRepository.persistAnalysis. */
    @Query("DELETE FROM import_rows WHERE importJobId = :jobId")
    suspend fun deleteRowsForJob(jobId: Long)

    @Query("UPDATE import_jobs SET sheetName = :sheetName, updatedAt = :updatedAt WHERE id = :jobId")
    suspend fun updateJobSheet(jobId: Long, sheetName: String?, updatedAt: Long)

    @Query("UPDATE import_jobs SET status = :status, updatedAt = :updatedAt WHERE id = :jobId")
    suspend fun updateJobStatus(jobId: Long, status: String, updatedAt: Long)

    @Query(
        """UPDATE import_jobs SET status = :status, totalRows = :totalRows, processedRows = :processedRows,
           acceptedRows = :acceptedRows, matchedRows = :matchedRows, newProductRows = :newProductRows,
           duplicateRows = :duplicateRows, errorRows = :errorRows, updatedAt = :updatedAt
           WHERE id = :jobId"""
    )
    suspend fun updateJobCounts(
        jobId: Long,
        status: String,
        totalRows: Int,
        processedRows: Int,
        acceptedRows: Int,
        matchedRows: Int,
        newProductRows: Int,
        duplicateRows: Int,
        errorRows: Int,
        updatedAt: Long
    )

    @Query(
        """UPDATE import_jobs SET status = :status, rejectedRows = :rejectedRows, completedAt = :completedAt,
           updatedAt = :updatedAt WHERE id = :jobId"""
    )
    suspend fun updateJobApprovalOutcome(jobId: Long, status: String, rejectedRows: Int, completedAt: Long?, updatedAt: Long)

    // --- Import mapping templates ---

    @Query("SELECT * FROM import_mapping_templates WHERE importType = :importType ORDER BY updatedAt DESC")
    fun observeTemplates(importType: String): Flow<List<ImportMappingTemplateEntity>>

    @Query("SELECT * FROM import_mapping_templates WHERE importType = :importType ORDER BY updatedAt DESC")
    suspend fun getTemplates(importType: String): List<ImportMappingTemplateEntity>

    @Insert
    suspend fun insertTemplate(template: ImportMappingTemplateEntity): Long
}
