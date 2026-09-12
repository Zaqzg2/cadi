package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.AttachmentDao
import com.inventorysmartai.app.data.local.database.entity.AttachmentEntity
import com.inventorysmartai.app.domain.model.Attachment
import com.inventorysmartai.app.domain.model.AttachmentOwnerType
import com.inventorysmartai.app.domain.repository.AttachmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    private val attachmentDao: AttachmentDao
) : AttachmentRepository {

    override fun observeAttachments(ownerType: AttachmentOwnerType, ownerId: Long): Flow<List<Attachment>> =
        attachmentDao.observeFor(ownerType.name, ownerId).map { list -> list.map { it.toDomain() } }

    override suspend fun addAttachment(attachment: Attachment): Long =
        attachmentDao.insert(
            AttachmentEntity(
                ownerType = attachment.ownerType.name,
                ownerId = attachment.ownerId,
                fileName = attachment.fileName,
                filePath = attachment.filePath,
                mimeType = attachment.mimeType,
                createdAt = attachment.createdAt
            )
        )

    override suspend fun deleteAttachment(id: Long) = attachmentDao.deleteById(id)
}

private fun AttachmentEntity.toDomain() = Attachment(
    id = id,
    ownerType = AttachmentOwnerType.valueOf(ownerType),
    ownerId = ownerId,
    fileName = fileName,
    filePath = filePath,
    mimeType = mimeType,
    createdAt = createdAt
)
