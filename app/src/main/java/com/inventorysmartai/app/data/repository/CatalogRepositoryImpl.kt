package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.local.database.dao.BranchDao
import com.inventorysmartai.app.data.local.database.dao.CategoryDao
import com.inventorysmartai.app.data.local.database.dao.CustomerDao
import com.inventorysmartai.app.data.local.database.dao.SupplierDao
import com.inventorysmartai.app.data.local.database.dao.UnitDao
import com.inventorysmartai.app.data.local.database.entity.BranchEntity
import com.inventorysmartai.app.data.local.database.entity.CategoryEntity
import com.inventorysmartai.app.data.local.database.entity.CustomerEntity
import com.inventorysmartai.app.data.local.database.entity.SupplierEntity
import com.inventorysmartai.app.data.local.database.entity.UnitEntity
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Category
import com.inventorysmartai.app.domain.model.Customer
import com.inventorysmartai.app.domain.model.Supplier
import com.inventorysmartai.app.domain.model.UnitOfMeasure
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.PartyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val branchDao: BranchDao,
    private val categoryDao: CategoryDao,
    private val unitDao: UnitDao
) : CatalogRepository {

    override fun observeBranches(): Flow<List<Branch>> =
        branchDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeUnits(): Flow<List<UnitOfMeasure>> =
        unitDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsertBranch(branch: Branch): Long {
        val now = System.currentTimeMillis()
        return branchDao.upsert(
            BranchEntity(branch.id, branch.name, branch.address, branch.phone, branch.isActive, now, now)
        )
    }

    override suspend fun upsertCategory(category: Category): Long {
        val now = System.currentTimeMillis()
        return categoryDao.upsert(CategoryEntity(category.id, category.name, category.isActive, now, now))
    }

    override suspend fun upsertUnit(unit: UnitOfMeasure): Long {
        val now = System.currentTimeMillis()
        return unitDao.upsert(UnitEntity(unit.id, unit.name, unit.symbol, unit.isActive, now, now))
    }

    override suspend fun deleteBranch(id: Long) {
        branchDao.getById(id)?.let { branchDao.delete(it) }
    }

    override suspend fun deleteCategory(id: Long) {
        categoryDao.deleteById(id)
    }

    override suspend fun deleteUnit(id: Long) {
        unitDao.deleteById(id)
    }
}

@Singleton
class PartyRepositoryImpl @Inject constructor(
    private val customerDao: CustomerDao,
    private val supplierDao: SupplierDao
) : PartyRepository {

    override fun observeCustomers(): Flow<List<Customer>> =
        customerDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSuppliers(): Flow<List<Supplier>> =
        supplierDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsertCustomer(customer: Customer): Long {
        val now = System.currentTimeMillis()
        return customerDao.upsert(CustomerEntity(customer.id, customer.name, customer.phone, customer.address, now, now))
    }

    override suspend fun upsertSupplier(supplier: Supplier): Long {
        val now = System.currentTimeMillis()
        return supplierDao.upsert(SupplierEntity(supplier.id, supplier.name, supplier.phone, supplier.address, now, now))
    }
}

private fun BranchEntity.toDomain() = Branch(id, name, address, phone, isActive)
private fun CategoryEntity.toDomain() = Category(id, name, isActive)
private fun UnitEntity.toDomain() = UnitOfMeasure(id, name, symbol, isActive)
private fun CustomerEntity.toDomain() = Customer(id, name, phone, address)
private fun SupplierEntity.toDomain() = Supplier(id, name, phone, address)
