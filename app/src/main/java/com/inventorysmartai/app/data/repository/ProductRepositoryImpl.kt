package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.BranchDao
import com.inventorysmartai.app.data.local.database.dao.CategoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.UnitDao
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.ProductEntity
import com.inventorysmartai.app.data.local.datastore.SettingsLocalDataSource
import com.inventorysmartai.app.domain.inventory.InventoryStatusCalculator
import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import com.inventorysmartai.app.domain.model.BranchStock
import com.inventorysmartai.app.domain.model.InventoryMovement
import com.inventorysmartai.app.domain.model.MovementType
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val inventoryDao: InventoryDao,
    private val inventoryMovementDao: InventoryMovementDao,
    private val branchDao: BranchDao,
    private val categoryDao: CategoryDao,
    private val unitDao: UnitDao,
    private val settings: SettingsLocalDataSource
) : ProductRepository {

    override fun observeProducts(): Flow<List<Product>> =
        combine(productDao.observeAll(), categoryDao.observeAll(), unitDao.observeAll()) { products, categories, units ->
            val categoryNames = categories.associate { it.id to it.name }
            val unitNames = units.associate { it.id to it.name }
            products.map { it.toDomain(categoryNames[it.categoryId], unitNames[it.unitId]) }
        }

    override fun observeProductsWithStock(): Flow<List<ProductStockSummary>> =
        combine(
            observeProducts(),
            inventoryDao.observeAll(),
            branchDao.observeAll(),
            settings.nearExpiryWindowDays
        ) { products, inventoryRows, branches, nearExpiryWindowDays ->
            val branchNames = branches.associate { it.id to it.name }
            val byProduct = inventoryRows.groupBy { it.productId }
            val now = System.currentTimeMillis()

            products.map { product ->
                buildSummary(product, byProduct[product.id].orEmpty(), branchNames, nearExpiryWindowDays, now)
            }
        }

    override fun observeProductDetail(productId: Long): Flow<ProductStockSummary?> {
        // Bundle the three reference-data lookups first so the outer combine stays within
        // kotlinx.coroutines' typed 4-flow overload — no untyped Array<*> casting needed.
        val referenceData = combine(
            categoryDao.observeAll(),
            unitDao.observeAll(),
            branchDao.observeAll()
        ) { categories, units, branches -> Triple(categories, units, branches) }

        return combine(
            productDao.observeById(productId),
            inventoryDao.observeByProduct(productId),
            settings.nearExpiryWindowDays,
            referenceData
        ) { entity, stockRows, nearExpiryWindowDays, (categories, units, branches) ->
            if (entity == null) return@combine null
            val product = entity.toDomain(
                categories.find { it.id == entity.categoryId }?.name,
                units.find { it.id == entity.unitId }?.name
            )
            val branchNames = branches.associate { it.id to it.name }
            buildSummary(product, stockRows, branchNames, nearExpiryWindowDays, System.currentTimeMillis())
        }
    }

    override fun observeMovements(productId: Long): Flow<List<InventoryMovement>> =
        inventoryMovementDao.observeByProduct(productId).map { rows ->
            rows.map {
                InventoryMovement(
                    id = it.id,
                    productId = it.productId,
                    branchId = it.branchId,
                    movementType = MovementType.valueOf(it.movementType),
                    quantityChange = it.quantityChange,
                    referenceType = it.referenceType,
                    referenceId = it.referenceId,
                    notes = it.notes,
                    createdAt = it.createdAt
                )
            }
        }

    override suspend fun getByBarcode(barcode: String): Product? =
        productDao.getByBarcode(barcode)?.toDomain(null, null)

    override suspend fun getByItemNumber(itemNumber: String): Product? =
        productDao.getByItemNumber(itemNumber)?.toDomain(null, null)

    override suspend fun upsert(product: Product): Long {
        val now = System.currentTimeMillis()
        val existing = if (product.id != 0L) productDao.getById(product.id) else null
        val entity = ProductEntity(
            id = product.id,
            itemNumber = product.itemNumber,
            barcode = product.barcode,
            name = product.name,
            normalizedName = ArabicTextNormalizer.normalize(product.name),
            alternateNames = product.alternateNames,
            categoryId = product.categoryId,
            unitId = product.unitId,
            minStock = product.minStock,
            reorderPoint = product.reorderPoint,
            hasExpiry = product.hasExpiry,
            defaultPrice = product.defaultPrice,
            isActive = product.isActive,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return productDao.upsert(entity)
    }

    private fun buildSummary(
        product: Product,
        stockRows: List<InventoryEntity>,
        branchNames: Map<Long, String>,
        nearExpiryWindowDays: Int,
        now: Long
    ): ProductStockSummary {
        val branchStocks = stockRows.map {
            BranchStock(
                branchId = it.branchId,
                branchName = branchNames[it.branchId] ?: "",
                quantity = it.quantity,
                batchNumber = it.batchNumber,
                expiryDate = it.expiryDate
            )
        }
        val totalQuantity = branchStocks.sumOf { it.quantity }
        val nearestExpiryDate = branchStocks.mapNotNull { it.expiryDate }.minOrNull()
        val status = InventoryStatusCalculator.compute(
            hasExpiry = product.hasExpiry,
            minStock = product.minStock,
            totalQuantity = totalQuantity,
            nearestExpiryDate = nearestExpiryDate,
            nearExpiryWindowDays = nearExpiryWindowDays,
            now = now
        )
        return ProductStockSummary(product, branchStocks, totalQuantity, nearestExpiryDate, status)
    }

}

internal fun ProductEntity.toDomain(categoryName: String?, unitName: String?) = Product(
    id = id,
    itemNumber = itemNumber,
    barcode = barcode,
    name = name,
    alternateNames = alternateNames,
    categoryId = categoryId,
    categoryName = categoryName,
    unitId = unitId,
    unitName = unitName,
    minStock = minStock,
    reorderPoint = reorderPoint,
    hasExpiry = hasExpiry,
    defaultPrice = defaultPrice,
    isActive = isActive
)
