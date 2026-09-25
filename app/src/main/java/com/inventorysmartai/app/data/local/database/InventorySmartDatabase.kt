package com.inventorysmartai.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.inventorysmartai.app.data.local.database.dao.AttachmentDao
import com.inventorysmartai.app.data.local.database.dao.AuditLogDao
import com.inventorysmartai.app.data.local.database.dao.BranchDao
import com.inventorysmartai.app.data.local.database.dao.CategoryDao
import com.inventorysmartai.app.data.local.database.dao.CountingDao
import com.inventorysmartai.app.data.local.database.dao.CustomerDao
import com.inventorysmartai.app.data.local.database.dao.GoalDao
import com.inventorysmartai.app.data.local.database.dao.ImportDao
import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.PurchaseDao
import com.inventorysmartai.app.data.local.database.dao.SalesDao
import com.inventorysmartai.app.data.local.database.dao.SupplierDao
import com.inventorysmartai.app.data.local.database.dao.UnitDao
import com.inventorysmartai.app.data.local.database.entity.AttachmentEntity
import com.inventorysmartai.app.data.local.database.entity.AuditLogEntity
import com.inventorysmartai.app.data.local.database.entity.BranchEntity
import com.inventorysmartai.app.data.local.database.entity.CategoryEntity
import com.inventorysmartai.app.data.local.database.entity.CommissionEntity
import com.inventorysmartai.app.data.local.database.entity.CustomerEntity
import com.inventorysmartai.app.data.local.database.entity.GoalEntity
import com.inventorysmartai.app.data.local.database.entity.ImportJobEntity
import com.inventorysmartai.app.data.local.database.entity.ImportMappingTemplateEntity
import com.inventorysmartai.app.data.local.database.entity.ImportRowEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountItemEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryMovementEntity
import com.inventorysmartai.app.data.local.database.entity.ProductEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseReceiptEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseReceiptItemEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestItemEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceItemEntity
import com.inventorysmartai.app.data.local.database.entity.SupplierEntity
import com.inventorysmartai.app.data.local.database.entity.UnitEntity

/**
 * All 22 tables from the ROOM DATA MODEL spec (20 requested + InventoryCountItemEntity, already
 * a justified Phase 1 addition, + PurchaseReceiptItemEntity added in Phase 2 so receipt lines can
 * carry their own cost/batch/expiry instead of just a running total on the request item). No
 * TypeConverters are needed: every column is a Room-native primitive, and enums are stored as
 * their `.name` String.
 *
 * version 2 (Phase 2): every Phase-1 table gained spec-required columns (see the "Phase 2" section of README.md)
 * plus this one new table. version 3 (Phase 3): import_jobs/import_rows gained the columns the
 * real Excel/CSV pipeline needs (importType, sheetName, branch/supplier context, error codes,
 * warnings — see ImportEntities.kt) plus one new table, import_mapping_templates (spec section
 * 20). version 4 (Phase 4): import_jobs gained sourceAttachmentId + metadataJson (AI-document
 * traceability and document-header fields) and attachments gained driveFileId/driveWebViewLink
 * (Drive backup linkage) — see SupportModels.kt's ImportJob/Attachment doc comments. No new
 * tables this phase. Still relying on fallbackToDestructiveMigration (see DatabaseModule) since
 * no production install exists yet to preserve — replace with real Migration objects before the
 * first real release.
 */
@Database(
    entities = [
        ProductEntity::class,
        BranchEntity::class, CategoryEntity::class, UnitEntity::class,
        CustomerEntity::class, SupplierEntity::class,
        InventoryEntity::class, InventoryMovementEntity::class,
        InventoryCountEntity::class, InventoryCountItemEntity::class,
        GoalEntity::class, CommissionEntity::class,
        PurchaseRequestEntity::class, PurchaseRequestItemEntity::class,
        PurchaseReceiptEntity::class, PurchaseReceiptItemEntity::class,
        SalesInvoiceEntity::class, SalesInvoiceItemEntity::class,
        AttachmentEntity::class,
        ImportJobEntity::class, ImportRowEntity::class, ImportMappingTemplateEntity::class,
        AuditLogEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class InventorySmartDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun branchDao(): BranchDao
    abstract fun categoryDao(): CategoryDao
    abstract fun unitDao(): UnitDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun inventoryMovementDao(): InventoryMovementDao
    abstract fun countingDao(): CountingDao
    abstract fun goalDao(): GoalDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun salesDao(): SalesDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun importDao(): ImportDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DATABASE_NAME = "inventory_smart.db"
    }
}
