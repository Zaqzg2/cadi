package com.inventorysmartai.app.presentation.sales.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.DataTable
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.SelectionBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.TableColumn
import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.presentation.common.AddProductsBar
import com.inventorysmartai.app.presentation.common.PickerDialog
import com.inventorysmartai.app.presentation.common.PickerItem
import com.inventorysmartai.app.presentation.common.SelectField
import com.inventorysmartai.app.presentation.common.SnackbarEffect
import com.inventorysmartai.app.presentation.common.toPickerGroups
import com.inventorysmartai.app.presentation.common.toPickerItems

@Composable
fun SalesDetailScreen(navController: NavController, viewModel: SalesDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val data = (uiState as? com.inventorysmartai.app.core.common.UiState.Success)?.data

    SnackbarEffect(snackbarHost, data?.message, viewModel::onMessageShown)
    SnackbarEffect(
        snackbarHost, data?.removedMessage, viewModel::onRemovedMessageShown,
        undoLabel = "تراجع", onUndo = viewModel::onUndoRemove
    )
    LaunchedEffect(data?.isSaved) { if (data?.isSaved == true) navController.popBackStack() }

    var showPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = data?.invoiceNumber ?: "فاتورة البيع",
                onBack = { navController.popBackStack() },
                actions = {
                    if (data != null && !data.isNew && !data.readOnly) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف المسودة")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (data != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الإجمالي (${data.items.size} صنف)", style = MaterialTheme.typography.titleMedium)
                            Text(Formatters.formatCurrency(data.total), style = MaterialTheme.typography.titleLarge)
                        }
                        if (data.readOnly) {
                            Text(
                                if (data.status == InvoiceStatus.CONFIRMED) "فاتورة مؤكدة — للعرض فقط، لأنها خصمت من المخزون" else "فاتورة ملغاة — للعرض فقط",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.onSave(InvoiceStatus.DRAFT) }, modifier = Modifier.weight(1f)) { Text("حفظ كمسودة") }
                                Button(onClick = { viewModel.onSave(InvoiceStatus.CONFIRMED) }, modifier = Modifier.weight(1f)) { Text("إتمام الفاتورة") }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { d ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectField(
                        label = "الفرع",
                        items = d.branches.map { PickerItem(it.id, it.name) },
                        selectedId = d.branchId,
                        onSelected = viewModel::onBranchSelected,
                        clearable = false,
                        enabled = !d.readOnly,
                        modifier = Modifier.weight(1f)
                    )
                    SelectField(
                        label = "العميل",
                        items = d.customers.map { PickerItem(it.id, it.name, subtitle = it.phone) },
                        selectedId = d.customerId,
                        onSelected = viewModel::onCustomerSelected,
                        placeholder = "بدون عميل",
                        enabled = !d.readOnly,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!d.readOnly) {
                    AddProductsBar(onAdd = { showPicker = true }, onBarcode = viewModel::onBarcodeScanned)
                }

                SelectionBar(
                    selectedCount = selected.size,
                    onClear = { selected = emptySet() },
                    onDeleteSelected = if (d.readOnly) null else ({
                        viewModel.onRemoveItems(selected.map { it as Long }.toSet())
                        selected = emptySet()
                    })
                )

                val columns = remember(d.readOnly, d.stockByProduct) {
                    listOf<TableColumn<SalesItemRow>>(
                        TableColumn(title = "الصنف", width = 160.dp, text = { it.productName }),
                        TableColumn(
                            title = "الكمية", width = 80.dp, numeric = true,
                            text = { it.quantity },
                            onEdit = if (d.readOnly) null else { row, text -> viewModel.onQuantityChange(row.productId, text) }
                        ),
                        TableColumn(
                            title = "السعر", width = 90.dp, numeric = true,
                            text = { it.unitPrice },
                            onEdit = if (d.readOnly) null else { row, text -> viewModel.onPriceChange(row.productId, text) }
                        ),
                        TableColumn(
                            title = "خصم %", width = 70.dp, numeric = true,
                            text = { it.discountPercent },
                            onEdit = if (d.readOnly) null else { row, text -> viewModel.onDiscountChange(row.productId, text) }
                        ),
                        TableColumn(title = "الإجمالي", width = 100.dp, numeric = true, text = { Formatters.formatNumber(it.lineTotal) }),
                        TableColumn(
                            title = "المتاح", width = 70.dp, numeric = true,
                            text = { d.stockByProduct[it.productId]?.let(Formatters::formatNumber).orEmpty() }
                        )
                    )
                }
                DataTable(
                    rows = d.items,
                    rowKey = { it.productId },
                    columns = columns,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    selectedKeys = selected,
                    onSelectionChange = if (d.readOnly) null else { keys -> selected = keys },
                    onDelete = if (d.readOnly) null else { row -> viewModel.onRemoveItem(row.productId) },
                    emptyText = "لم تتم إضافة أصناف بعد — اضغط «إضافة أصناف» أو امسح باركود"
                )

                if (showPicker) {
                    PickerDialog(
                        title = "إضافة أصناف للفاتورة",
                        items = d.products.toPickerItems { p -> d.stockByProduct[p.id]?.let { "المتاح ${Formatters.formatNumber(it)}" } },
                        groups = d.products.toPickerGroups(),
                        multiSelect = true,
                        disabledIds = d.items.map { it.productId }.toSet(),
                        onConfirm = { ids -> viewModel.onAddProducts(ids); showPicker = false },
                        onDismiss = { showPicker = false }
                    )
                }
            }
        }
    }

    data?.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::onErrorShown,
            title = { Text("تعذّر الحفظ") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::onErrorShown) { Text("حسنًا") } }
        )
    }
    if (showDeleteDialog) {
        DestructiveConfirmDialog(
            title = "حذف المسودة؟",
            message = "ستُحذف مسودة الفاتورة وكل أصنافها نهائيًا. لا تأثير على المخزون.",
            onConfirm = { showDeleteDialog = false; viewModel.onDeleteDraft() },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
