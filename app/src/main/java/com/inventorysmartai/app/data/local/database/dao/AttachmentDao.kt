package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.inventorysmartai.app.data.local.database.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt DESC")
    fun observeFor(ownerType: String, ownerId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AttachmentEntity?

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Set once an attachment has been backed up to the app's Drive folder — see
     *  google/GoogleWorkspaceRepositoryImpl (Phase 4). */
    @Query("UPDATE attachments SET driveFileId = :driveFileId, driveWebViewLink = :driveWebViewLink WHERE id = :id")
    suspend fun updateDriveInfo(id: Long, driveFileId: String, driveWebViewLink: String?)
}
