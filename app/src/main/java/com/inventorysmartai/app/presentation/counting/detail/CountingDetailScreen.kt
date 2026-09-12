package com.inventorysmartai.app.presentation.counting.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.domain.model.CountStatus

@Composable
fun CountingDetailScreen(navController: NavController, viewModel: CountingDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(title = "الجرد", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { data ->
                if (data.isSaved) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
                }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text("الفرع", style = MaterialTheme.typography.titleSmall)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(data.branches) { branch ->
                                FilterChip(
                                    selected = data.branchId == branch.id,
                                    onClick = { viewModel.onBranchSelected(branch.id) },
                                    label = { Text(branch.name) }
                                )
                            }
                        }
                    }
                    item { Text("التاريخ: ${Formatters.formatDate(data.countDate)}", style = MaterialTheme.typography.bodyMedium) }

                    item { Text("الأصناف", style = MaterialTheme.typography.titleSmall) }

                    if (data.items.isEmpty()) {
                        item { Text("لم تتم إضافة أصناف بعد", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }

                    items(data.items, key = { it.productId }) { row ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(row.productName, style = MaterialTheme.typography.bodyLarge)
                                    IconButton(onClick = { viewModel.onRemoveItem(row.productId) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "إزالة")
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = Formatters.formatNumber(row.systemQuantity),
                                        onValueChange = {},
                                        label = { Text("الكمية بالنظام") },
                                        enabled = false,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = row.actualQuantity,
                                        onValueChange = { viewModel.onActualQuantityChange(row.productId, it) },
                                        label = { Text("الكمية الفعلية") },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                val difference = (row.actualQuantity.toDoubleOrNull() ?: row.systemQuantity) - row.systemQuantity
                                Text(
                                    "الفرق: ${if (difference >= 0) "+" else ""}${Formatters.formatNumber(difference)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (difference == 0.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                                OutlinedTextField(
                                    value = row.notes,
                                    onValueChange = { viewModel.onItemNotesChange(row.productId, it) },
                                    label = { Text("ملاحظات") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    val remainingProducts = data.products.filterNot { p -> data.items.any { it.productId == p.id } }
                    if (remainingProducts.isNotEmpty()) {
                        item { Text("إضافة صنف", style = MaterialTheme.typography.titleSmall) }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(remainingProducts) { product ->
                                    FilterChip(selected = false, onClick = { viewModel.onAddProduct(product.id) }, label = { Text(product.name) })
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.onSave(markCompleted = true) }, modifier = Modifier.fillMaxWidth()) {
                                Text("إنهاء الجرد وتسوية المخزون")
                            }
                            OutlinedButton(onClick = { viewModel.onSave(markCompleted = false) }, modifier = Modifier.fillMaxWidth()) {
                                Text("حفظ كمسودة")
                            }
                        }
                    }
                }
            }
        }
    }
}
