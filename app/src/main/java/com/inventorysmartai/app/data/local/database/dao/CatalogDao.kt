package com.inventorysmartai.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inventorysmartai.app.data.local.database.entity.BranchEntity
import com.inventorysmartai.app.data.local.database.entity.CategoryEntity
import com.inventorysmartai.app.data.local.database.entity.UnitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY name ASC")
    fun observeAll(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches WHERE id = :id")
    suspend fun getById(id: Long): BranchEntity?

    /** Phase 3: resolves a BRANCH column's text value to an existing branch when importing
     *  inventory/counting/purchase-request rows that name their branch inline. */
    @Query("SELECT * FROM branches WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): BranchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(branch: BranchEntity): Long

    @Delete
    suspend fun delete(branch: BranchEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** Phase 3: resolves a CATEGORY column's text value during product import. */
    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY name ASC")
    fun observeAll(): Flow<List<UnitEntity>>

    /** Phase 3: resolves a UNIT column's text value during product import. */
    @Query("SELECT * FROM units WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): UnitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(unit: UnitEntity): Long

    @Query("DELETE FROM units WHERE id = :id")
    suspend fun deleteById(id: Long)
}
