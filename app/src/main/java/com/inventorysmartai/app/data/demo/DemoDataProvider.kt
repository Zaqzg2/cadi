package com.inventorysmartai.app.data.demo

import com.inventorysmartai.app.domain.model.BranchStock
import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.ProductStockSummary

/**
 * Pure, static, in-memory sample objects for @Preview functions only. This is deliberately
 * separate from [DemoDataSeeder] — this object never touches Room and is never reachable from
 * production code paths, only from `@Preview` composables at design time.
 */
object DemoDataProvider {
    val sampleProduct = Product(
        id = 1L,
        itemNumber = "P-0001",
        barcode = "6281000001",
        name = "أرز بسمتي 5 كجم",
        categoryId = 1L,
        categoryName = "مواد غذائية",
        unitId = 1L,
        unitName = "كرتون",
        minStock = 10.0,
        reorderPoint = 15.0,
        hasExpiry = false,
        defaultPrice = 24.5
    )

    val sampleStockSummaries = listOf(
        ProductStockSummary(sampleProduct, listOf(BranchStock(1L, "الفرع الرئيسي", 48.0, null)), 48.0, null, InventoryStatus.AVAILABLE),
        ProductStockSummary(
            sampleProduct.copy(id = 2L, itemNumber = "P-0002", name = "زيت دوار الشمس 1.5 لتر"),
            listOf(BranchStock(1L, "الفرع الرئيسي", 5.0, null)), 5.0, null, InventoryStatus.LOW
        ),
        ProductStockSummary(
            sampleProduct.copy(id = 3L, itemNumber = "P-0003", name = "سكر أبيض 1 كجم"),
            listOf(BranchStock(1L, "الفرع الرئيسي", 0.0, null)), 0.0, null, InventoryStatus.ZERO
        )
    )
}
