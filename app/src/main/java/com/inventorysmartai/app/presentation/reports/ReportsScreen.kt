package com.inventorysmartai.app.presentation.reports

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
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
import com.inventorysmartai.app.domain.model.ProductStockSummary

private val reportTiles = listOf(
    ReportType.INVENTORY to "المخزون",
    ReportType.LOW_STOCK to "منخفض",
    ReportType.ZERO_STOCK to "صفر",
    ReportType.NEAR_EXPIRY to "قريب الانتهاء",
    ReportType.EXPIRED to "منتهي",
    ReportType.BRANCHES to "الفروع",
    ReportType.SALES to "المبيعات",
    ReportType.PURCHASES to "المشتريات"
)

private class ReportTable(val title: String, val headers: List<String>, val rows: List<List<String>>)

/** لوحة تحكم: مؤشرات + توزيع الحالات + مخططات 30 يومًا، ثم جدول التقرير المختار القابل للطباعة. */
@Composable
fun ReportsScreen(navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(ReportType.INVENTORY) }
    val data = (uiState as? UiState.Success)?.data
    val table = data?.let { buildTable(selected, it) }

    Scaffold(topBar = {
        AppTopBar(title = "التقارير", actions = {
            IconButton(enabled = table != null, onClick = { table?.let { printTable(context, it) } }) {
                Icon(Icons.Filled.Print, contentDescription = "طباعة الجدول")
            }
        })
    }) { padding ->
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
                    if (table == null) {
                        item { Text("لا يوجد جدول لهذا التقرير", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        item {
                            Text("${table.title} — ${Formatters.formatInt(table.rows.size)} صف", style = MaterialTheme.typography.titleSmall)
                        }
                        item { TableHeader(table.headers) }
                        itemsIndexed(table.rows) { i, row -> TableRow(row, i) }
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

private fun weightFor(i: Int, n: Int): Float = if (n >= 6) (if (i == 1) 2.4f else if (i == 0) 0.6f else 1f) else (if (i == 0) 2f else 1f)

@Composable
private fun TableHeader(headers: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        headers.forEachIndexed { i, h ->
            Text(h, modifier = Modifier.weight(weightFor(i, headers.size)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, index: Int) {
    val bg = if (index % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow
    Row(modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 10.dp, vertical = 8.dp)) {
        cells.forEachIndexed { i, cell -> Text(cell, modifier = Modifier.weight(weightFor(i, cells.size)), style = MaterialTheme.typography.bodySmall) }
    }
}

private fun statusLabel(s: InventoryStatus) = when (s) {
    InventoryStatus.AVAILABLE -> "متوفر"
    InventoryStatus.LOW -> "منخفض"
    InventoryStatus.ZERO -> "صفر"
    InventoryStatus.NEAR_EXPIRY -> "قريب الانتهاء"
    InventoryStatus.EXPIRED -> "منتهي"
}

private fun buildTable(type: ReportType, d: ReportsData): ReportTable? {
    val title = reportTiles.firstOrNull { it.first == type }?.second ?: return null
    fun products(filter: (ProductStockSummary) -> Boolean) = ReportTable(
        title, listOf("#", "الصنف", "الكمية", "الحد الأدنى", "الحالة", "الانتهاء"),
        d.products.filter(filter).mapIndexed { i, p ->
            listOf(
                (i + 1).toString(), p.product.name, Formatters.formatNumber(p.totalQuantity), Formatters.formatNumber(p.product.minStock),
                statusLabel(p.status), p.nearestExpiryDate?.let { Formatters.formatDate(it) } ?: "—"
            )
        }
    )
    fun days(series: List<Pair<Long, Double>>) = ReportTable(
        title, listOf("اليوم", "الإجمالي"),
        series.map { (day, total) ->
            listOf(Formatters.formatDate(if (day > 100_000_000_000L) day else day * 86_400_000L), Formatters.formatNumber(total))
        }
    )
    return when (type) {
        ReportType.INVENTORY -> products { true }
        ReportType.LOW_STOCK -> products { it.status == InventoryStatus.LOW }
        ReportType.ZERO_STOCK -> products { it.status == InventoryStatus.ZERO }
        ReportType.NEAR_EXPIRY -> products { it.status == InventoryStatus.NEAR_EXPIRY }
        ReportType.EXPIRED -> products { it.status == InventoryStatus.EXPIRED }
        ReportType.SALES -> days(d.salesByDay)
        ReportType.PURCHASES -> days(d.purchasesByDay)
        ReportType.BRANCHES -> ReportTable(
            title, listOf("الفرع", "عدد البنود", "إجمالي الكمية"),
            d.products.flatMap { it.branchStocks }.groupBy { it.branchName }.map { (name, list) ->
                listOf(name, Formatters.formatInt(list.size), Formatters.formatNumber(list.sumOf { it.quantity }))
            }
        )
        else -> null
    }
}

// يُحتفظ بالمرجع حتى ينتهي إنشاء مستند الطباعة (وإلا قد يجمعه Garbage Collector قبل الطباعة).
private var printWebView: WebView? = null

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun printTable(context: Context, t: ReportTable) {
    val html = buildString {
        append("<html dir='rtl' lang='ar'><head><meta charset='utf-8'><style>")
        append("body{font-family:sans-serif;padding:16px}h2{margin:0 0 4px}p{color:#666;margin:0 0 12px;font-size:12px}")
        append("table{width:100%;border-collapse:collapse;font-size:13px}th,td{border:1px solid #bbb;padding:6px 8px;text-align:right}")
        append("th{background:#e3f1ec}tr:nth-child(even){background:#f6f6f6}</style></head><body>")
        append("<h2>${esc(t.title)}</h2><p>${Formatters.formatDate(System.currentTimeMillis())} — ${t.rows.size}</p><table><tr>")
        t.headers.forEach { append("<th>${esc(it)}</th>") }
        append("</tr>")
        t.rows.forEach { r -> append("<tr>"); r.forEach { append("<td>${esc(it)}</td>") }; append("</tr>") }
        append("</table></body></html>")
    }
    val web = WebView(context)
    printWebView = web
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val pm = view.context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(t.title, view.createPrintDocumentAdapter(t.title), PrintAttributes.Builder().build())
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
