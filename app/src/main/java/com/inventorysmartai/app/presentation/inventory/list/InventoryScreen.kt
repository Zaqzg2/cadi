package com.inventorysmartai.app.presentation.inventory.list

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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.DataTable
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.SearchField
import com.inventorysmartai.app.core.designsystem.component.SelectionBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.SwipeActionRow
import com.inventorysmartai.app.core.designsystem.component.TableColumn
import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.presentation.common.startBarcodeScan
import com.inventorysmartai.app.presentation.inventory.components.InventoryFilterSheet
import com.inventorysmartai.app.presentation.inventory.components.ProductCard

private val tabs = listOf(
    InventoryStatusFilter.ALL to "الكل",
    InventoryStatusFilter.AVAILABLE to "متوفر",
    InventoryStatusFilter.LOW to "منخفض",
    InventoryStatusFilter.ZERO to "صفر",
    InventoryStatusFilter.NEAR_EXPIRY to "قريب الانتهاء",
    InventoryStatusFilter.EXPIRED to "منتهي"
)

private fun statusLabel(status: InventoryStatus) = when (status) {
    InventoryStatus.AVAILABLE -> "متوفر"
    InventoryStatus.LOW -> "منخفض"
    InventoryStatus.ZERO -> "صفر"
    InventoryStatus.NEAR_EXPIRY -> "قريب الانتهاء"
    InventoryStatus.EXPIRED -> "منتهي"
}

