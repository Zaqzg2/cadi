package com.inventorysmartai.app.presentation.settings.catalog

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.StateContent
import kotlinx.coroutines.launch

/** Branches / categories / units: add, rename (tap the row or the pencil), delete with a confirmation
 *  that states what depends on the item. */
@Composable
fun CatalogListScreen(navController: NavController, viewModel: CatalogViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CatalogItemUi?>(null) }
    var pendingDelete by remember { mutableStateOf<Pair<CatalogItemUi, Int>?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = viewModel.kind.titleAr, onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Card(modifier = Modifier.fillMaxWidth(), onClick = { editing = item }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                    item.secondary?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { editing = item }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "تعديل")
                                }
                                IconButton(onClick = {
                                    scope.launch { pendingDelete = item to viewModel.usageCount(item.id) }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        NameDialog(
            title = viewModel.kind.addLabel,
            initial = "",
            confirmLabel = "إضافة",
            onConfirm = { viewModel.onAdd(it); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }

    editing?.let { item ->
        NameDialog(
            title = "تعديل الاسم",
            initial = item.name,
            confirmLabel = "حفظ",
            onConfirm = { viewModel.onRename(item.id, it); editing = null },
            onDismiss = { editing = null }
        )
    }

    pendingDelete?.let { (item, usage) ->
        val consequence = when (viewModel.kind) {
            CatalogKind.BRANCH ->
                if (usage > 0) "تحذير: سيتم حذف $usage سجل رصيد مخزون مرتبط بهذا الفرع نهائيًا." else "لا توجد أرصدة مرتبطة بهذا الفرع."
            CatalogKind.CATEGORY ->
                if (usage > 0) "$usage صنف مرتبط بهذا التصنيف وسيصبح بلا تصنيف." else "لا توجد أصناف مرتبطة بهذا التصنيف."
            CatalogKind.UNIT ->
                if (usage > 0) "$usage صنف مرتبط بهذه الوحدة وسيصبح بلا وحدة." else "لا توجد أصناف مرتبطة بهذه الوحدة."
        }
        DestructiveConfirmDialog(
            title = "حذف \"${item.name}\"؟",
            message = consequence + "\nلا يمكن التراجع عن هذا الإجراء.",
            onConfirm = { viewModel.onDelete(item.id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("الاسم") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
