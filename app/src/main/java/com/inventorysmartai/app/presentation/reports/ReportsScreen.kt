package com.inventorysmartai.app.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.theme.LocalInventoryStatusColors
import com.inventorysmartai.app.domain.model.InventoryStatus

/** لوحة تحكم: مؤشرات + توزيع الحالات + مخططات 30 يومًا، ثم جدول التقرير المختار القابل للطباعة. */
@Composable
fun ReportsScreen(navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(ReportType.INVENTORY) }
    val data = (uiState as? UiState.Success)?.data
    val table = data?.let { buildReportTable(selected, it) }

    Scaffold(topBar = { AppTopBar(title = "التقارير") }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { d ->
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Kpis(d.inventoryByStatus) }
                    item { StatusBar(d.inventoryByStatus) }
                    item { ChartCard("المبيعات — آخر 30 يومًا", d.salesByDay) }
                    item { ChartCard("المشتريات — آخر 30 يومًا", d.purchasesByDay) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(reportTiles) { (type, label) ->
                                FilterChip(selected = selected == type, onClick = { selected = type }, label = { Text(label) })
                            }
                        }
                    }
                    // أزرار التحميل والمشاركة والطباعة فوق الجدول مباشرة، وبأسمائها كي لا يبحث عنها المستخدم.
                    item { ReportExportBar(table) }
                    if (table == null) {
                        item { Text("لا يوجد جدول لهذا التقرير", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        item {
                            Text("${table.title} — ${Formatters.formatInt(table.rows.size)} صف", style = MaterialTheme.typography.titleSmall)
                        }
                        item { ReportTableHeader(table.headers) }
                        itemsIndexed(table.displayRows) { i, row -> ReportTableRow(row, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Kpis(by: Map<InventoryStatus, Int>) {
    val c = LocalInventoryStatusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("الإجمالي", by.values.sum(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            Kpi("متوفر", by[InventoryStatus.AVAILABLE] ?: 0, c.available, Modifier.weight(1f))
            Kpi("منخفض", by[InventoryStatus.LOW] ?: 0, c.low, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("صفر", by[InventoryStatus.ZERO] ?: 0, c.zero, Modifier.weight(1f))
            Kpi("قريب الانتهاء", by[InventoryStatus.NEAR_EXPIRY] ?: 0, c.nearExpiry, Modifier.weight(1f))
            Kpi("منتهي", by[InventoryStatus.EXPIRED] ?: 0, c.expired, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Kpi(label: String, value: Int, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(Formatters.formatInt(value), style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun StatusBar(by: Map<InventoryStatus, Int>) {
    val c = LocalInventoryStatusColors.current
    val parts = listOf(
        (by[InventoryStatus.AVAILABLE] ?: 0) to c.available, (by[InventoryStatus.LOW] ?: 0) to c.low,
        (by[InventoryStatus.ZERO] ?: 0) to c.zero, (by[InventoryStatus.NEAR_EXPIRY] ?: 0) to c.nearExpiry,
        (by[InventoryStatus.EXPIRED] ?: 0) to c.expired
    )
    Card(shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("توزيع حالة المخزون", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                parts.filter { it.first > 0 }.forEach { (n, col) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(col)) }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, series: List<Pair<Long, Double>>) {
    Card(shape = MaterialTheme.shapes.medium) { Column(modifier = Modifier.padding(12.dp)) { BarChartSection(title, series) } }
}
