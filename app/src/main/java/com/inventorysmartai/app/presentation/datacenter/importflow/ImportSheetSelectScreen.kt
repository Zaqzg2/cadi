package com.inventorysmartai.app.presentation.datacenter.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.navigation.Destination
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable

/** Spec section 24, step 3 ("اختر الورقة") — only reached when the workbook has more than one
 *  sheet (see ImportSetupScreen, which skips straight to analysis otherwise). */
@Composable
fun ImportSheetSelectScreen(navController: NavController, viewModel: ImportFlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(title = "اختر الورقة", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "يحتوي هذا الملف على ${state.sheets.size} أوراق — اختر الورقة المطلوب استيرادها",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.sheets) { sheet ->
                    val label = sheet ?: "(بدون اسم)"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = state.selectedSheet == sheet, onClick = { viewModel.onSheetSelected(sheet) }),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.selectedSheet == sheet) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(selected = state.selectedSheet == sheet, onClick = { viewModel.onSheetSelected(sheet) })
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Button(
                onClick = {
                    viewModel.startAnalysis()
                    navController.navigate(Destination.ImportAnalyzing.route)
                },
                enabled = state.selectedSheet != null || state.sheets.any { it == null },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("متابعة")
            }
        }
    }
}
