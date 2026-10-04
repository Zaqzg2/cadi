package com.inventorysmartai.app.presentation.datacenter.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.ActionConfirmationDialog
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.CellEmphasis
import com.inventorysmartai.app.core.designsystem.component.DataTable
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.SearchField
import com.inventorysmartai.app.core.designsystem.component.SelectionBar
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.core.designsystem.component.TableColumn
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.RowDecision
import com.inventorysmartai.app.domain.importing.SimpleJson
import com.inventorysmartai.app.domain.model.ImportJobStatus
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.model.ImportRowStatus
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.presentation.common.PickerDialog
import com.inventorysmartai.app.presentation.common.toPickerGroups
import com.inventorysmartai.app.presentation.common.toPickerItems

/** A review row with its field values decoded once (so filtering/search/table cells don't re-parse JSON). */
private data class ReviewLine(val row: ImportRow, val values: Map<String, String>)

private fun statusLabel(status: ImportRowStatus) = when (status) {
    ImportRowStatus.PENDING -> "بانتظار المراجعة"
    ImportRowStatus.MATCHED -> "مطابق"
    ImportRowStatus.NEW_PRODUCT -> "صنف جديد"
    ImportRowStatus.AMBIGUOUS -> "غير مؤكد"
    ImportRowStatus.DUPLICATE -> "مكرر"
    ImportRowStatus.ERROR -> "خطأ"
    ImportRowStatus.ACCEPTED -> "مقبول"
    ImportRowStatus.REJECTED -> "مرفوض"
}

private fun statusEmphasis(status: ImportRowStatus) = when (status) {
    ImportRowStatus.ERROR, ImportRowStatus.DUPLICATE -> CellEmphasis.ERROR
    ImportRowStatus.AMBIGUOUS, ImportRowStatus.PENDING -> CellEmphasis.WARNING
    ImportRowStatus.REJECTED -> CellEmphasis.MUTED
    else -> CellEmphasis.NORMAL
}

private val filterOrder = listOf(
    ImportRowStatus.ERROR, ImportRowStatus.AMBIGUOUS, ImportRowStatus.DUPLICATE, ImportRowStatus.PENDING,
    ImportRowStatus.MATCHED, ImportRowStatus.NEW_PRODUCT, ImportRowStatus.ACCEPTED, ImportRowStatus.REJECTED
)

/**
 * Import review as a TABLE: one column per mapped field (tap a cell to edit — the row is re-validated and
 * re-matched at once), status filters with counts, search, multi-select with bulk accept / reject / delete,
 * and a row sheet to resolve uncertain matches (confirm the suggestion, pick another product, or "new").
 */
