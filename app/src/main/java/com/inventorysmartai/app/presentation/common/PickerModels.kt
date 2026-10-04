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

/** Picker rows for products: searchable by name + item number + barcode, grouped by category. */
fun List<com.inventorysmartai.app.domain.model.Product>.toPickerItems(
    trailing: (com.inventorysmartai.app.domain.model.Product) -> String? = { null }
): List<PickerItem> = map { p ->
    PickerItem(
        id = p.id,
        title = p.name,
        subtitle = listOfNotNull(p.itemNumber?.let { "رقم $it" }, p.categoryName, p.unitName).joinToString(" • ").ifBlank { null },
        trailing = trailing(p),
        groupId = p.categoryId,
        searchText = "${p.name} ${p.itemNumber.orEmpty()} ${p.barcode.orEmpty()}"
    )
}

/** The category filter chips of the product picker (only categories that products actually use). */
fun List<com.inventorysmartai.app.domain.model.Product>.toPickerGroups(): List<PickerGroup> =
    mapNotNull { p -> p.categoryId?.let { id -> PickerGroup(id, p.categoryName ?: "—") } }
        .distinctBy { it.id }
        .sortedBy { it.name }
