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
import com.inventorysmartai.app.domain.repository.PartyDeleteResult
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

    override suspend fun renameBranch(id: Long, name: String) = branchDao.rename(id, name.trim(), System.currentTimeMillis())
    override suspend fun renameCategory(id: Long, name: String) = categoryDao.rename(id, name.trim(), System.currentTimeMillis())
    override suspend fun renameUnit(id: Long, name: String) = unitDao.rename(id, name.trim(), System.currentTimeMillis())

    override suspend fun branchUsageCount(id: Long): Int = branchDao.stockRowCount(id)
    override suspend fun categoryUsageCount(id: Long): Int = categoryDao.productCount(id)
    override suspend fun unitUsageCount(id: Long): Int = unitDao.productCount(id)
}

@Singleton
class PartyRepositoryImpl @Inject constructor(
    private val customerDao: CustomerDao,
    private val supplierDao: SupplierDao
) : PartyRepository {

    override fun observeCustomers(): Flow<List<Customer>> =
        customerDao.observeByActive(true).map { list -> list.map { it.toDomain() } }

    override fun observeSuppliers(): Flow<List<Supplier>> =
        supplierDao.observeByActive(true).map { list -> list.map { it.toDomain() } }

    override fun observeArchivedCustomers(): Flow<List<Customer>> =
        customerDao.observeByActive(false).map { list -> list.map { it.toDomain() } }

    override fun observeArchivedSuppliers(): Flow<List<Supplier>> =
        supplierDao.observeByActive(false).map { list -> list.map { it.toDomain() } }

    override suspend fun updateCustomer(customer: Customer) {
        val existing = customerDao.getById(customer.id) ?: throw IllegalArgumentException("العميل غير موجود")
        customerDao.update(
            existing.copy(
                customerNumber = customer.customerNumber, name = customer.name.trim(), phone = customer.phone,
                address = customer.address, openingBalance = customer.openingBalance, isActive = customer.isActive,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun updateSupplier(supplier: Supplier) {
        val existing = supplierDao.getById(supplier.id) ?: throw IllegalArgumentException("المورّد غير موجود")
        supplierDao.update(
            existing.copy(
                supplierNumber = supplier.supplierNumber, name = supplier.name.trim(), phone = supplier.phone,
                address = supplier.address, notes = supplier.notes, isActive = supplier.isActive,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteCustomer(id: Long): PartyDeleteResult {
        val existing = customerDao.getById(id) ?: return PartyDeleteResult(deleted = true)
        val used = customerDao.invoiceCount(id)
        if (used > 0) return PartyDeleteResult(deleted = false, blockedByDocuments = used)
        customerDao.delete(existing)
        return PartyDeleteResult(deleted = true)
    }

    override suspend fun deleteSupplier(id: Long): PartyDeleteResult {
        val existing = supplierDao.getById(id) ?: return PartyDeleteResult(deleted = true)
        val used = supplierDao.documentCount(id)
        if (used > 0) return PartyDeleteResult(deleted = false, blockedByDocuments = used)
        supplierDao.delete(existing)
        return PartyDeleteResult(deleted = true)
    }

    override suspend fun setCustomerActive(id: Long, active: Boolean) {
        customerDao.getById(id)?.let { customerDao.update(it.copy(isActive = active, updatedAt = System.currentTimeMillis())) }
    }

    override suspend fun setSupplierActive(id: Long, active: Boolean) {
        supplierDao.getById(id)?.let { supplierDao.update(it.copy(isActive = active, updatedAt = System.currentTimeMillis())) }
    }

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
