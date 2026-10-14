package com.inventorysmartai.app.domain.inventory

import com.inventorysmartai.app.domain.model.BranchStock
import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InventoryListFilterTest {

    private fun item(
        id: Long,
        name: String,
        vararg stock: Pair<Long, Double>,
        status: InventoryStatus = InventoryStatus.AVAILABLE,
        categoryId: Long? = null
    ) = ProductStockSummary(
        product = Product(id = id, itemNumber = null, barcode = null, name = name, categoryId = categoryId, unitId = null),
        branchStocks = stock.map { (branchId, quantity) -> BranchStock(branchId = branchId, branchName = "فرع $branchId", quantity = quantity, expiryDate = null) },
        totalQuantity = stock.sumOf { it.second },
        nearestExpiryDate = null,
        status = status
    )

    private val products = listOf(
        item(1, "حليب السعودية", 1L to 3.0, 2L to 50.0),
        item(2, "صابون سائل", 1L to 10.0),
        item(3, "منظف زجاج", 1L to 9.0, status = InventoryStatus.LOW),
        item(4, "عطر جو", 2L to 0.0, status = InventoryStatus.ZERO, categoryId = 7L)
    )

    private fun ids(filter: InventoryListFilter) = filter.filterAndSort(products).map { it.product.id }

    @Test
    fun `a quantity limit is strict by default - less than 10 excludes 10`() {
        // totals: 53, 10, 9, 0
        assertEquals(listOf(4L, 3L), ids(InventoryListFilter(quantityBelow = 10.0, sortOrder = SortOrder.QUANTITY_ASC)))
    }

    @Test
    fun `inclusive bounds keep the limit itself`() {
        assertEquals(listOf(4L, 3L, 2L), ids(InventoryListFilter(quantityBelow = 10.0, inclusiveBounds = true, sortOrder = SortOrder.QUANTITY_ASC)))
    }

    @Test
    fun `a lower bound and an upper bound make a range`() {
        assertEquals(listOf(3L), ids(InventoryListFilter(quantityAbove = 5.0, quantityBelow = 10.0)))
        assertEquals(listOf(1L), ids(InventoryListFilter(quantityAbove = 10.0)))
    }

    @Test
    fun `with a branch the quantity is that branch's stock, not the total`() {
        // the milk has 3 in branch 1 (53 in total) - it is "below 5" only when looking at the branch
        assertEquals(listOf(1L), ids(InventoryListFilter(branchId = 1L, quantityBelow = 5.0)))
        assertEquals(emptyList<Long>(), ids(InventoryListFilter(quantityBelow = 2.0).copy(branchId = 3L)))
    }

    @Test
    fun `the status filter follows the computed status`() {
        assertEquals(listOf(3L), ids(InventoryListFilter(status = InventoryStatusFilter.LOW)))
        assertEquals(listOf(4L), ids(InventoryListFilter(status = InventoryStatusFilter.ZERO)))
    }

    @Test
    fun `search finds Arabic spelling variants`() {
        assertEquals(listOf(1L), ids(InventoryListFilter(searchQuery = "السعوديه")))
        assertEquals(listOf(2L), ids(InventoryListFilter(searchQuery = "صابون")))
    }

    @Test
    fun `category and sorting work together`() {
        assertEquals(listOf(4L), ids(InventoryListFilter(categoryId = 7L)))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(InventoryListFilter(sortOrder = SortOrder.QUANTITY_DESC)))
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(InventoryListFilter(sortOrder = SortOrder.QUANTITY_ASC)))
    }

    @Test
    fun `no filter returns everything by name`() {
        assertEquals(4, InventoryListFilter().filterAndSort(products).size)
    }

    @Test
    fun `the quantity chip text says what is applied`() {
        assertEquals("الرصيد أقل من 10", InventoryListFilter(quantityBelow = 10.0).quantityLabelAr())
        assertEquals("الرصيد أقل من أو يساوي 10", InventoryListFilter(quantityBelow = 10.0, inclusiveBounds = true).quantityLabelAr())
        assertEquals("الرصيد أكبر من 5", InventoryListFilter(quantityAbove = 5.0).quantityLabelAr())
        assertEquals("الرصيد أكبر من 5 وأقل من 10", InventoryListFilter(quantityAbove = 5.0, quantityBelow = 10.0).quantityLabelAr())
        assertNull(InventoryListFilter().quantityLabelAr())
    }

    @Test
    fun `numbers are written without a trailing zero`() {
        assertEquals("10", InventoryListFilter.plainNumber(10.0))
        assertEquals("2.5", InventoryListFilter.plainNumber(2.5))
    }
}
