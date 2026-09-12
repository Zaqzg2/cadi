package com.inventorysmartai.app.di

import android.content.Context
import androidx.room.Room
import com.inventorysmartai.app.data.local.database.InventorySmartDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InventorySmartDatabase =
        Room.databaseBuilder(context, InventorySmartDatabase::class.java, InventorySmartDatabase.DATABASE_NAME)
            // Acceptable while there is no shipped v1 to preserve; replace with real
            // Migration objects before the first production release.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideProductDao(db: InventorySmartDatabase): ProductDao = db.productDao()
    @Provides fun provideBranchDao(db: InventorySmartDatabase): BranchDao = db.branchDao()
    @Provides fun provideCategoryDao(db: InventorySmartDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideUnitDao(db: InventorySmartDatabase): UnitDao = db.unitDao()
    @Provides fun provideCustomerDao(db: InventorySmartDatabase): CustomerDao = db.customerDao()
    @Provides fun provideSupplierDao(db: InventorySmartDatabase): SupplierDao = db.supplierDao()
    @Provides fun provideInventoryDao(db: InventorySmartDatabase): InventoryDao = db.inventoryDao()
    @Provides fun provideInventoryMovementDao(db: InventorySmartDatabase): InventoryMovementDao = db.inventoryMovementDao()
    @Provides fun provideCountingDao(db: InventorySmartDatabase): CountingDao = db.countingDao()
    @Provides fun provideGoalDao(db: InventorySmartDatabase): GoalDao = db.goalDao()
    @Provides fun providePurchaseDao(db: InventorySmartDatabase): PurchaseDao = db.purchaseDao()
    @Provides fun provideSalesDao(db: InventorySmartDatabase): SalesDao = db.salesDao()
    @Provides fun provideAttachmentDao(db: InventorySmartDatabase): AttachmentDao = db.attachmentDao()
    @Provides fun provideImportDao(db: InventorySmartDatabase): ImportDao = db.importDao()
    @Provides fun provideAuditLogDao(db: InventorySmartDatabase): AuditLogDao = db.auditLogDao()
}
