package com.inventorysmartai.app.di

import com.inventorysmartai.app.data.importing.AndroidContentImportSource
import com.inventorysmartai.app.data.importing.DefaultImportEngine
import com.inventorysmartai.app.data.importing.DefaultImportReviewManager
import com.inventorysmartai.app.data.importing.DeterministicProductMatcher
import com.inventorysmartai.app.data.importing.ImportEngine
import com.inventorysmartai.app.data.repository.AttachmentRepositoryImpl
import com.inventorysmartai.app.data.repository.CatalogRepositoryImpl
import com.inventorysmartai.app.data.repository.CountingRepositoryImpl
import com.inventorysmartai.app.data.repository.GoalRepositoryImpl
import com.inventorysmartai.app.data.repository.ImportRepositoryImpl
import com.inventorysmartai.app.data.repository.PartyRepositoryImpl
import com.inventorysmartai.app.data.repository.ProductRepositoryImpl
import com.inventorysmartai.app.data.repository.PurchaseRepositoryImpl
import com.inventorysmartai.app.data.repository.ReportsRepositoryImpl
import com.inventorysmartai.app.data.repository.SalesRepositoryImpl
import com.inventorysmartai.app.data.repository.SettingsRepositoryImpl
import com.inventorysmartai.app.domain.importing.ColumnMapper
import com.inventorysmartai.app.domain.importing.DefaultColumnMapper
import com.inventorysmartai.app.domain.importing.DefaultDuplicateDetector
import com.inventorysmartai.app.domain.importing.DefaultImportPipeline
import com.inventorysmartai.app.domain.importing.DefaultImportValidator
import com.inventorysmartai.app.domain.importing.DefaultNormalizer
import com.inventorysmartai.app.domain.importing.DuplicateDetector
import com.inventorysmartai.app.domain.importing.ImportPipeline
import com.inventorysmartai.app.domain.importing.ImportReviewManager
import com.inventorysmartai.app.domain.importing.ImportSource
import com.inventorysmartai.app.domain.importing.ImportValidator
import com.inventorysmartai.app.domain.importing.Normalizer
import com.inventorysmartai.app.domain.importing.ProductMatcher
import com.inventorysmartai.app.domain.repository.AttachmentRepository
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.CountingRepository
import com.inventorysmartai.app.domain.repository.GoalRepository
import com.inventorysmartai.app.domain.repository.ImportRepository
import com.inventorysmartai.app.domain.repository.PartyRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import com.inventorysmartai.app.domain.repository.ReportsRepository
import com.inventorysmartai.app.domain.repository.SalesRepository
import com.inventorysmartai.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository
    @Binds @Singleton abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository
    @Binds @Singleton abstract fun bindPartyRepository(impl: PartyRepositoryImpl): PartyRepository
    @Binds @Singleton abstract fun bindCountingRepository(impl: CountingRepositoryImpl): CountingRepository
    @Binds @Singleton abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository
    @Binds @Singleton abstract fun bindPurchaseRepository(impl: PurchaseRepositoryImpl): PurchaseRepository
    @Binds @Singleton abstract fun bindSalesRepository(impl: SalesRepositoryImpl): SalesRepository
    @Binds @Singleton abstract fun bindReportsRepository(impl: ReportsRepositoryImpl): ReportsRepository
    @Binds @Singleton abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds @Singleton abstract fun bindAttachmentRepository(impl: AttachmentRepositoryImpl): AttachmentRepository
    @Binds @Singleton abstract fun bindImportRepository(impl: ImportRepositoryImpl): ImportRepository
    @Binds @Singleton abstract fun bindProductMatcher(impl: DeterministicProductMatcher): ProductMatcher

    // --- Phase 3: bulk-import pipeline components ---
    @Binds @Singleton abstract fun bindImportSource(impl: AndroidContentImportSource): ImportSource
    @Binds @Singleton abstract fun bindImportEngine(impl: DefaultImportEngine): ImportEngine
    @Binds @Singleton abstract fun bindColumnMapper(impl: DefaultColumnMapper): ColumnMapper
    @Binds @Singleton abstract fun bindNormalizer(impl: DefaultNormalizer): Normalizer
    @Binds @Singleton abstract fun bindImportValidator(impl: DefaultImportValidator): ImportValidator
    @Binds @Singleton abstract fun bindDuplicateDetector(impl: DefaultDuplicateDetector): DuplicateDetector
    @Binds @Singleton abstract fun bindImportPipeline(impl: DefaultImportPipeline): ImportPipeline
    @Binds @Singleton abstract fun bindImportReviewManager(impl: DefaultImportReviewManager): ImportReviewManager
}
