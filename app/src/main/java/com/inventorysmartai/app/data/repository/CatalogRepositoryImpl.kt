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
            BranchEntity(id = branch.id, name = branch.name, code = branch.code, address = branch.address, phone = branch.phone, isActive = branch.isActive, createdAt = now, updatedAt = now)
        )
    }

    override suspend fun upsertCategory(category: Category): Long {
        val now = System.currentTimeMillis()
        return categoryDao.upsert(
            CategoryEntity(id = category.id, name = category.name, code = category.code, isActive = category.isActive, createdAt = now, updatedAt = now)
        )
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
        return customerDao.upsert(
            CustomerEntity(
                id = customer.id, customerNumber = customer.customerNumber, name = customer.name,
                phone = customer.phone, address = customer.address, openingBalance = customer.openingBalance,
                isActive = customer.isActive, createdAt = now, updatedAt = now
            )
        )
    }

    override suspend fun upsertSupplier(supplier: Supplier): Long {
        val now = System.currentTimeMillis()
        return supplierDao.upsert(
            SupplierEntity(
                id = supplier.id, supplierNumber = supplier.supplierNumber, name = supplier.name,
                phone = supplier.phone, address = supplier.address, notes = supplier.notes,
                isActive = supplier.isActive, createdAt = now, updatedAt = now
            )
        )
    }
}

private fun BranchEntity.toDomain() = Branch(id = id, name = name, code = code, address = address, phone = phone, isActive = isActive)
private fun CategoryEntity.toDomain() = Category(id = id, name = name, code = code, isActive = isActive)
private fun UnitEntity.toDomain() = UnitOfMeasure(id, name, symbol, isActive)
private fun CustomerEntity.toDomain() = Customer(
    id = id, customerNumber = customerNumber, name = name, phone = phone, address = address,
    openingBalance = openingBalance, isActive = isActive
)
private fun SupplierEntity.toDomain() = Supplier(
    id = id, supplierNumber = supplierNumber, name = name, phone = phone, address = address,
    notes = notes, isActive = isActive
)
