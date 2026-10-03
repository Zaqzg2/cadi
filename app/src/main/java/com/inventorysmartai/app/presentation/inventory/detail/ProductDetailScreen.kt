package com.inventorysmartai.app.presentation.inventory.detail

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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.common.UiState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.StatusChip
import com.inventorysmartai.app.domain.model.InventoryMovement
import com.inventorysmartai.app.domain.model.MovementType

private val detailTabs = listOf("الأساسية", "المخزون بالفروع", "الحركات", "الجرد", "المشتريات", "المبيعات", "المرفقات")

@Composable
fun ProductDetailScreen(navController: NavController, viewModel: ProductDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    /** null = no dialog; otherwise the number of document lines using the product. */
    var deleteDialogRefs by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(finished) { if (finished) navController.popBackStack() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.onMessageShown() }
    }

    val loaded = (uiState as? UiState.Success)?.data

    Scaffold(
        topBar = {
            AppTopBar(
                title = "تفاصيل الصنف",
                onBack = { navController.popBackStack() },
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = {
                            navController.navigate(Destination.ManualEntry.createRoute(productId = loaded.summary.product.id))
                        }) { Icon(Icons.Filled.Edit, contentDescription = "تعديل الصنف") }
                        IconButton(onClick = {
                            scope.launch { deleteDialogRefs = viewModel.documentReferenceCount() }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "حذف الصنف") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { data ->
                Text(
                    data.summary.product.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
                if (!data.summary.product.isActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("هذا الصنف مؤرشف ولا يظهر في القوائم", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.setActive(true) }) { Text("استعادة") }
                    }
                }
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                    detailTabs.forEachIndexed { index, label ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                    }
                }
                when (selectedTab) {
                    0 -> BasicInfoTab(data)
                    1 -> BranchStockTab(data)
                    2 -> MovementsTab(data.movements, "لا توجد حركات مسجلة بعد")
                    3 -> MovementsTab(data.countMovements, "لا توجد تسويات جرد مرتبطة بهذا الصنف بعد")
                    4 -> MovementsTab(data.purchaseMovements, "لا توجد مشتريات مرتبطة بهذا الصنف بعد")
                    5 -> MovementsTab(data.saleMovements, "لا توجد مبيعات مرتبطة بهذا الصنف بعد")
                    6 -> AttachmentsTab(data.attachmentCount)
                }
            }
        }
    }

    deleteDialogRefs?.let { refs ->
        val stock = loaded?.summary?.totalQuantity ?: 0.0
        if (refs > 0) {
            // Used by counts / purchases / sales: deleting would break that history, so offer archive.
            AlertDialog(
                onDismissRequest = { deleteDialogRefs = null },
                title = { Text("لا يمكن حذف هذا الصنف") },
                text = { Text("الصنف مستخدم في $refs سطرًا من الجرد أو المشتريات أو المبيعات، وحذفه سيُفسد هذه السجلات.\nيمكنك أرشفته: يختفي من كل القوائم ويبقى في السجلات القديمة، ويمكن استعادته لاحقًا.") },
                confirmButton = { TextButton(onClick = { deleteDialogRefs = null; viewModel.setActive(false) }) { Text("أرشفة الصنف") } },
                dismissButton = { TextButton(onClick = { deleteDialogRefs = null }) { Text("إلغاء") } }
            )
        } else {
            DestructiveConfirmDialog(
                title = "حذف \"${loaded?.summary?.product?.name.orEmpty()}\"؟",
                message = (if (stock > 0) "سيُحذف الصنف مع رصيده الحالي (${Formatters.formatNumber(stock)}) وكل حركاته نهائيًا." else "سيُحذف الصنف وحركاته نهائيًا.") +
                    "\nلا يمكن التراجع عن هذا الإجراء.",
                onConfirm = { deleteDialogRefs = null; viewModel.delete() },
                onDismiss = { deleteDialogRefs = null }
            )
        }
    }
}

@Composable
private fun BasicInfoTab(data: ProductDetailData) {
    val p = data.summary.product
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            InfoCard(
                rows = listOfNotNull(
                    p.itemNumber?.let { "رقم الصنف" to it },
                    p.barcode?.let { "الباركود" to it },
                    p.categoryName?.let { "التصنيف" to it },
                    p.unitName?.let { "الوحدة" to it },
                    p.defaultPrice?.let { "السعر الافتراضي" to Formatters.formatCurrency(it) },
                    (if (p.createdAt > 0L) Formatters.formatDate(p.createdAt) else null)?.let { "تاريخ الإضافة" to it },
                    data.summary.nearestExpiryDate?.let { "أقرب انتهاء" to Formatters.formatDate(it) }
                )
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("الحالة", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(data.summary.status)
            }
        }
        item {
            InfoCard(
                rows = listOf(
                    "الكمية الحالية" to Formatters.formatNumber(data.summary.totalQuantity),
                    "الحد الأدنى" to Formatters.formatNumber(p.minStock),
                    "نقطة إعادة الطلب" to Formatters.formatNumber(p.reorderPoint)
                )
            )
        }
    }
}

@Composable
private fun BranchStockTab(data: ProductDetailData) {
    if (data.summary.branchStocks.isEmpty()) {
        EmptyTabMessage("لا يوجد رصيد مسجل في أي فرع بعد")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(data.summary.branchStocks) { stock ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stock.branchName, style = MaterialTheme.typography.bodyLarge)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Formatters.formatNumber(stock.quantity), style = MaterialTheme.typography.titleMedium)
                        stock.expiryDate?.let {
                            Text("ينتهي: ${Formatters.formatDate(it)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementsTab(movements: List<InventoryMovement>, emptyMessage: String) {
    if (movements.isEmpty()) {
        EmptyTabMessage(emptyMessage)
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(movements) { movement ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(movementLabel(movement.movementType), style = MaterialTheme.typography.bodyLarge)
                        Text(Formatters.formatDate(movement.createdAt), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        (if (movement.quantityChange >= 0) "+" else "") + Formatters.formatNumber(movement.quantityChange),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentsTab(count: Int) {
    EmptyTabMessage(if (count == 0) "لا توجد مرفقات بعد" else "$count مرفق — إدارة المرفقات ستتوفر في مرحلة لاحقة")
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(message: String) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun movementLabel(type: MovementType): String = when (type) {
    MovementType.PURCHASE_IN -> "استلام مشتريات"
    MovementType.SALE_OUT -> "بيع"
    MovementType.COUNT_ADJUSTMENT -> "تسوية جرد"
    MovementType.TRANSFER_IN -> "تحويل وارد"
    MovementType.TRANSFER_OUT -> "تحويل صادر"
    MovementType.RETURN_IN -> "مرتجع وارد"
    MovementType.RETURN_OUT -> "مرتجع صادر"
    MovementType.MANUAL -> "تعديل يدوي"
}
