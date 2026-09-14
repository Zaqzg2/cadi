@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.counting.list

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
import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.navigation.Destination

@Composable
fun CountingListScreen(navController: NavController, viewModel: CountingListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("الجرد") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.CountingDetail.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "جرد جديد")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text("لا توجد عمليات جرد بعد — اضغط + لبدء أول جرد", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) { counts ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(counts, key = { it.id }) { count ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navController.navigate(Destination.CountingDetail.createRoute(count.id)) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(count.branchName ?: "فرع", style = MaterialTheme.typography.bodyLarge)
                                    Text(Formatters.formatDate(count.countDate), style = MaterialTheme.typography.labelSmall)
                                }
                                StatusPill(text = statusLabel(count.status), color = statusColor(count.status))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: CountStatus): String = when (status) {
    CountStatus.DRAFT -> "مسودة"
    CountStatus.IN_PROGRESS -> "جارٍ"
    CountStatus.COMPLETED -> "مكتمل"
    CountStatus.CANCELLED -> "ملغى"
}

@Composable
private fun statusColor(status: CountStatus) = when (status) {
    CountStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    CountStatus.CANCELLED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}
