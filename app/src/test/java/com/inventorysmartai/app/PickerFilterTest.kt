package com.inventorysmartai.app

import com.inventorysmartai.app.presentation.common.PickerItem
import com.inventorysmartai.app.presentation.common.filterPickerItems
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerFilterTest {
    private val items = listOf(
        PickerItem(1, "حليب السعودية", groupId = 10, searchText = "حليب السعودية 1001 6281000000011"),
        PickerItem(2, "عصير برتقال", groupId = 20, searchText = "عصير برتقال 1002 6281000000028"),
        PickerItem(3, "حليب مجفف", groupId = 10, searchText = "حليب مجفف 1003")
    )

    @Test fun emptyQueryReturnsAll() = assertEquals(listOf(1L, 2L, 3L), filterPickerItems(items, "", null).map { it.id })
    @Test fun groupFilter() = assertEquals(listOf(1L, 3L), filterPickerItems(items, "", 10).map { it.id })
    @Test fun allWordsMustMatch() = assertEquals(listOf(3L), filterPickerItems(items, "حليب مجفف", null).map { it.id })
    @Test fun matchesBarcode() = assertEquals(listOf(2L), filterPickerItems(items, "6281000000028", null).map { it.id })
    @Test fun matchesItemNumberWithArabicDigits() = assertEquals(listOf(1L), filterPickerItems(items, "١٠٠١", null).map { it.id })
    @Test fun spellingVariantMatches() = assertEquals(listOf(1L), filterPickerItems(items, "حليب السعوديه", null).map { it.id })
    @Test fun queryAndGroupCombine() = assertEquals(emptyList<Long>(), filterPickerItems(items, "عصير", 10).map { it.id })
    @Test fun noMatch() = assertEquals(emptyList<Long>(), filterPickerItems(items, "xyz", null).map { it.id })
}
