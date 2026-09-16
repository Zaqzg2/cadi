package com.inventorysmartai.app.fakes

import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Minimal in-memory stand-in for ProductDao — plain JUnit tests here have no Room/Robolectric,
 *  so anything that needs "a database" for its logic gets a fake like this instead. */
class FakeProductDao(seed: List<ProductEntity> = emptyList()) : ProductDao {
    private var nextId = (seed.maxOfOrNull { it.id } ?: 0L) + 1
    private val state = MutableStateFlow(seed.associateBy { it.id })

    override fun observeAll(): Flow<List<ProductEntity>> = state.map { it.values.sortedBy { p -> p.name } }

    override fun observeById(id: Long): Flow<ProductEntity?> = state.map { it[id] }

    override suspend fun getById(id: Long): ProductEntity? = state.value[id]

    override suspend fun getByBarcode(barcode: String): ProductEntity? =
        state.value.values.firstOrNull { it.barcode == barcode }

    override suspend fun getByItemNumber(itemNumber: String): ProductEntity? =
        state.value.values.firstOrNull { it.itemNumber == itemNumber }

    override suspend fun getByNormalizedName(normalizedName: String): ProductEntity? =
        state.value.values.firstOrNull { it.normalizedName == normalizedName }

    override suspend fun upsert(product: ProductEntity): Long {
        val id = if (product.id != 0L) product.id else nextId++
        state.value = state.value + (id to product.copy(id = id))
        return id
    }

    override suspend fun update(product: ProductEntity) {
        state.value = state.value + (product.id to product)
    }

    override suspend fun delete(product: ProductEntity) {
        state.value = state.value - product.id
    }
}
