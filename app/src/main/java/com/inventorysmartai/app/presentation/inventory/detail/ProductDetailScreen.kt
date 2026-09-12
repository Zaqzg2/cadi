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
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { AppTopBar(title = "تفاصيل الصنف", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { data ->
                Text(
                    data.summary.product.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
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
}

@Composable
private fun BasicInfoTab(data: ProductDetailData) {
    val p = data.summary.product
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            InfoCard(
                rows = listOfNotNull(
                    "رقم الصنف" to p.itemNumber,
                    p.barcode?.let { "الباركود" to it },
                    p.categoryName?.let { "التصنيف" to it },
                    p.unitName?.let { "الوحدة" to it }
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
    MovementType.MANUAL -> "تعديل يدوي"
}
