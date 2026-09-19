package com.inventorysmartai.app.presentation.datacenter.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.SimpleJson
import com.inventorysmartai.app.domain.model.aggregateImportRowCounts

@Composable
fun ImportJobDetailScreen(navController: NavController, viewModel: ImportJobDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(title = "تقرير الاستيراد", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState) { detail ->
                val counts = aggregateImportRowCounts(detail.rows)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(detail.job.fileName ?: "بدون اسم ملف", style = MaterialTheme.typography.titleMedium)
                                Text(Formatters.formatDate(detail.job.createdAt), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "الإجمالي: ${counts.totalRows}  •  مقبول: ${counts.acceptedRows}  •  مطابق: ${counts.matchedRows}  •  " +
                                        "جديد: ${counts.newProductRows}  •  مكرر: ${counts.duplicateRows}  •  أخطاء: ${counts.errorRows}  •  " +
                                        "مرفوض: ${counts.rejectedRows}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    items(detail.rows, key = { it.id }) { row ->
                        val normalized = SimpleJson.decodeMap(row.normalizedData)
                        val name = normalized[ImportField.PRODUCT_NAME.name]
                            ?: normalized[ImportField.ITEM_NUMBER.name]
                            ?: normalized[ImportField.BARCODE.name]
                            ?: "صف ${row.rowIndex + 1}"
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                StatusPill(text = row.status.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
