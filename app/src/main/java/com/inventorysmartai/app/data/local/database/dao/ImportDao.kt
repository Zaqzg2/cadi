package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inventorysmartai.app.data.local.database.entity.ImportJobEntity
import com.inventorysmartai.app.data.local.database.entity.ImportRowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_jobs ORDER BY createdAt DESC")
    fun observeJobs(): Flow<List<ImportJobEntity>>

    @Insert
    suspend fun insertJob(job: ImportJobEntity): Long

    @Insert
    suspend fun insertRows(rows: List<ImportRowEntity>)

    @Query("SELECT * FROM import_rows WHERE importJobId = :jobId")
    suspend fun getRowsForJob(jobId: Long): List<ImportRowEntity>

    @Query(
        """UPDATE import_jobs SET status = :status, totalRows = :totalRows, processedRows = :processedRows,
           acceptedRows = :acceptedRows, matchedRows = :matchedRows, newProductRows = :newProductRows,
           duplicateRows = :duplicateRows, errorRows = :errorRows, completedAt = :completedAt, updatedAt = :updatedAt
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
        completedAt: Long,
        updatedAt: Long
    )
}
