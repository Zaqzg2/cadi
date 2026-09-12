package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Generic attachment row — [ownerType] + [ownerId] point at whichever entity it belongs to,
 *  so Product / PurchaseRequest / SalesInvoice / InventoryCount all share one table. */
@Entity(tableName = "attachments", indices = [Index(value = ["ownerType", "ownerId"])])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerType: String, // AttachmentOwnerType.name
    val ownerId: Long,
    val fileName: String,
    val filePath: String,
    val mimeType: String? = null,
    val createdAt: Long
)