/** No digit grouping: an editable cell must show text that parses back to the same number. */
private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(navController: NavController, viewModel: InventoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAdvancedSearch by remember { mutableStateOf(false) }
    /** Ids waiting for the user to confirm a delete (single swipe or bulk). */
    var pendingDelete by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val data = (uiState as? com.inventorysmartai.app.core.common.UiState.Success)?.data

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (data?.showArchived == true) "الأصناف المؤرشفة" else "المخزون") },
                actions = {
                    IconButton(onClick = {
                        startBarcodeScan(
                            context,
                            onResult = { code ->
                                viewModel.onBarcodeQueryChange(code)
                                showAdvancedSearch = true
                            }
                        )
                    }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "مسح الباركود") }
                    IconButton(onClick = { showAdvancedSearch = !showAdvancedSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "بحث برقم الصنف / الباركود")
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "الفرز والفلاتر")
                    }
                    IconButton(onClick = viewModel::toggleViewMode) {
                        val table = data?.viewMode != InventoryViewMode.CARDS
                        Icon(
                            if (table) Icons.Filled.ViewAgenda else Icons.Filled.TableChart,
                            contentDescription = if (table) "عرض البطاقات" else "عرض الجدول"
                        )
                    }
                    IconButton(onClick = viewModel::toggleArchived) {
                        val archived = data?.showArchived == true
                        Icon(
                            if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                            contentDescription = if (archived) "العودة للأصناف النشطة" else "عرض المؤرشفة"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (data?.showArchived != true) {
                FloatingActionButton(onClick = { navController.navigate(Destination.ManualEntry.createRoute()) }) {
                    Icon(Icons.Filled.Add, contentDescription = "إضافة صنف")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { d ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SearchField(value = d.filters.searchQuery, onValueChange = viewModel::onSearchQueryChange)
                        if (showAdvancedSearch) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = d.filters.itemNumberQuery,
                                    onValueChange = viewModel::onItemNumberQueryChange,
                                    label = { Text("رقم الصنف") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = d.filters.barcodeQuery,
                                    onValueChange = viewModel::onBarcodeQueryChange,
                                    label = { Text("الباركود") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    val selectedIndex = tabs.indexOfFirst { it.first == d.filters.selectedTab }.coerceAtLeast(0)
                    ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 16.dp) {
                        tabs.forEach { (tab, label) ->
                            Tab(
                                selected = d.filters.selectedTab == tab,
                                onClick = { viewModel.onTabSelected(tab) },
                                text = { Text(label) }
                            )
                        }
                    }

                    SelectionBar(
                        selectedCount = d.selectedIds.size,
                        onClear = viewModel::clearSelection,
                        onDeleteSelected = { pendingDelete = d.selectedIds },
                        extraActions = {
                            TextButton(onClick = { viewModel.setActive(d.selectedIds, active = d.showArchived) }) {
                                Text(if (d.showArchived) "استعادة" else "أرشفة")
                            }
                        }
                    )

                    when {
                        d.totalCount == 0 -> Text(
                            if (d.showArchived) "لا توجد أصناف مؤرشفة" else "لا توجد أصناف بعد — اضغط + لإضافة صنف، أو استورد ملفًا من مركز البيانات",
                            modifier = Modifier.padding(32.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        d.products.isEmpty() -> Text(
                            "لا توجد أصناف مطابقة لهذا الفلتر",
                            modifier = Modifier.padding(32.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        d.viewMode == InventoryViewMode.TABLE -> InventoryTable(
                            data = d,
                            viewModel = viewModel,
                            navController = navController,
                            onDelete = { id -> pendingDelete = setOf(id) },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        else -> LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(d.products, key = { it.product.id }) { summary ->
                                SwipeActionRow(
                                    onDelete = { pendingDelete = setOf(summary.product.id) },
                                    onEdit = {
                                        navController.navigate(Destination.ManualEntry.createRoute(productId = summary.product.id))
                                    }
                                ) {
                                    ProductCard(
                                        summary = summary,
                                        onClick = { navController.navigate(Destination.ProductDetail.createRoute(summary.product.id)) }
                                    )
                                }
                            }
                        }
                    }

                    if (showFilterSheet) {
                        InventoryFilterSheet(
                            branches = d.branches,
                            categories = d.categories,
                            selectedBranchId = d.filters.selectedBranchId,
                            selectedCategoryId = d.filters.selectedCategoryId,
                            sortOrder = d.filters.sortOrder,
                            onBranchSelected = viewModel::onBranchSelected,
                            onCategorySelected = viewModel::onCategorySelected,
                            onSortOrderSelected = viewModel::onSortOrderSelected,
                            onDismiss = { showFilterSheet = false }
                        )
                    }
                }
            }
        }
    }

    if (pendingDelete.isNotEmpty()) {
        val names = data?.products?.filter { it.product.id in pendingDelete }?.map { it.product.name }.orEmpty()
        DestructiveConfirmDialog(
            title = if (pendingDelete.size == 1) "حذف \"${names.firstOrNull().orEmpty()}\"؟" else "حذف ${pendingDelete.size} صنف؟",
            message = "سيُحذف الصنف مع رصيده وحركاته نهائيًا. الأصناف المستخدمة في الجرد أو المشتريات أو المبيعات لن تُحذف، وستُعرض عليك أرشفتها بدلًا من ذلك.",
            onConfirm = { viewModel.deleteProducts(pendingDelete); pendingDelete = emptySet() },
            onDismiss = { pendingDelete = emptySet() }
        )
    }

    if (blocked.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBlocked,
            title = { Text("تعذّر حذف ${blocked.size} صنف") },
            text = {
                Text(
                    "هذه الأصناف مستخدمة في سجلات سابقة:\n" + blocked.take(5).joinToString("\n") { "• ${it.name}" } +
                        (if (blocked.size > 5) "\n… و${blocked.size - 5} أخرى" else "") +
                        "\n\nيمكنك أرشفتها: تختفي من القوائم وتبقى في السجلات القديمة."
                )
            },
            confirmButton = { TextButton(onClick = viewModel::archiveBlocked) { Text("أرشفتها") } },
            dismissButton = { TextButton(onClick = viewModel::dismissBlocked) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun InventoryTable(
    data: InventoryUiData,
    viewModel: InventoryViewModel,
    navController: NavController,
    onDelete: (Long) -> Unit,
    modifier: Modifier
) {
    val byName = compareBy<ProductStockSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.product.name }
    val columns = remember {
        listOf<TableColumn<ProductStockSummary>>(
            TableColumn(
                title = "اسم الصنف", width = 170.dp,
                text = { it.product.name },
                comparator = byName,
                onEdit = { row, text -> viewModel.editField(row.product.id, EditableField.NAME, text) }
            ),
            TableColumn(
                title = "رقم الصنف", width = 110.dp,
                text = { it.product.itemNumber.orEmpty() },
                comparator = compareBy { it.product.itemNumber.orEmpty() },
                onEdit = { row, text -> viewModel.editField(row.product.id, EditableField.ITEM_NUMBER, text) }
            ),
            TableColumn(title = "الباركود", width = 130.dp, text = { it.product.barcode.orEmpty() }),
            TableColumn(
                title = "التصنيف", width = 110.dp,
                text = { it.product.categoryName.orEmpty() },
                comparator = compareBy { it.product.categoryName.orEmpty() }
            ),
            TableColumn(title = "الوحدة", width = 80.dp, text = { it.product.unitName.orEmpty() }),
            TableColumn(
                title = "الكمية", width = 90.dp, numeric = true,
                text = { Formatters.formatNumber(it.totalQuantity) },
                comparator = compareBy { it.totalQuantity }
            ),
            TableColumn(
                title = "الحد الأدنى", width = 100.dp, numeric = true,
                text = { plain(it.product.minStock) },
                comparator = compareBy { it.product.minStock },
                onEdit = { row, text -> viewModel.editField(row.product.id, EditableField.MIN_STOCK, text) }
            ),
            TableColumn(
                title = "إعادة الطلب", width = 100.dp, numeric = true,
                text = { plain(it.product.reorderPoint) },
                comparator = compareBy { it.product.reorderPoint },
                onEdit = { row, text -> viewModel.editField(row.product.id, EditableField.REORDER_POINT, text) }
            ),
            TableColumn(
                title = "الحالة", width = 110.dp,
                text = { statusLabel(it.status) },
                comparator = compareBy { it.status.ordinal }
            ),
            TableColumn(
                title = "تاريخ الإضافة", width = 110.dp,
                text = { if (it.product.createdAt > 0L) Formatters.formatDate(it.product.createdAt) else "" },
                comparator = compareBy { it.product.createdAt }
            ),
            TableColumn(
                title = "أقرب انتهاء", width = 110.dp,
                text = { it.nearestExpiryDate?.let(Formatters::formatDate).orEmpty() },
                comparator = compareBy { it.nearestExpiryDate ?: Long.MAX_VALUE }
            )
        )
    }

    DataTable(
        rows = data.products,
        rowKey = { it.product.id },
        columns = columns,
        modifier = modifier,
        selectedKeys = data.selectedIds,
        onSelectionChange = { keys -> viewModel.setSelection(keys.map { it as Long }.toSet()) },
        onRowClick = { navController.navigate(Destination.ProductDetail.createRoute(it.product.id)) },
        onDelete = { onDelete(it.product.id) }
    )
}
