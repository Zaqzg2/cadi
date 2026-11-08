@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.sales.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.CellEmphasis
import com.inventorysmartai.app.core.designsystem.component.DashboardTablePage
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.DistributionCard
import com.inventorysmartai.app.core.designsystem.component.Kpi
import com.inventorysmartai.app.core.designsystem.component.KpiGrid
import com.inventorysmartai.app.core.designsystem.component.RankBarsCard
import com.inventorysmartai.app.core.designsystem.component.Segment
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.TableColumn
import com.inventorysmartai.app.core.designsystem.component.TableFilter
import com.inventorysmartai.app.core.designsystem.component.TrendBarsCard
import com.inventorysmartai.app.core.designsystem.component.rankItems
import com.inventorysmartai.app.core.designsystem.component.rememberTopBarSearch
import com.inventorysmartai.app.core.designsystem.theme.LocalInventoryStatusColors
import com.inventorysmartai.app.domain.dashboard.dailySalesTotals
import com.inventorysmartai.app.domain.dashboard.salesByCustomer
import com.inventorysmartai.app.domain.dashboard.salesSummary
import com.inventorysmartai.app.domain.dashboard.soldQuantityByProduct
import com.inventorysmartai.app.domain.dashboard.topEntries
import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.navigation.Destination

private const val TREND_DAYS = 14

