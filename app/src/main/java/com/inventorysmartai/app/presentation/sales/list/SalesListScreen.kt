@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.sales.list

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.SearchField
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.SwipeActionRow
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.domain.model.SalesInvoice

@Composable
fun SalesListScreen(navController: NavController, viewModel: SalesListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<SalesInvoice?>(null) }

    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); viewModel.onMessageShown() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("المبيعات") }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.SalesDetail.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "فاتورة بيع جديدة")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text(if (query.isNotBlank()) "لا توجد نتائج للبحث" else "لا توجد فواتير بيع بعد — اضغط + لإنشاء فاتورة جديدة", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) { list ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { item ->
                        // swipe: delete (drafts only — the ViewModel explains why otherwise), tap: open
                        SwipeActionRow(onDelete = { if (viewModel.canDelete(item)) pendingDelete = item else viewModel.delete(item) }) {
                            Card(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Destination.SalesDetail.createRoute(item.id)) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.invoiceNumber, style = MaterialTheme.typography.bodyLarge)
                                        Text(item.customerName ?: "عميل نقدي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(Formatters.formatCurrency(item.total), style = MaterialTheme.typography.titleMedium)
                                        StatusPill(text = if (item.status == InvoiceStatus.DRAFT) "مسودة" else if (item.status == InvoiceStatus.CONFIRMED) "مؤكدة" else "ملغاة", color = if (item.status == InvoiceStatus.CONFIRMED) MaterialTheme.colorScheme.primary else if (item.status == InvoiceStatus.CANCELLED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DestructiveConfirmDialog(
            title = "حذف المسودة؟",
            message = "ستُحذف المسودة وأصنافها نهائيًا. لا تأثير على المخزون.",
            onConfirm = { pendingDelete = null; viewModel.delete(item) },
            onDismiss = { pendingDelete = null }
        )
    }
}
