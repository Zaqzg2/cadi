package com.inventorysmartai.app.presentation.common

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer

/**
 * One selectable row in [PickerDialog] (a product, a customer, a branch...).
 *
 * [searchText] is everything the search box should match against — for a product that is
 * "name + item number + barcode", so typing OR scanning a barcode finds it.
 * [groupId] ties the row to a [PickerGroup] (a product's category) for the category filter.
 */
data class PickerItem(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val groupId: Long? = null,
    val searchText: String = title
)

data class PickerGroup(val id: Long, val name: String)

/**
 * Pure filter used by the picker (kept out of the composable so it is unit-tested).
 * - Query is split on whitespace; EVERY word must appear in [PickerItem.searchText] (Arabic-normalized,
 *   so "حليب السعوديه" finds "حليب السعودية", and "٥" finds "5").
 * - [groupId] == null means "all groups".
 * - Order of [items] is preserved.
 */
fun filterPickerItems(items: List<PickerItem>, query: String, groupId: Long?): List<PickerItem> {
    val words = ArabicTextNormalizer.normalize(query).split(' ').filter { it.isNotBlank() }
    return items.filter { item ->
        (groupId == null || item.groupId == groupId) &&
            (words.isEmpty() || ArabicTextNormalizer.normalize(item.searchText).let { hay -> words.all { hay.contains(it) } })
    }
}
