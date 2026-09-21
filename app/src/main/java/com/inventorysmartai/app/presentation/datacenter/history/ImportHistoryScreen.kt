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
import com.inventorysmartai.app.domain.model.ImportJob
import com.inventorysmartai.app.domain.model.ImportJobStatus
import com.inventorysmartai.app.navigation.Destination

/** Spec section 18. Also serves as the Data Center's "العمليات" section — see
 *  ImportHistoryViewModel's doc comment for why one screen covers all four tiles. */
@Composable
fun ImportHistoryScreen(navController: NavController, viewModel: ImportHistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(title = viewModel.filter.labelAr, onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState) { jobs ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(jobs, key = { it.id }) { job ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navController.navigate(Destination.ImportJobDetail.createRoute(job.id)) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(job.fileName ?: "بدون اسم ملف", style = MaterialTheme.typography.bodyLarge)
                                    StatusPill(text = statusLabel(job.status), color = statusColor(job.status))
                                }
                                Text(Formatters.formatDate(job.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "الإجمالي: ${job.totalRows ?: 0}  •  مقبول: ${job.acceptedRows ?: 0}  •  " +
                                        "مطابق: ${job.matchedRows ?: 0}  •  جديد: ${job.newProductRows ?: 0}  •  " +
                                        "مكرر: ${job.duplicateRows ?: 0}  •  أخطاء: ${job.errorRows ?: 0}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: ImportJobStatus): String = when (status) {
    ImportJobStatus.PENDING -> "قيد الانتظار"
    ImportJobStatus.PROCESSING -> "قيد المعالجة"
    ImportJobStatus.REVIEW_REQUIRED -> "قيد المراجعة"
    ImportJobStatus.COMPLETED -> "مكتمل"
    ImportJobStatus.PARTIALLY_COMPLETED -> "مكتمل جزئيًا"
    ImportJobStatus.FAILED -> "فشل"
    ImportJobStatus.CANCELLED -> "ملغى"
}

@Composable
private fun statusColor(status: ImportJobStatus) = when (status) {
    ImportJobStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    ImportJobStatus.PARTIALLY_COMPLETED -> MaterialTheme.colorScheme.tertiary
    ImportJobStatus.FAILED, ImportJobStatus.CANCELLED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
