package com.inventorysmartai.app.presentation.parties

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StateContent

/** Data Center section B: a minimal list+add screen for customers/suppliers, mirroring
 *  CatalogListScreen's branches/categories/units pattern (see PartyListViewModel doc comment). */
@Composable
fun PartyListScreen(navController: NavController, viewModel: PartyListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    Scaffold(
        topBar = { AppTopBar(title = viewModel.kind.titleAr, onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = viewModel.kind.addLabel)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text("لا توجد عناصر بعد — اضغط + للإضافة", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) { items ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                item.phone?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(viewModel.kind.addLabel) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("الاسم") })
                        OutlinedTextField(value = newPhone, onValueChange = { newPhone = it }, singleLine = true, label = { Text("الهاتف (اختياري)") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addParty(newName, newPhone)
                        newName = ""
                        newPhone = ""
                        showAddDialog = false
                    }) { Text("إضافة") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") }
                }
            )
        }
    }
}
