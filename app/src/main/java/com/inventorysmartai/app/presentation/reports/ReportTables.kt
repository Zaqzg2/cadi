package com.inventorysmartai.app.presentation.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.data.assistant.files.FileNames
import com.inventorysmartai.app.data.assistant.files.XlsxWriter
import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.domain.reports.ReportCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal val reportTiles = listOf(
    ReportType.INVENTORY to "المخزون",
    ReportType.LOW_STOCK to "منخفض",
    ReportType.ZERO_STOCK to "صفر",
    ReportType.NEAR_EXPIRY to "قريب الانتهاء",
    ReportType.EXPIRED to "منتهي",
    ReportType.BRANCHES to "الفروع",
    ReportType.SALES to "المبيعات",
    ReportType.PURCHASES to "المشتريات",
    ReportType.COUNTING to "الجرد",
    ReportType.GOALS to "الأهداف"
)

/**
 * [rows] تحمل القيم الحقيقية (أرقام وتواريخ نصية): للتصدير إلى Excel/CSV حيث يجب أن تبقى الأرقام أرقامًا قابلة للجمع.
 * [displayRows] هي نفسها منسّقة للشاشة والطباعة.
 */
internal class ReportTable(val title: String, val headers: List<String>, val rows: List<List<Any>>) {
    val displayRows: List<List<String>> by lazy { rows.map { row -> row.map(::cellText) } }
}

private fun cellText(value: Any): String = when (value) {
    is Double -> Formatters.formatNumber(value)
    is Int -> Formatters.formatInt(value)
    else -> value.toString()
}

internal enum class ExportFormat(val extension: String, val mime: String) {
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", "text/csv")
}

private fun statusLabel(s: InventoryStatus) = when (s) {
    InventoryStatus.AVAILABLE -> "متوفر"
    InventoryStatus.LOW -> "منخفض"
    InventoryStatus.ZERO -> "صفر"
    InventoryStatus.NEAR_EXPIRY -> "قريب الانتهاء"
    InventoryStatus.EXPIRED -> "منتهي"
}

private fun countStatusLabel(s: CountStatus) = when (s) {
    CountStatus.DRAFT -> "مسودة"
    CountStatus.IN_PROGRESS -> "جارٍ"
    CountStatus.COMPLETED -> "مكتمل"
    CountStatus.CANCELLED -> "ملغى"
}

/** جدول التقرير [type] من بيانات [d]؛ null إن لم يكن للنوع جدول. */
internal fun buildReportTable(type: ReportType, d: ReportsData): ReportTable? {
    val title = reportTiles.firstOrNull { it.first == type }?.second ?: return null
    fun products(filter: (ProductStockSummary) -> Boolean) = ReportTable(
        title, listOf("#", "الصنف", "الكمية", "الحد الأدنى", "الحالة", "الانتهاء"),
        d.products.filter(filter).mapIndexed { i, p ->
            listOf<Any>(
                i + 1, p.product.name, p.totalQuantity, p.product.minStock,
                statusLabel(p.status), p.nearestExpiryDate?.let { Formatters.formatDate(it) } ?: "—"
            )
        }
    )
    fun days(series: List<Pair<Long, Double>>) = ReportTable(
        title, listOf("اليوم", "الإجمالي"),
        series.map { (day, total) ->
            listOf<Any>(Formatters.formatDate(if (day > 100_000_000_000L) day else day * 86_400_000L), total)
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
                listOf<Any>(name, list.size, list.sumOf { it.quantity })
            }
        )
        ReportType.COUNTING -> ReportTable(
            title, listOf("#", "التاريخ", "الفرع", "الحالة", "الأصناف", "بها فرق"),
            d.counts.mapIndexed { i, c ->
                listOf<Any>(
                    i + 1, Formatters.formatDate(c.countDate), c.branchName ?: "—", countStatusLabel(c.status),
                    c.items.size, c.items.count { it.difference != 0.0 }
                )
            }
        )
        ReportType.GOALS -> ReportTable(
            title, listOf("الصنف", "الهدف الإجمالي", "المجموعات", "الفترة"),
            d.goals.map { g ->
                listOf<Any>(
                    g.productName ?: "—", g.groups.sumOf { it.targetQuantity }, g.groups.size,
                    "${Formatters.formatDate(g.periodStart)} – ${Formatters.formatDate(g.periodEnd)}"
                )
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// عرض الجدول على الشاشة
// ---------------------------------------------------------------------------------------------------------------------

private fun weightFor(i: Int, n: Int): Float = if (n >= 6) (if (i == 1) 2.4f else if (i == 0) 0.6f else 1f) else (if (i == 0) 2f else 1f)

@Composable
internal fun ReportTableHeader(headers: List<String>) {
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
internal fun ReportTableRow(cells: List<String>, index: Int) {
    val bg = if (index % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow
    Row(modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 10.dp, vertical = 8.dp)) {
        cells.forEachIndexed { i, cell -> Text(cell, modifier = Modifier.weight(weightFor(i, cells.size)), style = MaterialTheme.typography.bodySmall) }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// التحميل والمشاركة والطباعة
// ---------------------------------------------------------------------------------------------------------------------

/** محتوى الملف: Excel بأرقام حقيقية أو CSV. */
private fun reportBytes(table: ReportTable, format: ExportFormat): ByteArray = when (format) {
    ExportFormat.XLSX -> {
        val sheet = XlsxWriter.SheetSpec(FileNames.safe(table.title, "تقرير", ""), table.headers, table.rows)
        XlsxWriter.write(listOf(sheet), true)
    }
    ExportFormat.CSV -> ReportCsv.build(table.headers, table.rows).toByteArray(Charsets.UTF_8)
}

/**
 * أزرار تصدير التقرير، ظاهرة بأسمائها (لا أيقونة وحيدة مخفية):
 * «تحميل» يحفظ ملفًا في المكان الذي يختاره المستخدم (التنزيلات مثلًا)، «مشاركة» تفتح قائمة المشاركة (واتساب، بريد، Drive...)،
 * و«طباعة / PDF» تفتح الطباعة التي تتيح أيضًا الحفظ كملف PDF.
 */
@Composable
internal fun ReportExportBar(table: ReportTable?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadMenu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ReportTable?>(null) }

    val onPicked: (Uri?, ExportFormat) -> Unit = { uri, format ->
        val toSave = pending
        pending = null
        if (uri != null && toSave != null) scope.launch { saveReportTable(context, toSave, format, uri) }
    }
    val xlsxSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.XLSX.mime)) { onPicked(it, ExportFormat.XLSX) }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)) { onPicked(it, ExportFormat.CSV) }

    val enabled = table != null
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            FilledTonalButton(onClick = { downloadMenu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text(" تحميل")
            }
            DropdownMenu(expanded = downloadMenu, onDismissRequest = { downloadMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Excel (.xlsx)") },
                    onClick = {
                        downloadMenu = false
                        table?.let { pending = it; xlsxSaver.launch(FileNames.safe(it.title, "تقرير", ExportFormat.XLSX.extension)) }
                    }
                )
                DropdownMenuItem(
                    text = { Text("CSV") },
                    onClick = {
                        downloadMenu = false
                        table?.let { pending = it; csvSaver.launch(FileNames.safe(it.title, "تقرير", ExportFormat.CSV.extension)) }
                    }
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            FilledTonalButton(onClick = { shareMenu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(" مشاركة")
            }
            DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Excel (.xlsx)") },
                    onClick = { shareMenu = false; table?.let { shareReportTable(context, it, ExportFormat.XLSX) } }
                )
                DropdownMenuItem(
                    text = { Text("CSV — لبرامج أخرى") },
                    onClick = { shareMenu = false; table?.let { shareReportTable(context, it, ExportFormat.CSV) } }
                )
            }
        }
        OutlinedButton(onClick = { table?.let { printReportTable(context, it) } }, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Print, contentDescription = null)
            Text(" PDF")
        }
    }
}

