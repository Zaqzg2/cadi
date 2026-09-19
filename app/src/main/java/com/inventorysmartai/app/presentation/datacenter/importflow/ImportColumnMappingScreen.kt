package com.inventorysmartai.app.presentation.datacenter.importflow

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.navigation.Destination

/** Spec section 7 (Column Mapping Screen): shows every detected column with its suggested/
 *  current field, lets the user remap or ignore any column, and offers a previously saved
 *  template when one matches the file's structure closely enough (spec section 20). */
@Composable
fun ImportColumnMappingScreen(navController: NavController, viewModel: ImportFlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    val mapping = state.columnMapping

    Scaffold(
        topBar = { AppTopBar(title = "مطابقة الأعمدة", onBack = { navController.popBackStack() }) }
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
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("تم العثور على قالب مشابه: \"${state.suggestedTemplate?.name}\"", style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { viewModel.useSuggestedTemplate() }) { Text("استخدام القالب السابق") }
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

                mapping?.mappings?.forEach { column ->
                    item(key = column.columnIndex) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(column.header.ifBlank { "عمود بلا اسم" }, style = MaterialTheme.typography.bodyLarge)
                                    if (column.requiresConfirmation) {
                                        StatusPill(text = "يحتاج تأكيد", color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                                val assignable = (state.importType?.assignableFields() ?: emptyList()) + ImportField.IGNORE
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(assignable.distinct()) { field ->
                                        FilterChip(
                                            selected = column.field == field,
                                            onClick = { viewModel.onColumnMappingChanged(column.columnIndex, field) },
                                            label = { Text(field.labelAr) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(onClick = { showSaveTemplateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("حفظ هذا التعيين كقالب لاستخدامه لاحقًا")
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.confirmColumnMapping()
                    navController.navigate(Destination.ImportReview.route) {
                        popUpTo(Destination.ImportColumnMapping.route) { inclusive = true }
                    }
                },
                enabled = mapping != null && !state.isBusy,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("متابعة إلى التحقق والمطابقة")
            }
        }
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
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showSaveTemplateDialog = false }) { Text("إلغاء") } }
        )
    }
}
