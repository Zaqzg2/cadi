package com.inventorysmartai.app.presentation.datacenter.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StatCard
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.RowDecision
import com.inventorysmartai.app.domain.importing.SimpleJson
import com.inventorysmartai.app.domain.model.ImportJobStatus
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.model.ImportRowStatus
import com.inventorysmartai.app.domain.model.aggregateImportRowCounts
import com.inventorysmartai.app.navigation.Destination

/** Spec sections 12-17: the review summary, per-row review/actions, bulk actions, and the
 *  "اعتماد الاستيراد" approval step, all on one screen — matching how the spec describes it as
 *  a single "critical screen" rather than several. */
@Composable
fun ImportReviewScreen(navController: NavController, viewModel: ImportFlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingRow by remember { mutableStateOf<ImportRow?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    val counts = remember(state.reviewRows) { aggregateImportRowCounts(state.reviewRows) }
    val result = state.approvalResult

    Scaffold(
        topBar = { AppTopBar(title = "مراجعة الاستيراد", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (result != null) {
            ImportResultView(
                modifier = Modifier.padding(padding).fillMaxSize(),
                status = result.status,
                savedRows = result.savedRows,
                failedRows = result.failedRows,
                errorMessage = result.errorMessage,
                onDone = {
                    navController.popBackStack(Destination.DataCenter.route, inclusive = false)
                }
            )
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    val stats = listOf(
                        "إجمالي الصفوف" to counts.totalRows,
                        "مطابقة" to counts.matchedRows,
                        "أصناف جديدة" to counts.newProductRows,
                        "مكررة" to counts.duplicateRows,
                        "أخطاء" to counts.errorRows
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.chunked(2).forEach { rowStats ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                rowStats.forEach { (label, value) ->
                                    StatCard(
                                        label = label,
                                        value = value.toString(),
                                        modifier = Modifier.weight(1f),
                                        accentColor = if (label == "أخطاء") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (rowStats.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.onAcceptAllValid() }, modifier = Modifier.weight(1f)) {
                            Text("قبول المطابقات الأكيدة")
                        }
                        OutlinedButton(onClick = { viewModel.onRejectAll() }, modifier = Modifier.weight(1f)) {
                            Text("رفض الكل")
                        }
                    }
                }

                items(state.reviewRows, key = { it.id }) { row ->
                    ReviewRowCard(
                        row = row,
                        onAccept = { viewModel.onRowDecision(row.id, RowDecision.Accept) },
                        onReject = { viewModel.onRowDecision(row.id, RowDecision.Reject) },
                        onEdit = { editingRow = row }
                    )
                }

                if (state.reviewRows.isEmpty()) {
                    item { Text("لا توجد صفوف بعد", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            Button(
                onClick = { viewModel.approveImport() },
                enabled = !state.isBusy && state.reviewRows.any { it.status == ImportRowStatus.ACCEPTED },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("اعتماد الاستيراد")
            }
        }
    }

    editingRow?.let { row ->
        EditRowDialog(
            row = row,
            importType = state.importType,
            onDismiss = { editingRow = null },
            onSave = { edits ->
                viewModel.onEditRow(row.id, edits)
                editingRow = null
            }
        )
    }
}

@Composable
private fun ReviewRowCard(row: ImportRow, onAccept: () -> Unit, onReject: () -> Unit, onEdit: () -> Unit) {
    val normalized = remember(row.normalizedData) { SimpleJson.decodeMap(row.normalizedData) }
    val displayName = normalized[ImportField.PRODUCT_NAME.name]
        ?: normalized[ImportField.ITEM_NUMBER.name]
        ?: normalized[ImportField.BARCODE.name]
        ?: "صف رقم ${row.rowIndex + 1}"
    val (statusLabel, statusColor) = rowStatusLabel(row.status)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("صف ${row.rowIndex + 1}: $displayName", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                StatusPill(text = statusLabel, color = statusColor)
            }
            if (row.errorMessage != null) {
                Text(row.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            val warnings = SimpleJson.decodeList(row.warningsJson)
            warnings.forEach { warning ->
                Text("⚠ $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (row.edited) {
                Text("تم تعديل هذا الصف يدويًا", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onAccept) { Icon(Icons.Filled.Check, contentDescription = "قبول") }
                IconButton(onClick = onReject) { Icon(Icons.Filled.Close, contentDescription = "رفض") }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "تعديل") }
            }
        }
    }
}

@Composable
private fun rowStatusLabel(status: ImportRowStatus): Pair<String, Color> = when (status) {
    ImportRowStatus.PENDING -> "بانتظار المراجعة" to MaterialTheme.colorScheme.onSurfaceVariant
    ImportRowStatus.MATCHED -> "مطابقة" to MaterialTheme.colorScheme.primary
    ImportRowStatus.NEW_PRODUCT -> "صنف جديد" to MaterialTheme.colorScheme.tertiary
    ImportRowStatus.AMBIGUOUS -> "غير مؤكد" to MaterialTheme.colorScheme.tertiary
    ImportRowStatus.DUPLICATE -> "مكرر" to MaterialTheme.colorScheme.error
    ImportRowStatus.ERROR -> "خطأ" to MaterialTheme.colorScheme.error
    ImportRowStatus.ACCEPTED -> "مقبول" to MaterialTheme.colorScheme.primary
    ImportRowStatus.REJECTED -> "مرفوض" to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun EditRowDialog(
    row: ImportRow,
    importType: ImportType?,
    onDismiss: () -> Unit,
    onSave: (Map<ImportField, String?>) -> Unit
) {
    val normalized = remember(row.normalizedData) { SimpleJson.decodeMap(row.normalizedData) }
    val editableFields = remember(importType) {
        (listOf(ImportField.PRODUCT_NAME, ImportField.ITEM_NUMBER, ImportField.BARCODE) +
            (importType?.requiredFields?.toList() ?: emptyList()) +
            (importType?.optionalFields?.toList() ?: emptyList())).distinct()
    }
    val values = remember(row.id) { mutableStateOf(editableFields.associateWith { normalized[it.name].orEmpty() }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الصف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                editableFields.forEach { field ->
                    OutlinedTextField(
                        value = values.value[field].orEmpty(),
                        onValueChange = { newValue -> values.value = values.value + (field to newValue) },
                        label = { Text(field.labelAr) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values.value.mapValues { it.value.ifBlank { null } }) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ImportResultView(
    modifier: Modifier,
    status: ImportJobStatus,
    savedRows: Int,
    failedRows: Int,
    errorMessage: String?,
    onDone: () -> Unit
) {
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val (title, color) = when (status) {
            ImportJobStatus.COMPLETED -> "تم الاستيراد بنجاح" to MaterialTheme.colorScheme.primary
            ImportJobStatus.PARTIALLY_COMPLETED -> "تم الاستيراد جزئيًا" to MaterialTheme.colorScheme.tertiary
            else -> "فشل اعتماد الاستيراد" to MaterialTheme.colorScheme.error
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = color)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("الصفوف المحفوظة: $savedRows")
                Text("الصفوف غير المحفوظة: $failedRows")
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("العودة إلى مركز البيانات") }
    }
}
