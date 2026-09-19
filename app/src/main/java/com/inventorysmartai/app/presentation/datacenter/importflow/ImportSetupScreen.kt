package com.inventorysmartai.app.presentation.datacenter.importflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.navigation.Destination

private val IMPORT_TYPE_MIME_TYPES = arrayOf(
    "text/comma-separated-values",
    "text/csv",
    "application/csv",
    "text/plain",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/octet-stream"
)

@Composable
fun ImportSetupScreen(navController: NavController, viewModel: ImportFlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toString()) }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "استيراد بيانات", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("نوع البيانات", style = MaterialTheme.typography.titleSmall)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(importTypeOptions) { (type, label) ->
                        FilterChip(
                            selected = state.importType == type,
                            onClick = { viewModel.onImportTypeSelected(type) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (state.importType != null && needsBranch(state.importType)) {
                item { Text("الفرع الافتراضي (اختياري إن كان الملف يحدد الفرع لكل صف)", style = MaterialTheme.typography.titleSmall) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.branches) { branch ->
                            FilterChip(
                                selected = state.selectedBranchId == branch.id,
                                onClick = {
                                    viewModel.onBranchSelected(if (state.selectedBranchId == branch.id) null else branch.id)
                                },
                                label = { Text(branch.name) }
                            )
                        }
                    }
                }
            }

            if (state.importType == ImportType.PURCHASE_REQUESTS) {
                item { Text("المورد (اختياري)", style = MaterialTheme.typography.titleSmall) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.suppliers) { supplier ->
                            FilterChip(
                                selected = state.selectedSupplierId == supplier.id,
                                onClick = {
                                    viewModel.onSupplierSelected(if (state.selectedSupplierId == supplier.id) null else supplier.id)
                                },
                                label = { Text(supplier.name) }
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الملف", style = MaterialTheme.typography.titleSmall)
                        if (state.pickedFileName != null) {
                            Text(state.pickedFileName.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                state.pickedFileSize?.let { Text(Formatters.formatFileSize(it), style = MaterialTheme.typography.bodySmall) }
                                state.detectedSourceType?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
                            }
                        } else {
                            Text("لم يتم اختيار ملف بعد", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.fileError != null) {
                            Text(state.fileError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { filePickerLauncher.launch(IMPORT_TYPE_MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.pickedFileName == null) "اختر الملف (Excel أو CSV)" else "اختيار ملف آخر")
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (state.sheets.size > 1) {
                            navController.navigate(Destination.ImportSheetSelect.route)
                        } else {
                            viewModel.startAnalysis()
                            navController.navigate(Destination.ImportAnalyzing.route)
                        }
                    },
                    enabled = state.canStartAnalysis && !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("متابعة")
                }
            }
        }
    }
}

private val importTypeOptions = listOf(
    ImportType.PRODUCTS to "الأصناف",
    ImportType.INVENTORY to "الجرد / المخزون",
    ImportType.COUNTING to "الجرد الفعلي",
    ImportType.PURCHASE_REQUESTS to "طلبات الشراء",
    ImportType.GOALS to "الأهداف"
)

private fun needsBranch(type: ImportType?): Boolean =
    type == ImportType.INVENTORY || type == ImportType.COUNTING || type == ImportType.PURCHASE_REQUESTS
