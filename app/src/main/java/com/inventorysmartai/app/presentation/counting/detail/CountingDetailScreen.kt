package com.inventorysmartai.app.presentation.counting.detail

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
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.DataTable
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.SelectionBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.TableColumn
import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.presentation.common.AddProductsBar
import com.inventorysmartai.app.presentation.common.PickerDialog
import com.inventorysmartai.app.presentation.common.PickerItem
import com.inventorysmartai.app.presentation.common.SelectField
import com.inventorysmartai.app.presentation.common.SnackbarEffect
import com.inventorysmartai.app.presentation.common.toPickerGroups
import com.inventorysmartai.app.presentation.common.toPickerItems

@Composable
fun CountingDetailScreen(navController: NavController, viewModel: CountingDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val data = (uiState as? UiState.Success)?.data

    SnackbarEffect(snackbarHost, data?.message, viewModel::onMessageShown)
    SnackbarEffect(snackbarHost, data?.removedMessage, viewModel::onRemovedMessageShown, undoLabel = "تراجع", onUndo = viewModel::onUndoRemove)
    LaunchedEffect(data?.isSaved) { if (data?.isSaved == true) navController.popBackStack() }

    var showPicker by remember { mutableStateOf(false) }
    var showComplete by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "جرد المخزون",
                onBack = { navController.popBackStack() },
                actions = {
                    if (data != null && !data.isNew && !data.readOnly) {
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "حذف المسودة") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (data != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${data.items.size} صنف • ${data.differingCount} فيه فرق",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (data.readOnly) {
                            Text(
                                if (data.status == CountStatus.COMPLETED) "جرد مكتمل — للعرض فقط، لأنه صحّح المخزون" else "جرد ملغى — للعرض فقط",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.onSave(false) }, modifier = Modifier.weight(1f)) { Text("حفظ كمسودة") }
                                Button(onClick = { showComplete = true }, modifier = Modifier.weight(1f)) { Text("إتمام الجرد") }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { d ->
                SelectField(
                    label = "الفرع",
                    items = d.branches.map { PickerItem(it.id, it.name) },
                    selectedId = d.branchId,
                    onSelected = viewModel::onBranchSelected,
                    clearable = false,
                    enabled = !d.readOnly,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (!d.readOnly) AddProductsBar(onAdd = { showPicker = true }, onBarcode = viewModel::onBarcodeScanned)

                SelectionBar(
                    selectedCount = selected.size,
                    onClear = { selected = emptySet() },
                    onDeleteSelected = if (d.readOnly) null else ({
                        viewModel.onRemoveItems(selected.map { it as Long }.toSet())
                        selected = emptySet()
                    })
                )

                val columns = remember(d.readOnly) {
                    listOf<TableColumn<CountItemRow>>(
                        TableColumn(title = "الصنف", width = 160.dp, text = { it.productName }),
                        TableColumn(title = "النظام", width = 80.dp, numeric = true, text = { Formatters.formatNumber(it.systemQuantity) }),
                        TableColumn(
                            title = "الفعلي", width = 90.dp, numeric = true,
                            text = { it.actualQuantity },
                            onEdit = if (d.readOnly) null else { row, text -> viewModel.onActualQuantityChange(row.productId, text) }
                        ),
                        TableColumn(
                            title = "الفرق", width = 80.dp, numeric = true,
                            text = { row ->
                                row.difference?.let { diff -> (if (diff > 0) "+" else "") + Formatters.formatNumber(diff) } ?: "؟"
                            }
                        ),
                        TableColumn(
                            title = "ملاحظة", width = 140.dp,
                            text = { it.notes },
                            onEdit = if (d.readOnly) null else { row, text -> viewModel.onItemNotesChange(row.productId, text) }
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
                    onDelete = if (d.readOnly) null else { row -> viewModel.onRemoveItems(setOf(row.productId)) },
                    emptyText = "أضف الأصناف من القائمة (يمكنك إضافة تصنيف كامل بتحديد المعروض) أو امسح باركود: كل مسح يعدّ وحدة واحدة"
                )

                if (showPicker) {
                    PickerDialog(
                        title = "إضافة أصناف للجرد",
                        items = d.products.toPickerItems(),
                        groups = d.products.toPickerGroups(),
                        multiSelect = true,
                        disabledIds = d.items.map { it.productId }.toSet(),
                        onConfirm = { ids -> viewModel.onAddProducts(ids); showPicker = false },
                        onDismiss = { showPicker = false }
                    )
                }

                if (showComplete) {
                    AlertDialog(
                        onDismissRequest = { showComplete = false },
                        title = { Text("إتمام الجرد؟") },
                        text = {
                            Text(
                                if (d.differingCount > 0) "سيُصحَّح رصيد ${d.differingCount} صنف في هذا الفرع ويُسجَّل لكل منها حركة تسوية. لا يمكن تعديل الجرد بعد إتمامه."
                                else "لا توجد فروق — لن يتغير أي رصيد. لا يمكن تعديل الجرد بعد إتمامه."
                            )
                        },
                        confirmButton = { TextButton(onClick = { showComplete = false; viewModel.onSave(true) }) { Text("إتمام") } },
                        dismissButton = { TextButton(onClick = { showComplete = false }) { Text("إلغاء") } }
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
            title = "حذف مسودة الجرد؟",
            message = "ستُحذف المسودة وأسطرها نهائيًا. لا تأثير على المخزون.",
            onConfirm = { showDelete = false; viewModel.onDeleteDraft() },
            onDismiss = { showDelete = false }
        )
    }
}
