@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.stockops

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.core.designsystem.component.rememberTopBarSearch
import com.inventorysmartai.app.domain.stockops.StockOperation
import com.inventorysmartai.app.domain.stockops.StockOperationType
import com.inventorysmartai.app.navigation.Destination

/** سجل التحويل بين الفروع والمرتجعات — نفس نمط الجرد والمشتريات: بحث بالأيقونة، بطاقات، وزر + لعملية جديدة. */
@Composable
fun StockOperationsScreen(navController: NavController, viewModel: StockOperationsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<StockOperation?>(null) }
    val search = rememberTopBarSearch(query = query, onQueryChange = viewModel::onQueryChange, placeholder = "بحث بالصنف أو الفرع أو الرقم")

    Scaffold(
        topBar = { AppTopBar(title = "التحويل والمرتجعات", onBack = { navController.popBackStack() }, search = search) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.StockOperationNew.route) }) {
                Icon(Icons.Filled.Add, contentDescription = "عملية جديدة")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text(
                            if (query.isNotBlank()) "لا توجد نتائج للبحث" else "لا توجد عمليات بعد — اضغط + لتحويل بضاعة بين الفروع أو تسجيل مرتجع",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            ) { operations ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(operations, key = { "${it.type.name}-${it.number}" }) { operation ->
                        Card(modifier = Modifier.fillMaxWidth(), onClick = { details = operation }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${operation.type.titleAr} #${operation.number}", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${route(operation)} • ${operation.lines.size} صنف • ${Formatters.formatDate(operation.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(text = operation.type.shortAr, color = accent(operation.type))
                            }
                        }
                    }
                }
            }
        }
    }

    details?.let { operation ->
        AlertDialog(
            onDismissRequest = { details = null },
            confirmButton = { TextButton(onClick = { details = null }) { Text("إغلاق") } },
            title = { Text("${operation.type.titleAr} #${operation.number}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(route(operation), style = MaterialTheme.typography.bodyMedium)
                    operation.lines.forEach { line ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(line.productName, modifier = Modifier.weight(1f))
                            Text(Formatters.formatNumber(line.quantity), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    operation.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        )
    }
}

/** «من X إلى Y» للتحويل، واسم الفرع للمرتجعات. */
private fun route(operation: StockOperation): String = when (operation.type) {
    StockOperationType.TRANSFER -> "من ${operation.branchName ?: "—"} إلى ${operation.toBranchName ?: "—"}"
    else -> operation.branchName ?: "—"
}

@Composable
private fun accent(type: StockOperationType): Color = when (type) {
    StockOperationType.TRANSFER -> MaterialTheme.colorScheme.primary
    StockOperationType.SALES_RETURN -> MaterialTheme.colorScheme.secondary
    StockOperationType.PURCHASE_RETURN -> MaterialTheme.colorScheme.tertiary
}
