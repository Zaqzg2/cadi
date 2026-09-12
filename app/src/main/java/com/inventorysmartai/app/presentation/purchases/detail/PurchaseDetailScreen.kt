package com.inventorysmartai.app.presentation.purchases.detail

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
import androidx.compose.material3.OutlinedButton
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
import com.inventorysmartai.app.domain.model.PurchaseStatus

@Composable
fun PurchaseDetailScreen(navController: NavController, viewModel: PurchaseDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar(title = "طلب الشراء", onBack = { navController.popBackStack() }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { data ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text(data.requestNumber, style = MaterialTheme.typography.headlineSmall) }

                    item { Text("المورد", style = MaterialTheme.typography.titleSmall) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(data.suppliers) { supplier ->
                                FilterChip(
                                    selected = data.supplierId == supplier.id,
                                    onClick = { viewModel.onSupplierSelected(supplier.id) },
                                    label = { Text(supplier.name) }
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
                                        value = Formatters.formatNumber(row.currentStock),
                                        onValueChange = {},
                                        label = { Text("المخزون الحالي") },
                                        enabled = false,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = row.requestedQuantity,
                                        onValueChange = { viewModel.onQuantityChange(row.productId, it) },
                                        label = { Text("الكمية المطلوبة") },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                if (row.receivedQuantity > 0) {
                                    Text("تم استلام: ${Formatters.formatNumber(row.receivedQuantity)}", style = MaterialTheme.typography.labelSmall)
                                }
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
                            Button(onClick = { viewModel.onSave(PurchaseStatus.SUBMITTED) }, modifier = Modifier.fillMaxWidth()) {
                                Text("إرسال الطلب")
                            }
                            OutlinedButton(onClick = { viewModel.onSave(PurchaseStatus.DRAFT) }, modifier = Modifier.fillMaxWidth()) {
                                Text("حفظ كمسودة")
                            }
                            if (!data.isNew && data.status != PurchaseStatus.RECEIVED && data.status != PurchaseStatus.CANCELLED) {
                                OutlinedButton(onClick = viewModel::onReceiveAll, modifier = Modifier.fillMaxWidth()) {
                                    Text("تسجيل استلام الكمية كاملة")
                                }
                            }
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