/** يكتب الجدول في الملف الذي أنشأه المستخدم من منتقي «حفظ باسم» (لا يحتاج أي صلاحية تخزين). */
private suspend fun saveReportTable(context: Context, table: ReportTable, format: ExportFormat, uri: Uri) {
    val result = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = reportBytes(table, format)
            val stream = context.contentResolver.openOutputStream(uri) ?: error("تعذّر فتح الملف للكتابة")
            stream.use { it.write(bytes) }
        }
    }
    val message = result.fold(
        onSuccess = { "تم حفظ الملف" },
        onFailure = { "تعذّر حفظ الملف: ${it.message ?: "خطأ غير معروف"}" }
    )
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

// يُحتفظ بالمرجع حتى ينتهي إنشاء مستند الطباعة (وإلا قد يجمعه Garbage Collector قبل الطباعة).
private var printWebView: WebView? = null

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

internal fun printReportTable(context: Context, t: ReportTable) {
    val html = buildString {
        append("<html dir='rtl' lang='ar'><head><meta charset='utf-8'><style>")
        append("body{font-family:sans-serif;padding:16px}h2{margin:0 0 4px}p{color:#666;margin:0 0 12px;font-size:12px}")
        append("table{width:100%;border-collapse:collapse;font-size:13px}th,td{border:1px solid #bbb;padding:6px 8px;text-align:right}")
        append("th{background:#e3f1ec}tr:nth-child(even){background:#f6f6f6}</style></head><body>")
        append("<h2>${esc(t.title)}</h2><p>${Formatters.formatDate(System.currentTimeMillis())} — ${t.rows.size}</p><table><tr>")
        t.headers.forEach { append("<th>${esc(it)}</th>") }
        append("</tr>")
        t.displayRows.forEach { r -> append("<tr>"); r.forEach { append("<td>${esc(it)}</td>") }; append("</tr>") }
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

/**
 * يصدّر الجدول المعروض إلى ملف (Excel بأرقام حقيقية أو CSV) في ذاكرة التخزين المؤقت ويفتح قائمة المشاركة
 * (Drive، واتساب، بريد، حفظ...). يُحذف ما صُدِّر سابقًا أولًا كي لا يتراكم في التخزين.
 */
internal fun shareReportTable(context: Context, table: ReportTable, format: ExportFormat) {
    try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, FileNames.safe(table.title, "تقرير", format.extension))
        file.writeBytes(reportBytes(table, format))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "مشاركة التقرير"))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذّر تصدير التقرير: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
    }
}
