package com.inventorysmartai.app.presentation.datacenter.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.domain.importing.ColumnMapping
import com.inventorysmartai.app.domain.importing.ColumnMappingEditor
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.SimpleJson
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.presentation.common.PickerItem
import com.inventorysmartai.app.presentation.common.SelectField

/**
 * Column mapping with a real editor: every file column shows a PREVIEW of its first values, can be
 * mapped (searchable list), renamed, or deleted (excluded — restorable below); and you can ADD a column
 * that is not in the file (e.g. a fixed branch for every row). A warning appears when a required field
 * has no column, before every row fails validation.
 */
@Composable
fun ImportColumnMappingScreen(navController: NavController, viewModel: ImportFlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ColumnMapping?>(null) }
    val mapping = state.columnMapping

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHost.showSnackbar(it); viewModel.consumeSnackbar() }
    }

    val assignable = remember(state.importType) { ((state.importType?.assignableFields() ?: emptyList()) + ImportField.IGNORE).distinct() }
    val fieldItems = remember(assignable) { assignable.mapIndexed { i, f -> PickerItem(i.toLong(), f.labelAr) } }

    // First values of every column, read from the first analysis pass (its rows keep the file's raw cells).
    val previews = remember(state.reviewRows) {
        val perHeader = LinkedHashMap<String, MutableList<String>>()
        state.reviewRows.take(30).forEach { row ->
            SimpleJson.decodeMap(row.rawData).forEach { (header, value) ->
                if (value.isNotBlank()) perHeader.getOrPut(header) { mutableListOf() }.let { if (it.size < 3) it.add(value) }
            }
        }
        perHeader
    }

    val missing = if (mapping != null && state.importType != null) ColumnMappingEditor.missingRequiredFields(mapping, state.importType!!) else emptyList()
    val active = mapping?.mappings?.filter { it.field != ImportField.IGNORE }.orEmpty()
    val deleted = mapping?.mappings?.filter { it.field == ImportField.IGNORE }.orEmpty()

    Scaffold(
        topBar = { AppTopBar(title = "مطابقة الأعمدة", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.suggestedTemplate != null) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تم العثور على قالب مشابه: \"${state.suggestedTemplate?.name}\"", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.useSuggestedTemplate() }) { Text("استخدام القالب") }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "الملف: ${state.pickedFileName ?: ""}" + (state.selectedSheet?.let { " — الورقة: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (missing.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                "لا يوجد عمود معيّن للحقل المطلوب: ${missing.joinToString("، ") { it.labelAr }} — ستظهر كل الصفوف بخطأ. عيّن عمودًا له، أو أضف عمودًا بقيمة ثابتة.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                items(active, key = { it.columnIndex }) { column ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(column.displayName, style = MaterialTheme.typography.bodyLarge)
                                    if (column.label != null && !column.isVirtual) {
                                        Text("في الملف: ${column.header}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (column.isVirtual) StatusPill(text = "عمود مضاف", color = MaterialTheme.colorScheme.primary)
                                else if (column.requiresConfirmation) StatusPill(text = "يحتاج تأكيد", color = MaterialTheme.colorScheme.tertiary)
                                IconButton(onClick = { renaming = column }) { Icon(Icons.Filled.Edit, contentDescription = "تغيير اسم العمود") }
                                IconButton(onClick = { viewModel.removeColumn(column.columnIndex) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف العمود", tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            if (column.isVirtual) {
                                OutlinedTextField(
                                    value = column.constantValue.orEmpty(),
                                    onValueChange = { viewModel.setColumnConstant(column.columnIndex, it) },
                                    label = { Text("القيمة لكل الصفوف") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                val sample = previews[column.header].orEmpty()
                                Text(
                                    if (sample.isEmpty()) "لا توجد قيم في أول الصفوف" else "أمثلة: " + sample.joinToString(" ، "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            SelectField(
                                label = "يُعيَّن إلى الحقل",
                                items = fieldItems,
                                selectedId = column.field?.let { assignable.indexOf(it).takeIf { i -> i >= 0 }?.toLong() },
                                onSelected = { id -> viewModel.onColumnMappingChanged(column.columnIndex, id?.let { assignable.getOrNull(it.toInt()) }) },
                                placeholder = "غير معيّن (يُتجاهل)",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (deleted.isNotEmpty()) {
                    item { Text("الأعمدة المحذوفة (${deleted.size}) — لن تُستورد", style = MaterialTheme.typography.titleSmall) }
                    items(deleted, key = { "deleted-${it.columnIndex}" }) { column ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(column.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { viewModel.restoreColumn(column.columnIndex) }) { Text("استعادة") }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(" إضافة عمود بقيمة ثابتة")
                        }
                        OutlinedButton(onClick = { showSaveTemplateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("حفظ هذا التعيين كقالب لاستخدامه لاحقًا")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.confirmColumnMapping {
                        navController.navigate(Destination.ImportReview.route) {
                            popUpTo(Destination.ImportColumnMapping.route) { inclusive = true }
                        }
                    }
                },
                enabled = mapping != null && !state.isBusy,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                    Text(state.stageLabel.ifBlank { "جاري التحقق والمطابقة..." })
                } else {
                    Text("متابعة إلى التحقق والمطابقة")
                }
            }
        }
    }

    if (showAddDialog) {
        AddColumnDialog(
            assignable = assignable.filter { it != ImportField.IGNORE },
            onConfirm = { label, field, value -> viewModel.addConstantColumn(label, field, value); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }

    renaming?.let { column ->
        var text by remember(column.columnIndex) { mutableStateOf(column.label ?: column.header) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("تغيير اسم العمود") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("الاسم") }, singleLine = true)
                    if (!column.isVirtual) {
                        Text("يغيّر الاسم المعروض هنا فقط؛ الملف نفسه لا يتغير.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.renameColumn(column.columnIndex, text); renaming = null }, enabled = text.isNotBlank()) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("إلغاء") } }
        )
    }

    if (showSaveTemplateDialog) {
        var templateName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text("حفظ القالب") },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text("اسم القالب") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveCurrentMappingAsTemplate(templateName)
                    showSaveTemplateDialog = false
                }, enabled = templateName.isNotBlank()) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showSaveTemplateDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun AddColumnDialog(
    assignable: List<ImportField>,
    onConfirm: (label: String, field: ImportField, value: String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var fieldIndex by remember { mutableStateOf<Long?>(null) }
    var value by remember { mutableStateOf("") }
    val items = remember(assignable) { assignable.mapIndexed { i, f -> PickerItem(i.toLong(), f.labelAr) } }
    val field = fieldIndex?.let { assignable.getOrNull(it.toInt()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عمود") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("عمود غير موجود في الملف: تأخذ كل الصفوف قيمته حيث يكون الحقل فارغًا في الملف (مثال: الفرع = الرياض).", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("اسم العمود") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectField(label = "الحقل", items = items, selectedId = fieldIndex, onSelected = { fieldIndex = it }, clearable = false, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("القيمة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { field?.let { onConfirm(label, it, value) } }, enabled = field != null && label.isNotBlank() && value.isNotBlank()) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
