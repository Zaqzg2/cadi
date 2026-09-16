package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer

/**
 * [itemNumber] and [barcode] are both nullable and only conditionally unique: SQLite treats
 * every NULL as distinct from every other NULL, so any number of products with no item number
 * (or no barcode) can coexist under a UNIQUE index without collisions — exactly what the spec
 * asks for ("do not make barcode/itemNumber mandatory if the product doesn't have them").
 *
 * [normalizedName] and [alternateNames] back ProductMatcher's deterministic "exact normalized
 * name" strategy (see domain/matching/ProductMatcher.kt) and future fuzzy-matching suggestions.
 * [alternateNames] is a simple newline-joined list — no separate table needed for Phase 2.
 */
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index("categoryId"),
        Index("unitId"),
        Index(value = ["barcode"], unique = true),
        Index(value = ["itemNumber"], unique = true),
        Index(value = ["normalizedName"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemNumber: String?,
    val barcode: String?,
    val name: String,
    val normalizedName: String = ArabicTextNormalizer.normalize(name),
    val alternateNames: String? = null,
    val categoryId: Long?,
    val unitId: Long?,
    val minStock: Double = 0.0,
    val reorderPoint: Double = 0.0,
    val hasExpiry: Boolean = false,
    val defaultPrice: Double? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