@Composable
fun ImportReviewScreen(navController: NavController, viewModel: ImportFlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var statusFilter by remember { mutableStateOf<ImportRowStatus?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }
    var detailRowId by remember { mutableStateOf<Long?>(null) }
    var confirmRejectAll by remember { mutableStateOf(false) }
    var confirmApprove by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeSnackbar() }
    }

    val lines = remember(state.reviewRows) { state.reviewRows.map { ReviewLine(it, SimpleJson.decodeMap(it.normalizedData)) } }
    val counts = remember(state.reviewRows) { state.reviewRows.groupingBy { it.status }.eachCount() }
    val productNames = remember(state.products) { state.products.associate { it.id to it.name } }
    val result = state.approvalResult

    // Names the approval would silently create (the import matches categories/units by exact name).
    val liveLines = lines.filter { it.row.status != ImportRowStatus.REJECTED }
    val newCategories = remember(liveLines, state.categoryNames) {
        liveLines.mapNotNull { it.values[ImportField.CATEGORY.name]?.trim() }.filter { it.isNotEmpty() && it.lowercase() !in state.categoryNames }.distinct()
    }
    val newUnits = remember(liveLines, state.unitNames) {
        liveLines.mapNotNull { it.values[ImportField.UNIT.name]?.trim() }.filter { it.isNotEmpty() && it.lowercase() !in state.unitNames }.distinct()
    }

    val visible = remember(lines, statusFilter, query) {
        val q = query.trim().lowercase()
        lines.filter { line ->
            (statusFilter == null || line.row.status == statusFilter) &&
                (q.isEmpty() || line.values.values.any { it.lowercase().contains(q) } || line.row.errorMessage.orEmpty().lowercase().contains(q))
        }
    }

    // Only fields that actually have a value somewhere get a column; PRODUCT_NAME first.
    val fields = remember(lines, state.importType) {
        val present = lines.flatMap { it.values.keys }.toSet()
        (state.importType?.assignableFields() ?: emptyList()).filter { it.name in present }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "مراجعة الاستيراد",
                onBack = { navController.popBackStack() },
                actions = { if (result == null) TextButton(onClick = { confirmCancel = true }) { Text("إلغاء الاستيراد") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (result != null) {
            ImportResultView(
                modifier = Modifier.padding(padding).fillMaxSize(),
                status = result.status,
                savedRows = result.savedRows,
                failedRows = result.failedRows,
                errorMessage = result.errorMessage,
                notSavedReport = remember(lines) { rejectedReport(lines) },
                onDone = { navController.popBackStack(Destination.DataCenter.route, inclusive = false) }
            )
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = "بحث في الصفوف"
            )
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("الكل (${state.reviewRows.size})") }) }
                items(filterOrder.filter { (counts[it] ?: 0) > 0 }) { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = { statusFilter = if (statusFilter == status) null else status },
                        label = { Text("${statusLabel(status)} (${counts[status]})") }
                    )
                }
            }
            if (newCategories.isNotEmpty() || newUnits.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("سيُنشأ عند الاعتماد:", style = MaterialTheme.typography.labelLarge)
                        if (newCategories.isNotEmpty()) Text("تصنيفات جديدة: " + newCategories.joinToString("، "), style = MaterialTheme.typography.bodySmall)
                        if (newUnits.isNotEmpty()) Text("وحدات جديدة: " + newUnits.joinToString("، "), style = MaterialTheme.typography.bodySmall)
                        Text("إن كان الاسم خطأ إملائيًا فعدّله في العمود قبل الاعتماد.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { viewModel.onAcceptAllValid() }, modifier = Modifier.weight(1f)) { Text("قبول المطابقات الأكيدة") }
                OutlinedButton(onClick = { confirmRejectAll = true }, modifier = Modifier.weight(1f)) { Text("رفض الكل") }
            }

            SelectionBar(
                selectedCount = selected.size,
                onClear = { selected = emptySet() },
                onDeleteSelected = { confirmDeleteSelected = true },
                extraActions = {
                    TextButton(onClick = { viewModel.onBulkDecision(selected.map { it as Long }, RowDecision.Accept); selected = emptySet() }) { Text("قبول") }
                    TextButton(onClick = { viewModel.onBulkDecision(selected.map { it as Long }, RowDecision.Reject); selected = emptySet() }) { Text("رفض") }
                }
            )

            val columns = remember(fields, productNames) {
                buildList<TableColumn<ReviewLine>> {
                    add(TableColumn(title = "الصف", width = 56.dp, numeric = true, text = { (it.row.rowIndex + 1).toString() }, comparator = compareBy { it.row.rowIndex }))
                    add(TableColumn(
                        title = "الحالة", width = 100.dp,
                        text = { statusLabel(it.row.status) },
                        comparator = compareBy { it.row.status.ordinal },
                        emphasis = { statusEmphasis(it.row.status) }
                    ))
                    fields.forEach { field ->
                        add(TableColumn(
                            title = field.labelAr,
                            width = if (field == ImportField.PRODUCT_NAME) 170.dp else 110.dp,
                            numeric = field.isNumeric,
                            text = { it.values[field.name].orEmpty() },
                            comparator = compareBy { it.values[field.name].orEmpty() },
                            onEdit = { line, text -> viewModel.onEditRow(line.row.id, mapOf(field to text.ifBlank { null })) }
                        ))
                    }
                    add(TableColumn(
                        title = "الصنف المطابق", width = 150.dp,
                        text = { line ->
                            val row = line.row
                            when {
                                row.matchedProductId != null -> productNames[row.matchedProductId].orEmpty()
                                row.status == ImportRowStatus.AMBIGUOUS && row.suggestedProductId != null ->
                                    "؟ " + productNames[row.suggestedProductId].orEmpty() + (row.confidence?.let { " (${(it * 100).toInt()}%)" } ?: "")
                                row.status == ImportRowStatus.NEW_PRODUCT -> "صنف جديد"
                                else -> ""
                            }
                        },
                        emphasis = { if (it.row.status == ImportRowStatus.AMBIGUOUS) CellEmphasis.WARNING else CellEmphasis.NORMAL }
                    ))
                    add(TableColumn(
                        title = "ملاحظات", width = 220.dp,
                        text = { line -> line.row.errorMessage ?: SimpleJson.decodeList(line.row.warningsJson).firstOrNull().orEmpty() },
                        emphasis = { if (it.row.errorMessage != null) CellEmphasis.ERROR else CellEmphasis.WARNING }
                    ))
                }
            }

            DataTable(
                rows = visible,
                rowKey = { it.row.id },
                columns = columns,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                selectedKeys = selected,
                onSelectionChange = { keys -> selected = keys },
                onRowClick = { detailRowId = it.row.id },
                showRowNumbers = false,
                emptyText = if (state.reviewRows.isEmpty()) "لا توجد صفوف بعد" else "لا توجد صفوف مطابقة لهذا الفلتر"
            )

            Button(
                onClick = { confirmApprove = true },
                enabled = !state.isBusy && state.reviewRows.any { it.status == ImportRowStatus.ACCEPTED },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("اعتماد الاستيراد (${counts[ImportRowStatus.ACCEPTED] ?: 0} صف مقبول)")
            }
        }
    }

    if (confirmRejectAll) {
        DestructiveConfirmDialog(
            title = "رفض كل الصفوف؟",
            message = "سيتم رفض جميع الصفوف (${state.reviewRows.size}) ويمكنك قبول ما تريد منها بعد ذلك.",
            confirmLabel = "رفض الكل",
            onConfirm = { confirmRejectAll = false; viewModel.onRejectAll() },
            onDismiss = { confirmRejectAll = false }
        )
    }
    if (confirmApprove) {
        val acceptedCount = counts[ImportRowStatus.ACCEPTED] ?: 0
        ActionConfirmationDialog(
            title = "اعتماد الاستيراد",
            actionDescriptionAr = "سيتم حفظ $acceptedCount صفًا مقبولًا في قاعدة البيانات، وتجاهل الباقي. هل تريد المتابعة؟",
            onConfirm = { confirmApprove = false; viewModel.approveImport() },
            onDismiss = { confirmApprove = false }
        )
    }
    if (confirmCancel) {
        DestructiveConfirmDialog(
            title = "إلغاء الاستيراد؟",
            message = "لن يتم حفظ أي شيء من هذا الملف، وسيُسجَّل الاستيراد كملغى.",
            confirmLabel = "إلغاء الاستيراد",
            onConfirm = {
                confirmCancel = false
                viewModel.cancelImport { navController.popBackStack(Destination.DataCenter.route, inclusive = false) }
            },
            onDismiss = { confirmCancel = false }
        )
    }
    if (confirmDeleteSelected) {
        DestructiveConfirmDialog(
            title = "حذف ${selected.size} صف؟",
            message = "ستُحذف هذه الصفوف من الاستيراد نهائيًا (لا تأثير على بياناتك الحالية).",
            onConfirm = { confirmDeleteSelected = false; viewModel.onDeleteRows(selected.map { it as Long }); selected = emptySet() },
            onDismiss = { confirmDeleteSelected = false }
        )
    }

    // Always re-read the row from the live list: after an edit the dialog must show the NEW status/errors.
    val detailLine = detailRowId?.let { id -> lines.firstOrNull { it.row.id == id } }
    if (detailRowId != null && detailLine == null) detailRowId = null
    detailLine?.let { line ->
        RowDetailDialog(
            line = line,
            importType = state.importType,
            products = state.products,
            onClose = { detailRowId = null },
            onSaveFields = { edits -> viewModel.onEditRow(line.row.id, edits) },
            onDecision = { decision -> viewModel.onRowDecision(line.row.id, decision) },
            onResolve = { decision -> viewModel.onResolveMatch(line.row.id, decision) },
            onDelete = { viewModel.onDeleteRows(listOf(line.row.id)); detailRowId = null }
        )
    }
}

