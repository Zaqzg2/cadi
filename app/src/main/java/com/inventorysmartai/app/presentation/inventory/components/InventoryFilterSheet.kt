package com.inventorysmartai.app.presentation.inventory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Category
import com.inventorysmartai.app.domain.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryFilterSheet(
    branches: List<Branch>,
    categories: List<Category>,
    selectedBranchId: Long?,
    selectedCategoryId: Long?,
    sortOrder: SortOrder,
    onBranchSelected: (Long?) -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("الفرع", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = selectedBranchId == null, onClick = { onBranchSelected(null) }, label = { Text("الكل") })
                }
                items(branches) { branch ->
                    FilterChip(
                        selected = selectedBranchId == branch.id,
                        onClick = { onBranchSelected(branch.id) },
                        label = { Text(branch.name) }
                    )
                }
            }

            Text("التصنيف", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = selectedCategoryId == null, onClick = { onCategorySelected(null) }, label = { Text("الكل") })
                }
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { onCategorySelected(category.id) },
                        label = { Text(category.name) }
                    )
                }
            }

            Text("الترتيب", style = MaterialTheme.typography.titleSmall)
            val sortOptions = listOf(
                SortOrder.NAME_ASC to "الاسم (أ - ي)",
                SortOrder.NAME_DESC to "الاسم (ي - أ)",
                SortOrder.QUANTITY_ASC to "الكمية (تصاعدي)",
                SortOrder.QUANTITY_DESC to "الكمية (تنازلي)"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sortOptions) { (order, label) ->
                    FilterChip(selected = sortOrder == order, onClick = { onSortOrderSelected(order) }, label = { Text(label) })
                }
            }
        }
    }
}
