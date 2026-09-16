package com.inventorysmartai.app.domain.inventory

import com.inventorysmartai.app.domain.model.InventoryStatus
import java.util.concurrent.TimeUnit

/**
 * Pure stock-status logic, extracted out of ProductRepositoryImpl so it can be unit-tested
 * without a Room database (see InventoryStatusCalculatorTest — covers the spec's explicit
 * "low stock calculation" / "zero stock calculation" test requirements).
 *
 * Precedence is deliberate: an expired batch matters more than a merely low count, and a zero
 * count matters more than an approaching expiry on a *different*, still-stocked batch.
 */
object InventoryStatusCalculator {
    fun compute(
        hasExpiry: Boolean,
        minStock: Double,
        totalQuantity: Double,
        nearestExpiryDate: Long?,
        nearExpiryWindowDays: Int,
        now: Long
    ): InventoryStatus {
        val expiryThresholdMillis = now + TimeUnit.DAYS.toMillis(nearExpiryWindowDays.toLong())
        return when {
            hasExpiry && nearestExpiryDate != null && nearestExpiryDate < now -> InventoryStatus.EXPIRED
            totalQuantity <= 0.0 -> InventoryStatus.ZERO
            hasExpiry && nearestExpiryDate != null && nearestExpiryDate <= expiryThresholdMillis -> InventoryStatus.NEAR_EXPIRY
            totalQuantity <= minStock -> InventoryStatus.LOW
            else -> InventoryStatus.AVAILABLE
        }
    }
}
