@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.stockops

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.domain.stockops.StockOperationType
import com.inventorysmartai.app.presentation.common.PickerItem
import com.inventorysmartai.app.presentation.common.SelectField
import com.inventorysmartai.app.presentation.common.toPickerGroups
import com.inventorysmartai.app.presentation.common.toPickerItems

/**
 * نموذج عملية مخزون: نوعها (تحويل / مرتجع بيع / مرتجع شراء)، الفرع أو الفرعان، الطرف (اختياري للمرتجع)، ثم أسطر أصناف وكميات.
 * التنفيذ معاملة واحدة: إن لم يكفِ الرصيد لا يتغيّر شيء وتُعرض الأصناف الناقصة. صفحة ثانوية بلا شريط سفلي؛ زر التنفيذ فوق الكيبورد.
 */
@Composable
fun StockOperationFormScreen(navController: NavController, viewModel: StockOperationFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) { if (state.done) navController.popBackStack() }

    val productItems = remember(state.products) { state.products.toPickerItems() }
    val productGroups = remember(state.products) { state.products.toPickerGroups() }
    val branchItems = remember(state.branches) { state.branches.map { PickerItem(it.id, it.name) } }
    val customerItems = remember(state.customers) { state.customers.map { PickerItem(it.id, it.name, subtitle = it.phone) } }
    val supplierItems = remember(state.suppliers) { state.suppliers.map { PickerItem(it.id, it.name, subtitle = it.phone) } }

    Scaffold(
        topBar = { AppTopBar(title = "عملية مخزون جديدة", onBack = { navController.popBackStack() }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(if (state.submitting) "جارٍ التنفيذ…" else "تنفيذ العملية")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StockOperationType.entries.forEach { type ->
                            FilterChip(
                                selected = state.type == type,
                                onClick = { viewModel.onTypeChange(type) },
                                label = { Text(type.shortAr) }
                            )
                        }
                    }
                    Text(hint(state.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                SelectField(
                    label = if (state.type == StockOperationType.TRANSFER) "من فرع" else "الفرع",
                    items = branchItems,
                    selectedId = state.fromBranchId,
                    onSelected = { id -> if (id != null) viewModel.onFromBranch(id) },
                    modifier = Modifier.fillMaxWidth(),
                    clearable = false
                )
            }

            when (state.type) {
                StockOperationType.TRANSFER -> item {
                    SelectField(
                        label = "إلى فرع",
                        items = branchItems,
                        selectedId = state.toBranchId,
                        onSelected = { id -> if (id != null) viewModel.onToBranch(id) },
                        modifier = Modifier.fillMaxWidth(),
                        clearable = false
                    )
                }
                StockOperationType.SALES_RETURN -> item {
                    SelectField(
                        label = "العميل (اختياري)",
                        items = customerItems,
                        selectedId = state.partyId,
                        onSelected = viewModel::onParty,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                StockOperationType.PURCHASE_RETURN -> item {
                    SelectField(
                        label = "المورد (اختياري)",
                        items = supplierItems,
                        selectedId = state.partyId,
                        onSelected = viewModel::onParty,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            items(state.lines, key = { it.localId }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectField(
                            label = "الصنف",
                            items = productItems,
                            selectedId = row.productId,
                            onSelected = { id -> if (id != null) viewModel.onProduct(row.localId, id) },
                            modifier = Modifier.fillMaxWidth(),
                            groups = productGroups,
                            clearable = false
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.quantity,
                                onValueChange = { viewModel.onQuantity(row.localId, it) },
                                label = { Text("الكمية") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            if (state.lines.size > 1) {
                                IconButton(onClick = { viewModel.onRemoveLine(row.localId) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف السطر")
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = viewModel::onAddLine, modifier = Modifier.fillMaxWidth()) {
                    Text("إضافة صنف")
                }
            }

            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = { Text("ملاحظات (اختياري)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun hint(type: StockOperationType): String = when (type) {
    StockOperationType.TRANSFER -> "تُخصم الكمية من الفرع المرسل وتُضاف إلى الفرع المستلم."
    StockOperationType.SALES_RETURN -> "تُعاد الكمية المرتجعة من العميل إلى رصيد الفرع."
    StockOperationType.PURCHASE_RETURN -> "تُخصم الكمية المرتجعة إلى المورد من رصيد الفرع."
}
