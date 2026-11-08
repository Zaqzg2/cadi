@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.stockops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.DashboardTablePage
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
import com.inventorysmartai.app.domain.dashboard.movedQuantityByProduct
import com.inventorysmartai.app.domain.dashboard.operationsByBranch
import com.inventorysmartai.app.domain.dashboard.stockOpsSummary
import com.inventorysmartai.app.domain.dashboard.topEntries
import com.inventorysmartai.app.domain.stockops.StockOperation
import com.inventorysmartai.app.domain.stockops.StockOperationType
import com.inventorysmartai.app.navigation.Destination

/** التحويل والمرتجعات — لوحة معلومات (أنواع العمليات، الأصناف الأكثر حركة، نشاط الفروع) وجدول تفاعلي؛ النقر على الصف يعرض تفاصيله. */
@Composable
fun StockOperationsScreen(navController: NavController, viewModel: StockOperationsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<StockOperation?>(null) }
    val search = rememberTopBarSearch(query = query, onQueryChange = viewModel::onQueryChange, placeholder = "بحث بالصنف أو الفرع أو الرقم")

    Scaffold(
        topBar = { AppTopBar(title = "التحويل والمرتجعات", onBack = { navController.popBackStack() }, search = search) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.StockOperationNew.route) }) {
                Icon(Icons.Filled.Add, contentDescription = "عملية جديدة")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text(
                            if (query.isNotBlank()) "لا توجد نتائج للبحث" else "لا توجد عمليات بعد — اضغط + لتحويل بضاعة بين الفروع أو تسجيل مرتجع",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            ) { operations ->
                val summary = remember(operations) { stockOpsSummary(operations) }
                DashboardTablePage(
                    rows = operations,
                    rowKey = { "${it.type.name}-${it.number}" },
                    columns = operationColumns,
                    filters = operationFilters,
                    onRowClick = { details = it },
                    emptyText = "لا توجد عمليات مطابقة"
                ) {
                    item {
                        KpiGrid(
                            listOf(
                                Kpi("إجمالي العمليات", Formatters.formatInt(summary.total), MaterialTheme.colorScheme.primary),
                                Kpi("الكمية المتحركة", Formatters.formatNumber(summary.totalQuantity), MaterialTheme.colorScheme.secondary),
                                Kpi("تحويلات", Formatters.formatInt(summary.transfers), accent(StockOperationType.TRANSFER)),
                                Kpi("مرتجع بيع", Formatters.formatInt(summary.salesReturns), accent(StockOperationType.SALES_RETURN)),
                                Kpi("مرتجع شراء", Formatters.formatInt(summary.purchaseReturns), accent(StockOperationType.PURCHASE_RETURN))
                            )
                        )
                    }
                    item {
                        DistributionCard(
                            "أنواع العمليات",
                            StockOperationType.entries.map { type ->
                                Segment(type.titleAr, operations.count { it.type == type }.toDouble(), accent(type))
                            }
                        )
                    }
                    item {
                        val color = MaterialTheme.colorScheme.primary
                        RankBarsCard(
                            "الأصناف الأكثر حركة",
                            rankItems(topEntries(movedQuantityByProduct(operations), 6), { color }, { Formatters.formatNumber(it) })
                        )
                    }
                    item {
                        val color = MaterialTheme.colorScheme.secondary
                        RankBarsCard(
                            "الفروع الأكثر نشاطًا (عدد العمليات)",
                            rankItems(topEntries(operationsByBranch(operations), 5), { color }, { Formatters.formatNumber(it) })
                        )
                    }
                }
            }
        }
    }

    details?.let { operation ->
        AlertDialog(
            onDismissRequest = { details = null },
            confirmButton = { TextButton(onClick = { details = null }) { Text("إغلاق") } },
            title = { Text("${operation.type.titleAr} #${operation.number}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(route(operation), style = MaterialTheme.typography.bodyMedium)
                    operation.lines.forEach { line ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(line.productName, modifier = Modifier.weight(1f))
                            Text(Formatters.formatNumber(line.quantity), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    operation.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        )
    }
}

/** «من X إلى Y» للتحويل، واسم الفرع للمرتجعات. */
private fun route(operation: StockOperation): String = when (operation.type) {
    StockOperationType.TRANSFER -> "من ${operation.branchName ?: "—"} إلى ${operation.toBranchName ?: "—"}"
    else -> operation.branchName ?: "—"
}

@Composable
private fun accent(type: StockOperationType): Color = when (type) {
    StockOperationType.TRANSFER -> MaterialTheme.colorScheme.primary
    StockOperationType.SALES_RETURN -> MaterialTheme.colorScheme.secondary
    StockOperationType.PURCHASE_RETURN -> MaterialTheme.colorScheme.tertiary
}

private fun quantity(operation: StockOperation) = operation.lines.sumOf { it.quantity }

private val operationColumns: List<TableColumn<StockOperation>> = listOf(
    TableColumn(
        "النوع", 110.dp,
        text = { it.type.shortAr },
        comparator = compareBy<StockOperation> { it.type.ordinal }
    ),
    TableColumn(
        "الرقم", 70.dp, numeric = true,
        text = { it.number.toString() },
        comparator = compareBy<StockOperation> { it.number }
    ),
    TableColumn(
        "التاريخ", 100.dp,
        text = { Formatters.formatDate(it.createdAt) },
        comparator = compareBy<StockOperation> { it.createdAt }
    ),
    TableColumn(
        "المسار", 190.dp,
        text = { route(it) },
        comparator = compareBy<StockOperation> { it.branchName.orEmpty() }
    ),
    TableColumn(
        "الأصناف", 80.dp, numeric = true,
        text = { Formatters.formatInt(it.lines.size) },
        comparator = compareBy<StockOperation> { it.lines.size }
    ),
    TableColumn(
        "الكمية", 90.dp, numeric = true,
        text = { Formatters.formatNumber(quantity(it)) },
        comparator = compareBy<StockOperation> { quantity(it) }
    )
)

private val operationFilters: List<TableFilter<StockOperation>> =
    StockOperationType.entries.map { type -> TableFilter<StockOperation>(type.titleAr) { it.type == type } }