@Composable
private fun RowDetailDialog(
    line: ReviewLine,
    importType: ImportType?,
    products: List<Product>,
    onClose: () -> Unit,
    onSaveFields: (Map<ImportField, String?>) -> Unit,
    onDecision: (RowDecision) -> Unit,
    onResolve: (RowDecision) -> Unit,
    onDelete: () -> Unit
) {
    val row = line.row
    val editableFields = remember(importType) { importType?.assignableFields() ?: emptyList() }
    val inputs = remember(row.id) { mutableStateOf(editableFields.associateWith { line.values[it.name].orEmpty() }) }
    var pickingProduct by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val warnings = remember(row.warningsJson) { SimpleJson.decodeList(row.warningsJson) }
    val raw = remember(row.rawData) { SimpleJson.decodeMap(row.rawData) }
    val canAccept = row.errorCode == null && row.status != ImportRowStatus.AMBIGUOUS

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("الصف ${row.rowIndex + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    StatusPill(
                        text = statusLabel(row.status),
                        color = when (statusEmphasis(row.status)) {
                            CellEmphasis.ERROR -> MaterialTheme.colorScheme.error
                            CellEmphasis.WARNING -> MaterialTheme.colorScheme.tertiary
                            CellEmphasis.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
                            CellEmphasis.NORMAL -> MaterialTheme.colorScheme.primary
                        }
                    )
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    warnings.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                    if (row.edited) Text("عُدّل هذا الصف يدويًا", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (row.errorCode == null) {
                        MatchSection(
                            row = row,
                            products = products,
                            onUseSuggestion = { id -> onResolve(RowDecision.ChangeMatch(id)) },
                            onPickOther = { pickingProduct = true },
                            onNew = { onResolve(RowDecision.CreateNewProduct) }
                        )
                    }

                    Text("بيانات الصف", style = MaterialTheme.typography.titleSmall)
                    editableFields.forEach { field ->
                        OutlinedTextField(
                            value = inputs.value[field].orEmpty(),
                            onValueChange = { v -> inputs.value = inputs.value + (field to v) },
                            label = { Text(field.labelAr) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (field.isNumeric) KeyboardType.Decimal else KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedButton(
                        onClick = { onSaveFields(inputs.value.mapValues { it.value.ifBlank { null } }) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("حفظ التعديلات وإعادة التحقق") }

                    if (raw.isNotEmpty()) {
                        Text("القيم الأصلية في الملف (لا تتغير)", style = MaterialTheme.typography.titleSmall)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                raw.forEach { (header, value) -> Text("$header: $value", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDecision(RowDecision.Accept); onClose() }, enabled = canAccept, modifier = Modifier.weight(1f)) { Text("قبول") }
                    OutlinedButton(onClick = { onDecision(RowDecision.Reject); onClose() }, modifier = Modifier.weight(1f)) { Text("رفض") }
                    TextButton(onClick = { confirmDelete = true }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (pickingProduct) {
        PickerDialog(
            title = "اختر الصنف المطابق",
            items = products.toPickerItems(),
            groups = products.toPickerGroups(),
            initiallySelected = setOfNotNull(row.matchedProductId ?: row.suggestedProductId),
            onConfirm = { ids -> pickingProduct = false; ids.firstOrNull()?.let { onResolve(RowDecision.ChangeMatch(it)) } },
            onDismiss = { pickingProduct = false }
        )
    }
    if (confirmDelete) {
        DestructiveConfirmDialog(
            title = "حذف هذا الصف؟",
            message = "سيُحذف الصف من الاستيراد نهائيًا.",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Shows what the row will be attached to and lets the person decide: the pipeline's suggestion, another
 *  product from the list, or "it is a new product". An uncertain row cannot be accepted any other way. */
@Composable
private fun MatchSection(
    row: ImportRow,
    products: List<Product>,
    onUseSuggestion: (Long) -> Unit,
    onPickOther: () -> Unit,
    onNew: () -> Unit
) {
    val matched = products.firstOrNull { it.id == row.matchedProductId }
    val suggested = products.firstOrNull { it.id == row.suggestedProductId }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("المطابقة مع المخزون", style = MaterialTheme.typography.titleSmall)
            when {
                matched != null -> Text("مرتبط بالصنف: ${matched.name}", style = MaterialTheme.typography.bodyMedium)
                row.status == ImportRowStatus.AMBIGUOUS && suggested != null -> {
                    Text(
                        "غير مؤكد — الأقرب: ${suggested.name}" + (row.confidence?.let { " (تشابه ${(it * 100).toInt()}%)" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Button(onClick = { onUseSuggestion(suggested.id) }, modifier = Modifier.fillMaxWidth()) { Text("نعم، هو «${suggested.name}»") }
                }
                else -> Text("لا يوجد صنف مطابق — سيُنشأ كصنف جديد عند الاعتماد", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickOther, modifier = Modifier.weight(1f)) { Text("اختيار صنف آخر") }
                OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f)) { Text("صنف جديد") }
            }
        }
    }
}

@Composable
private fun ImportResultView(
    modifier: Modifier,
    status: ImportJobStatus,
    savedRows: Int,
    failedRows: Int,
    errorMessage: String?,
    notSavedReport: String?,
    onDone: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val (title, color) = when (status) {
            ImportJobStatus.COMPLETED -> "تم الاستيراد بنجاح" to MaterialTheme.colorScheme.primary
            ImportJobStatus.PARTIALLY_COMPLETED -> "تم الاستيراد جزئيًا" to MaterialTheme.colorScheme.tertiary
            else -> "فشل اعتماد الاستيراد" to MaterialTheme.colorScheme.error
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = color)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("الصفوف المحفوظة: $savedRows")
                Text("الصفوف غير المحفوظة: $failedRows")
                if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
        if (notSavedReport != null) {
            OutlinedButton(
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "الصفوف غير المحفوظة من الاستيراد")
                        putExtra(android.content.Intent.EXTRA_TEXT, notSavedReport)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, "مشاركة الصفوف غير المحفوظة"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("مشاركة الصفوف غير المحفوظة") }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("العودة إلى مركز البيانات") }
    }
}

/** Every row that was NOT accepted, with its status and reason, as plain text (one line per row) — so what
 *  was left out of an import can be fixed in the source file and imported again. Null when nothing was left out. */
private fun rejectedReport(lines: List<ReviewLine>): String? {
    val left = lines.filter { it.row.status != ImportRowStatus.ACCEPTED }
    if (left.isEmpty()) return null
    return left.joinToString("\n") { line ->
        val name = line.values[ImportField.PRODUCT_NAME.name] ?: line.values[ImportField.ITEM_NUMBER.name] ?: line.values[ImportField.BARCODE.name] ?: "—"
        val why = line.row.errorMessage ?: SimpleJson.decodeList(line.row.warningsJson).firstOrNull().orEmpty()
        "صف ${line.row.rowIndex + 1} | $name | ${statusLabel(line.row.status)}" + if (why.isNotBlank()) " | $why" else ""
    }
}
