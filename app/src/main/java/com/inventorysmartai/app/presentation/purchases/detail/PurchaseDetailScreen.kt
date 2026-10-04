package com.inventorysmartai.app.presentation.purchases.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.common.toDecimalOrNull
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.DataTable
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.SelectionBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.TableColumn
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.presentation.common.AddProductsBar
import com.inventorysmartai.app.presentation.common.PickerDialog
import com.inventorysmartai.app.presentation.common.PickerItem
import com.inventorysmartai.app.presentation.common.SelectField
import com.inventorysmartai.app.presentation.common.SnackbarEffect
import com.inventorysmartai.app.presentation.common.toPickerGroups
import com.inventorysmartai.app.presentation.common.toPickerItems

@Composable
fun PurchaseDetailScreen(navController: NavController, viewModel: PurchaseDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val data = (uiState as? UiState.Success)?.data

    SnackbarEffect(snackbarHost, data?.message, viewModel::onMessageShown)
    SnackbarEffect(snackbarHost, data?.removedMessage, viewModel::onRemovedMessageShown, undoLabel = "تراجع", onUndo = viewModel::onUndoRemove)
    LaunchedEffect(data?.isSaved) { if (data?.isSaved == true) navController.popBackStack() }

    var showPicker by remember { mutableStateOf(false) }
    var showReceive by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = data?.requestNumber ?: "طلب الشراء",
                onBack = { navController.popBackStack() },
                actions = {
                    if (data != null && !data.isNew && !data.hasReceipts) {
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "حذف الطلب") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (data != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (data.locked) {
                            Text(
                                if (data.status == PurchaseStatus.RECEIVED) "تم استلام هذا الطلب — للعرض فقط" else "طلب ملغى — للعرض فقط",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.onSave(PurchaseStatus.DRAFT) }, modifier = Modifier.weight(1f)) { Text("حفظ كمسودة") }
                                Button(onClick = { viewModel.onSave(PurchaseStatus.SUBMITTED) }, modifier = Modifier.weight(1f)) { Text("إرسال الطلب") }
                            }
                            if (data.canReceive) {
                                Button(onClick = { showReceive = true }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل استلام") }
                            }
                            if (!data.isNew) {
                                TextButton(onClick = { showCancel = true }, modifier = Modifier.fillMaxWidth()) { Text("إلغاء الطلب", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { d ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectField(
                        label = "المورد",
                        items = d.suppliers.map { PickerItem(it.id, it.name, subtitle = it.phone) },
                        selectedId = d.supplierId,
                        onSelected = viewModel::onSupplierSelected,
                        placeholder = "بدون مورد",
                        enabled = !d.locked,
                        modifier = Modifier.weight(1f)
                    )
                    SelectField(
                        label = "الفرع",
                        items = d.branches.map { PickerItem(it.id, it.name) },
                        selectedId = d.branchId,
                        onSelected = viewModel::onBranchSelected,
                        clearable = false,
                        enabled = !d.locked && !d.hasReceipts,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!d.locked) AddProductsBar(onAdd = { showPicker = true }, onBarcode = viewModel::onBarcodeScanned)

                SelectionBar(
                    selectedCount = selected.size,
                    onClear = { selected = emptySet() },
                    onDeleteSelected = if (d.locked) null else ({
                        viewModel.onRemoveItems(selected.map { it as Long }.toSet())
                        selected = emptySet()
                    })
                )

                val columns = remember(d.locked) {
                    listOf<TableColumn<PurchaseItemRow>>(
                        TableColumn(title = "الصنف", width = 160.dp, text = { it.productName }),
                        TableColumn(title = "المخزون", width = 80.dp, numeric = true, text = { Formatters.formatNumber(it.currentStock) }),
                        TableColumn(
                            title = "المطلوب", width = 90.dp, numeric = true,
                            text = { it.requestedQuantity },
                            onEdit = if (d.locked) null else { row, text -> viewModel.onQuantityChange(row.productId, text) }
                        ),
                        TableColumn(title = "المستلم", width = 80.dp, numeric = true, text = { Formatters.formatNumber(it.receivedQuantity) }),
                        TableColumn(title = "المتبقي", width = 80.dp, numeric = true, text = { Formatters.formatNumber(it.remaining) })
                    )
                }
                DataTable(
                    rows = d.items,
                    rowKey = { it.productId },
                    columns = columns,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    selectedKeys = selected,
                    onSelectionChange = if (d.locked) null else { keys -> selected = keys },
                    onDelete = if (d.locked) null else { row -> viewModel.onRemoveItems(setOf(row.productId)) },
                    emptyText = "لم تتم إضافة أصناف بعد — اضغط «إضافة أصناف» أو امسح باركود"
                )

                if (showPicker) {
                    PickerDialog(
                        title = "إضافة أصناف للطلب",
                        items = d.products.toPickerItems(),
                        groups = d.products.toPickerGroups(),
                        multiSelect = true,
                        disabledIds = d.items.map { it.productId }.toSet(),
                        onConfirm = { ids -> viewModel.onAddProducts(ids); showPicker = false },
                        onDismiss = { showPicker = false }
                    )
                }
                if (showReceive) {
                    ReceiveDialog(
                        rows = d.items.filter { it.remaining > 0.0 },
                        onConfirm = { q -> showReceive = false; viewModel.onReceive(q) },
                        onDismiss = { showReceive = false }
                    )
                }
            }
        }
    }

    data?.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::onErrorShown,
            title = { Text("تنبيه") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::onErrorShown) { Text("حسنًا") } }
        )
    }
    if (showDelete) {
        DestructiveConfirmDialog(
            title = "حذف الطلب؟",
            message = "سيُحذف الطلب وأصنافه نهائيًا. لم يُستلم عليه شيء فلا تأثير على المخزون.",
            onConfirm = { showDelete = false; viewModel.onDelete() },
            onDismiss = { showDelete = false }
        )
    }
    if (showCancel) {
        DestructiveConfirmDialog(
            title = "إلغاء الطلب؟",
            message = "سيُوسَم الطلب كملغى ولن يمكن تعديله. ما استُلم منه سابقًا يبقى في المخزون.",
            confirmLabel = "إلغاء الطلب",
            onConfirm = { showCancel = false; viewModel.onCancelRequest() },
            onDismiss = { showCancel = false }
        )
    }
}

/** One field per line, pre-filled with what is still outstanding: accept as-is for a full delivery, or
 *  type less for a partial one (blank / 0 = nothing received for that line). */
@Composable
private fun ReceiveDialog(rows: List<PurchaseItemRow>, onConfirm: (Map<Long, Double>) -> Unit, onDismiss: () -> Unit) {
    val entered = remember(rows) {
        mutableStateMapOf<Long, String>().apply {
            rows.forEach { put(it.productId, if (it.remaining % 1.0 == 0.0) it.remaining.toLong().toString() else it.remaining.toString()) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل استلام") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل الكمية المستلمة الآن لكل صنف", style = MaterialTheme.typography.bodySmall)
                rows.forEach { row ->
                    OutlinedTextField(
                        value = entered[row.productId].orEmpty(),
                        onValueChange = { entered[row.productId] = it },
                        label = { Text("${row.productName} (المتبقي ${Formatters.formatNumber(row.remaining)})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(entered.mapNotNull { (id, text) -> (if (text.isBlank()) 0.0 else text.toDecimalOrNull())?.takeIf { it >= 0.0 }?.let { id to it } }.toMap())
            }) { Text("تأكيد الاستلام") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
