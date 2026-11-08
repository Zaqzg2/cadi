@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.counting.list

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
import com.inventorysmartai.app.core.designsystem.component.rankItems
import com.inventorysmartai.app.core.designsystem.component.rememberTopBarSearch
import com.inventorysmartai.app.core.designsystem.theme.LocalInventoryStatusColors
import com.inventorysmartai.app.domain.dashboard.countingDifferencesByProduct
import com.inventorysmartai.app.domain.dashboard.countingSummary
import com.inventorysmartai.app.domain.dashboard.topEntries
import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.domain.model.InventoryCount
import com.inventorysmartai.app.navigation.Destination

/** الجرد — لوحة معلومات (حالات، فروقات، نشاط الفروع) وجدول تفاعلي يفتح الجرد بالنقر ويحذف المسودات بتأكيد. */
@Composable
fun CountingListScreen(navController: NavController, viewModel: CountingListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<InventoryCount?>(null) }

    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); viewModel.onMessageShown() } }
    val search = rememberTopBarSearch(query = query, onQueryChange = viewModel::onQueryChange)

    Scaffold(
        topBar = { AppTopBar(title = "الجرد", onBack = { navController.popBackStack() }, search = search) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.CountingDetail.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "جرد جديد")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text(if (query.isNotBlank()) "لا توجد نتائج للبحث" else "لا توجد عمليات جرد بعد — اضغط + لبدء أول جرد", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) { list ->
                val summary = remember(list) { countingSummary(list) }
                DashboardTablePage(
                    rows = list,
                    rowKey = { it.id },
                    columns = countingColumns,
                    filters = countingFilters,
                    onRowClick = { navController.navigate(Destination.CountingDetail.createRoute(it.id)) },
                    // سحب/حذف المسودات فقط: غير المسموح يشرح الـ ViewModel سببه برسالة.
                    onDelete = { item -> if (viewModel.canDelete(item)) pendingDelete = item else viewModel.delete(item) },
                    emptyText = "لا توجد عمليات جرد مطابقة"
                ) {
                    item {
                        val status = LocalInventoryStatusColors.current
                        KpiGrid(
                            listOf(
                                Kpi("عمليات الجرد", Formatters.formatInt(summary.total), MaterialTheme.colorScheme.primary),
                                Kpi("مكتملة", Formatters.formatInt(summary.completed), status.available),
                                Kpi("مسودة / جارية", Formatters.formatInt(summary.open), MaterialTheme.colorScheme.secondary),
                                Kpi("أصناف بها فرق", Formatters.formatInt(summary.itemsWithDifference), MaterialTheme.colorScheme.tertiary, "في الجرد المكتمل"),
                                Kpi("إجمالي النقص", Formatters.formatNumber(summary.shortage), status.low),
                                Kpi("إجمالي الزيادة", Formatters.formatNumber(summary.overage), status.available)
                            )
                        )
                    }
                    item {
                        val status = LocalInventoryStatusColors.current
                        DistributionCard(
                            "حالة عمليات الجرد",
                            listOf(
                                Segment("مكتمل", summary.completed.toDouble(), status.available),
                                Segment("مسودة / جارٍ", summary.open.toDouble(), MaterialTheme.colorScheme.secondary),
                                Segment("ملغى", summary.cancelled.toDouble(), status.zero)
                            )
                        )
                    }
                    item {
                        val color = MaterialTheme.colorScheme.primary
                        val perBranch = list.groupBy { it.branchName ?: "فرع" }.mapValues { (_, v) -> v.size.toDouble() }
                        RankBarsCard(
                            "الجرد حسب الفرع",
                            rankItems(topEntries(perBranch, 6), { color }, { Formatters.formatNumber(it) })
                        )
                    }
                    item {
                        val status = LocalInventoryStatusColors.current
                        val diffs = countingDifferencesByProduct(list)
                        RankBarsCard(
                            "أكبر الفروقات (الفعلي − النظام)",
                            rankItems(
                                topEntries(diffs, 5, byAbsolute = true),
                                { if (it < 0) status.low else status.available },
                                { (if (it > 0) "+" else "") + Formatters.formatNumber(it) }
                            ),
                            emptyText = "لا فروقات في الجرد المكتمل"
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DestructiveConfirmDialog(
            title = "حذف مسودة الجرد؟",
            message = "ستُحذف المسودة وأصنافها نهائيًا. لا تأثير على المخزون.",
            onConfirm = { pendingDelete = null; viewModel.delete(item) },
            onDismiss = { pendingDelete = null }
        )
    }
}

private fun statusLabel(status: CountStatus): String = when (status) {
    CountStatus.DRAFT -> "مسودة"
    CountStatus.IN_PROGRESS -> "جارٍ"
    CountStatus.COMPLETED -> "مكتمل"
    CountStatus.CANCELLED -> "ملغى"
}

private fun diffCount(c: InventoryCount) = c.items.count { it.difference != 0.0 }
private fun netDiff(c: InventoryCount) = c.items.sumOf { it.difference }

private val countingColumns: List<TableColumn<InventoryCount>> = listOf(
    TableColumn(
        "التاريخ", 100.dp,
        text = { Formatters.formatDate(it.countDate) },
        comparator = compareBy<InventoryCount> { it.countDate }
    ),
    TableColumn(
        "الفرع", 130.dp,
        text = { it.branchName ?: "—" },
        comparator = compareBy<InventoryCount> { it.branchName.orEmpty() }
    ),
    TableColumn(
        "الحالة", 90.dp,
        text = { statusLabel(it.status) },
        comparator = compareBy<InventoryCount> { it.status.ordinal },
        emphasis = {
            when (it.status) {
                CountStatus.CANCELLED -> CellEmphasis.MUTED
                CountStatus.COMPLETED -> CellEmphasis.NORMAL
                else -> CellEmphasis.WARNING
            }
        }
    ),
    TableColumn(
        "الأصناف", 80.dp, numeric = true,
        text = { Formatters.formatInt(it.items.size) },
        comparator = compareBy<InventoryCount> { it.items.size }
    ),
    TableColumn(
        "بها فرق", 80.dp, numeric = true,
        text = { Formatters.formatInt(diffCount(it)) },
        comparator = compareBy<InventoryCount> { diffCount(it) },
        emphasis = { if (diffCount(it) > 0) CellEmphasis.WARNING else CellEmphasis.MUTED }
    ),
    TableColumn(
        "صافي الفرق", 100.dp, numeric = true,
        text = { Formatters.formatNumber(netDiff(it)) },
        comparator = compareBy<InventoryCount> { netDiff(it) },
        emphasis = { if (netDiff(it) < 0.0) CellEmphasis.ERROR else CellEmphasis.NORMAL }
    )
)

private val countingFilters: List<TableFilter<InventoryCount>> = listOf(
    TableFilter("مسودة / جارٍ") { it.status == CountStatus.DRAFT || it.status == CountStatus.IN_PROGRESS },
    TableFilter("مكتمل") { it.status == CountStatus.COMPLETED },
    TableFilter("ملغى") { it.status == CountStatus.CANCELLED }
)
