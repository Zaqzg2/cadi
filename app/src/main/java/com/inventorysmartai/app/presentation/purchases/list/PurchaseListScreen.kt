@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.purchases.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.navigation.Destination

@Composable
fun PurchaseListScreen(navController: NavController, viewModel: PurchaseListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("المشتريات") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.PurchaseDetail.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "طلب شراء جديد")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text("لا توجد طلبات شراء بعد — اضغط + لإنشاء طلب جديد", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) { requests ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(requests, key = { it.id }) { request ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navController.navigate(Destination.PurchaseDetail.createRoute(request.id)) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(request.requestNumber, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        request.supplierName ?: "بدون مورد",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(text = statusLabel(request.status), color = statusColor(request.status))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: PurchaseStatus): String = when (status) {
    PurchaseStatus.DRAFT -> "مسودة"
    PurchaseStatus.SUBMITTED -> "مُرسل"
    PurchaseStatus.APPROVED -> "مُعتمد"
    PurchaseStatus.ORDERED -> "تم الطلب"
    PurchaseStatus.RECEIVED -> "مُستلم"
    PurchaseStatus.CANCELLED -> "ملغى"
}

@Composable
private fun statusColor(status: PurchaseStatus) = when (status) {
    PurchaseStatus.RECEIVED -> MaterialTheme.colorScheme.primary
    PurchaseStatus.CANCELLED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}