/** المبيعات — لوحة معلومات (الإيراد، متوسط الفاتورة، اتجاه آخر 14 يومًا، أفضل العملاء والأصناف) وجدول تفاعلي للفواتير. */
@Composable
fun SalesListScreen(navController: NavController, viewModel: SalesListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<SalesInvoice?>(null) }

    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); viewModel.onMessageShown() } }
    val search = rememberTopBarSearch(query = query, onQueryChange = viewModel::onQueryChange)

    Scaffold(
        topBar = { AppTopBar(title = "المبيعات", onBack = { navController.popBackStack() }, search = search) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.SalesDetail.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "فاتورة بيع جديدة")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text(if (query.isNotBlank()) "لا توجد نتائج للبحث" else "لا توجد فواتير بيع بعد — اضغط + لإنشاء فاتورة جديدة", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) { list ->
                val summary = remember(list) { salesSummary(list) }
                val trend = remember(list) { dailySalesTotals(list, TREND_DAYS, System.currentTimeMillis()) }
                DashboardTablePage(
                    rows = list,
                    rowKey = { it.id },
                    columns = salesColumns,
                    filters = salesFilters,
                    onRowClick = { navController.navigate(Destination.SalesDetail.createRoute(it.id)) },
                    onDelete = { item -> if (viewModel.canDelete(item)) pendingDelete = item else viewModel.delete(item) },
                    emptyText = "لا توجد فواتير مطابقة"
                ) {
                    item {
                        val status = LocalInventoryStatusColors.current
                        KpiGrid(
                            listOf(
                                Kpi("إجمالي المبيعات", Formatters.formatCurrency(summary.revenue), MaterialTheme.colorScheme.primary, "الفواتير المؤكدة فقط"),
                                Kpi("متوسط الفاتورة", Formatters.formatCurrency(summary.averageInvoice), MaterialTheme.colorScheme.secondary),
                                Kpi("فواتير مؤكدة", Formatters.formatInt(summary.confirmed), status.available),
                                Kpi("العملاء", Formatters.formatInt(summary.customers), MaterialTheme.colorScheme.primary),
                                Kpi("مسودات", Formatters.formatInt(summary.drafts), MaterialTheme.colorScheme.tertiary),
                                Kpi("ملغاة", Formatters.formatInt(summary.cancelled), status.low)
                            )
                        )
                    }
                    item {
                        TrendBarsCard(
                            title = "المبيعات — آخر $TREND_DAYS يومًا",
                            points = trend.map { (day, total) -> Formatters.formatDate(day).takeLast(5) to total },
                            color = MaterialTheme.colorScheme.primary,
                            totalText = Formatters.formatCurrency(trend.sumOf { it.second })
                        )
                    }
                    item {
                        val status = LocalInventoryStatusColors.current
                        DistributionCard(
                            "حالة الفواتير",
                            listOf(
                                Segment("مؤكدة", summary.confirmed.toDouble(), status.available),
                                Segment("مسودة", summary.drafts.toDouble(), MaterialTheme.colorScheme.tertiary),
                                Segment("ملغاة", summary.cancelled.toDouble(), status.low)
                            )
                        )
                    }
                    item {
                        val color = MaterialTheme.colorScheme.primary
                        RankBarsCard(
                            "أفضل العملاء",
                            rankItems(topEntries(salesByCustomer(list), 5), { color }, { Formatters.formatCurrency(it) })
                        )
                    }
                    item {
                        val color = MaterialTheme.colorScheme.secondary
                        RankBarsCard(
                            "الأكثر مبيعًا (بالكمية)",
                            rankItems(topEntries(soldQuantityByProduct(list), 5), { color }, { Formatters.formatNumber(it) })
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DestructiveConfirmDialog(
            title = "حذف المسودة؟",
            message = "ستُحذف المسودة وأصنافها نهائيًا. لا تأثير على المخزون.",
            onConfirm = { pendingDelete = null; viewModel.delete(item) },
            onDismiss = { pendingDelete = null }
        )
    }
}

private fun statusLabel(status: InvoiceStatus) = when (status) {
    InvoiceStatus.DRAFT -> "مسودة"
    InvoiceStatus.CONFIRMED -> "مؤكدة"
    InvoiceStatus.CANCELLED -> "ملغاة"
}

private val salesColumns: List<TableColumn<SalesInvoice>> = listOf(
    TableColumn(
        "الرقم", 110.dp,
        text = { it.invoiceNumber },
        comparator = compareBy<SalesInvoice> { it.invoiceNumber }
    ),
    TableColumn(
        "التاريخ", 100.dp,
        text = { Formatters.formatDate(it.invoiceDate) },
        comparator = compareBy<SalesInvoice> { it.invoiceDate }
    ),
    TableColumn(
        "العميل", 140.dp,
        text = { it.customerName ?: "عميل نقدي" },
        comparator = compareBy<SalesInvoice> { it.customerName.orEmpty() },
        emphasis = { if (it.customerName == null) CellEmphasis.MUTED else CellEmphasis.NORMAL }
    ),
    TableColumn(
        "الفرع", 100.dp,
        text = { it.branchName ?: "—" },
        comparator = compareBy<SalesInvoice> { it.branchName.orEmpty() }
    ),
    TableColumn(
        "الحالة", 90.dp,
        text = { statusLabel(it.status) },
        comparator = compareBy<SalesInvoice> { it.status.ordinal },
        emphasis = {
            when (it.status) {
                InvoiceStatus.CONFIRMED -> CellEmphasis.NORMAL
                InvoiceStatus.CANCELLED -> CellEmphasis.ERROR
                InvoiceStatus.DRAFT -> CellEmphasis.WARNING
            }
        }
    ),
    TableColumn(
        "الأصناف", 80.dp, numeric = true,
        text = { Formatters.formatInt(it.items.size) },
        comparator = compareBy<SalesInvoice> { it.items.size }
    ),
    TableColumn(
        "الإجمالي", 120.dp, numeric = true,
        text = { Formatters.formatCurrency(it.total) },
        comparator = compareBy<SalesInvoice> { it.total }
    )
)

private val salesFilters: List<TableFilter<SalesInvoice>> = listOf(
    TableFilter("مؤكدة") { it.status == InvoiceStatus.CONFIRMED },
    TableFilter("مسودة") { it.status == InvoiceStatus.DRAFT },
    TableFilter("ملغاة") { it.status == InvoiceStatus.CANCELLED }
)
