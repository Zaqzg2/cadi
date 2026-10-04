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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.SearchField
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.SwipeActionRow

/** Customers / suppliers: search, add, edit (tap or swipe right), delete (swipe left, with confirmation),
 *  and archive/restore for parties that already have documents. */
@Composable
fun PartyListScreen(navController: NavController, viewModel: PartyListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val archived by viewModel.archivedShown.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    /** null = closed; Pair(id or null for "new", initial form). */
    var editing by remember { mutableStateOf<Pair<Long?, PartyForm>?>(null) }
    var pendingDelete by remember { mutableStateOf<PartyItemUi?>(null) }

    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); viewModel.onMessageShown() } }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (archived) "${viewModel.kind.titleAr} — المؤرشفة" else viewModel.kind.titleAr,
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = viewModel::toggleArchived) {
                        Icon(
                            if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                            contentDescription = if (archived) "العودة للقائمة النشطة" else "عرض المؤرشفة"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (!archived) {
                FloatingActionButton(onClick = { editing = null to PartyForm() }) {
                    Icon(Icons.Filled.Add, contentDescription = viewModel.kind.addLabel)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "بحث بالاسم أو الهاتف أو الرقم"
            )
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text(
                            if (query.isNotBlank()) "لا توجد نتائج للبحث" else if (archived) "لا توجد عناصر مؤرشفة" else "لا توجد عناصر بعد — اضغط + للإضافة",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            ) { list ->
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { item ->
                        SwipeActionRow(
                            onDelete = { pendingDelete = item },
                            onEdit = if (archived) null else ({ editing = item.id to viewModel.formFor(item.id) })
                        ) {
                            Card(modifier = Modifier.fillMaxWidth(), onClick = { if (!archived) editing = item.id to viewModel.formFor(item.id) }) {
                                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                        listOfNotNull(item.number?.let { "رقم $it" }, item.phone, item.address).takeIf { it.isNotEmpty() }?.let {
                                            Text(it.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (archived) TextButton(onClick = { viewModel.setActive(item.id, true) }) { Text("استعادة") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { (id, initial) ->
        PartyDialog(
            kind = viewModel.kind,
            isNew = id == null,
            initial = initial,
            onConfirm = { form -> viewModel.save(id, form); editing = null },
            onDismiss = { editing = null }
        )
    }

    pendingDelete?.let { item ->
        DestructiveConfirmDialog(
            title = "حذف \"${item.name}\"؟",
            message = "إن كان له فواتير أو طلبات فلن يُحذف، وسيُعرض عليك أرشفته بدلًا من ذلك.",
            onConfirm = { pendingDelete = null; viewModel.delete(item.id) },
            onDismiss = { pendingDelete = null }
        )
    }

    blocked?.let { b ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBlocked,
            title = { Text("لا يمكن حذف \"${b.name}\"") },
            text = { Text("مرتبط بـ ${b.documents} مستندًا سابقًا. يمكنك أرشفته: يختفي من القوائم وتبقى مستنداته القديمة سليمة، ويمكن استعادته لاحقًا.") },
            confirmButton = { TextButton(onClick = viewModel::archiveBlocked) { Text("أرشفة") } },
            dismissButton = { TextButton(onClick = viewModel::dismissBlocked) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun PartyDialog(kind: PartyKind, isNew: Boolean, initial: PartyForm, onConfirm: (PartyForm) -> Unit, onDismiss: () -> Unit) {
    var form by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) kind.addLabel else "تعديل") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(form.name, { form = form.copy(name = it) }, label = { Text("الاسم *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(form.number, { form = form.copy(number = it) }, label = { Text("الرقم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    form.phone, { form = form.copy(phone = it) }, label = { Text("الهاتف") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(form.address, { form = form.copy(address = it) }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                if (kind == PartyKind.CUSTOMER) {
                    OutlinedTextField(
                        form.extra, { form = form.copy(extra = it) }, label = { Text("الرصيد الافتتاحي") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(form.extra, { form = form.copy(extra = it) }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(form) }, enabled = form.name.isNotBlank()) { Text(if (isNew) "إضافة" else "حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
