package com.inventorysmartai.app.presentation.sales.detail

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun SalesDetailScreen(navController: NavController, viewModel: SalesDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar(title = "فاتورة البيع", onBack = { navController.popBackStack() }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { data ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text(data.invoiceNumber, style = MaterialTheme.typography.headlineSmall) }

                    item { Text("العميل", style = MaterialTheme.typography.titleSmall) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(data.customers) { customer ->
                                FilterChip(
                                    selected = data.customerId == customer.id,
                                    onClick = { viewModel.onCustomerSelected(customer.id) },
                                    label = { Text(customer.name) }
                                )
                            }
                        }
                    }

                    item { Text("الفرع", style = MaterialTheme.typography.titleSmall) }
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
                                        value = row.quantity,
                                        onValueChange = { viewModel.onQuantityChange(row.productId, it) },
                                        label = { Text("الكمية") },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = row.unitPrice,
                                        onValueChange = { viewModel.onPriceChange(row.productId, it) },
                                        label = { Text("السعر") },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = row.discountPercent,
                                        onValueChange = { viewModel.onDiscountChange(row.productId, it) },
                                        label = { Text("الخصم %") },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Text("الإجمالي: ${Formatters.formatCurrency(row.lineTotal)}", style = MaterialTheme.typography.labelLarge)
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الإجمالي الكلي", style = MaterialTheme.typography.titleMedium)
                            Text(Formatters.formatCurrency(data.total), style = MaterialTheme.typography.titleLarge)
                        }
                    }

                    item {
                        Button(onClick = viewModel::onSave, modifier = Modifier.fillMaxWidth()) {
                            Text("حفظ الفاتورة")
                        }
                    }
                }

                if (data.isSaved) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }
    }
}
