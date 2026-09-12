package com.inventorysmartai.app.presentation.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.StatusChip
import com.inventorysmartai.app.domain.model.InventoryStatus

@Composable
fun ReportDetailScreen(navController: NavController, reportType: String, viewModel: ReportsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val type = runCatching { ReportType.valueOf(reportType) }.getOrDefault(ReportType.INVENTORY)

    Scaffold(topBar = { AppTopBar(title = reportTitle(type), onBack = { navController.popBackStack() }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            StateContent(state = uiState, onRetry = {}) { data -> ReportBody(type, data) }
        }
    }
}

@Composable
private fun ReportBody(type: ReportType, data: ReportsData) {
    when (type) {
        ReportType.INVENTORY -> InventoryStatusBreakdown(data.inventoryByStatus)
        ReportType.LOW_STOCK -> SingleStatusCallout(data.inventoryByStatus, InventoryStatus.LOW, "أصناف منخفضة المخزون")
        ReportType.ZERO_STOCK -> SingleStatusCallout(data.inventoryByStatus, InventoryStatus.ZERO, "أصناف بدون مخزون")
        ReportType.EXPIRED -> SingleStatusCallout(data.inventoryByStatus, InventoryStatus.EXPIRED, "أصناف منتهية الصلاحية")
        ReportType.NEAR_EXPIRY -> SingleStatusCallout(data.inventoryByStatus, InventoryStatus.NEAR_EXPIRY, "أصناف قريبة من الانتهاء")
        ReportType.SALES -> BarChartSection(title = "المبيعات — آخر 30 يوماً", series = data.salesByDay)
        ReportType.PURCHASES -> BarChartSection(title = "طلبات الشراء (بالكمية) — آخر 30 يوماً", series = data.purchasesByDay)
        ReportType.COUNTING, ReportType.GOALS, ReportType.BRANCHES ->
            Text("سيتم عرض تفاصيل هذا التقرير هنا — راجع شاشة القسم المخصص لبيانات أكثر تفصيلاً حالياً.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InventoryStatusBreakdown(byStatus: Map<InventoryStatus, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InventoryStatus.entries.forEach { status ->
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusChip(status)
                    Text(Formatters.formatInt(byStatus[status] ?: 0), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SingleStatusCallout(byStatus: Map<InventoryStatus, Int>, status: InventoryStatus, label: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(Formatters.formatInt(byStatus[status] ?: 0), style = MaterialTheme.typography.displayMedium)
        }
    }
}

/** A small hand-drawn bar chart over real local data — demo-scale, no chart library dependency. */
@Composable
private fun BarChartSection(title: String, series: List<Pair<Long, Double>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (series.isEmpty()) {
            Text("لا توجد بيانات كافية بعد لعرض الرسم البياني", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val barColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                drawBars(series.map { it.second }, barColor)
            }
            Text(
                "الإجمالي: ${Formatters.formatNumber(series.sumOf { it.second })}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun DrawScope.drawBars(values: List<Double>, color: androidx.compose.ui.graphics.Color) {
    val max = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val barWidth = size.width / (values.size * 1.5f)
    values.forEachIndexed { index, value ->
        val barHeight = (value / max * size.height).toFloat()
        val x = index * (barWidth * 1.5f)
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
        )
    }
}

private fun reportTitle(type: ReportType): String = when (type) {
    ReportType.INVENTORY -> "تقرير المخزون"
    ReportType.LOW_STOCK -> "منخفض المخزون"
    ReportType.ZERO_STOCK -> "مخزون صفر"
    ReportType.EXPIRED -> "منتهي الصلاحية"
    ReportType.NEAR_EXPIRY -> "قريب الانتهاء"
    ReportType.COUNTING -> "تقرير الجرد"
    ReportType.PURCHASES -> "تقرير المشتريات"
    ReportType.SALES -> "تقرير المبيعات"
    ReportType.GOALS -> "تقرير الأهداف"
    ReportType.BRANCHES -> "تقرير الفروع"
}
