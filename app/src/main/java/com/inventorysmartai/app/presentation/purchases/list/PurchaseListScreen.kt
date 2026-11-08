@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.purchases.list

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
import com.inventorysmartai.app.domain.dashboard.isOpenPurchase
import com.inventorysmartai.app.domain.dashboard.purchaseSummary
import com.inventorysmartai.app.domain.dashboard.requestedByProduct
import com.inventorysmartai.app.domain.dashboard.requestedBySupplier
import com.inventorysmartai.app.domain.dashboard.topEntries
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.navigation.Destination

/** المشتريات — لوحة معلومات (المفتوح والمستلم، نسبة الاستلام، أكثر الموردين والأصناف) وجدول تفاعلي لطلبات الشراء. */
@Composable
fun PurchaseListScreen(navController: NavController, viewModel: PurchaseListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<PurchaseRequest?>(null) }

    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); viewModel.onMessageShown() } }
    val search = rememberTopBarSearch(query = query, onQueryChange = viewModel::onQueryChange)

    Scaffold(
        topBar = { AppTopBar(title = "المشتريات", onBack = { navController.popBackStack() }, search = search) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.PurchaseDetail.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "طلب شراء جديد")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text(if (query.isNotBlank()) "لا توجد نتائج للبحث" else "لا توجد طلبات شراء بعد — اضغط + لإنشاء طلب جديد", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) { list ->
                val summary = remember(list) { purchaseSummary(list) }
                DashboardTablePage(
                    rows = list,
                    rowKey = { it.id },
                    columns = purchaseColumns,
                    filters = purchaseFilters,
                    onRowClick = { navController.navigate(Destination.PurchaseDetail.createRoute(it.id)) },
                    onDelete = { item -> if (viewModel.canDelete(item)) pendingDelete = item else viewModel.delete(item) },
                    emptyText = "لا توجد طلبات مطابقة"
                ) {
                    item {
                        val status = LocalInventoryStatusColors.current
                        KpiGrid(
                            listOf(
                                Kpi("طلبات الشراء", Formatters.formatInt(summary.total), MaterialTheme.colorScheme.primary),
                                Kpi("مفتوحة", Formatters.formatInt(summary.open), MaterialTheme.colorScheme.secondary),
                                Kpi("مستلمة", Formatters.formatInt(summary.received), status.available),
                                Kpi("ملغاة / مرفوضة", Formatters.formatInt(summary.closedWithoutReceipt), status.low),
                                Kpi("الكمية المطلوبة", Formatters.formatNumber(summary.requestedQuantity), MaterialTheme.colorScheme.primary),
                                Kpi("الكمية المستلمة", Formatters.formatNumber(summary.receivedQuantity), status.available, "استلام ${Formatters.formatPercent(summary.receivedPercent)}")
                            )
                        )
                    }
                    item {
                        val status = LocalInventoryStatusColors.current
                        val counts = list.groupingBy { it.status }.eachCount()
                        fun seg(label: String, s: PurchaseStatus, color: androidx.compose.ui.graphics.Color) = Segment(label, (counts[s] ?: 0).toDouble(), color)
                        DistributionCard(
                            "حالات الطلبات",
                            listOf(
                                seg("مسودة", PurchaseStatus.DRAFT, status.zero),
                                seg("مُرسل", PurchaseStatus.SUBMITTED, MaterialTheme.colorScheme.secondary),
                                seg("مُعتمد", PurchaseStatus.APPROVED, MaterialTheme.colorScheme.primary),
                                seg("مُعتمد جزئيًا", PurchaseStatus.PARTIALLY_APPROVED, MaterialTheme.colorScheme.tertiary),
                                seg("تم الطلب", PurchaseStatus.ORDERED, status.nearExpiry),
                                seg("مُستلم", PurchaseStatus.RECEIVED, status.available),
                                seg("مرفوض", PurchaseStatus.REJECTED, status.expired),
                                seg("ملغى", PurchaseStatus.CANCELLED, status.low)
                            )
                        )
                    }
                    item {
                        val color = MaterialTheme.colorScheme.primary
                        RankBarsCard(
                            "أكثر الموردين طلبًا (بالكمية)",
                            rankItems(topEntries(requestedBySupplier(list), 5), { color }, { Formatters.formatNumber(it) })
                        )
                    }
                    item {
                        val color = MaterialTheme.colorScheme.secondary
                        RankBarsCard(
                            "أكثر الأصناف طلبًا",
                            rankItems(topEntries(requestedByProduct(list), 5), { color }, { Formatters.formatNumber(it) })
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DestructiveConfirmDialog(
            title = "حذف الطلب؟",
            message = "سيُحذف الطلب وأصنافه نهائيًا. لم يُستلم عليه شيء فلا تأثير على المخزون.",
            onConfirm = { pendingDelete = null; viewModel.delete(item) },
            onDismiss = { pendingDelete = null }
        )
    }
}

private fun statusLabel(status: PurchaseStatus): String = when (status) {
    PurchaseStatus.DRAFT -> "مسودة"
    PurchaseStatus.SUBMITTED -> "مُرسل"
    PurchaseStatus.APPROVED -> "مُعتمد"
    PurchaseStatus.PARTIALLY_APPROVED -> "مُعتمد جزئيًا"
    PurchaseStatus.REJECTED -> "مرفوض"
    PurchaseStatus.ORDERED -> "تم الطلب"
    PurchaseStatus.RECEIVED -> "مُستلم"
    PurchaseStatus.CANCELLED -> "ملغى"
}

private fun requested(r: PurchaseRequest) = r.items.sumOf { it.requestedQuantity }
private fun received(r: PurchaseRequest) = r.items.sumOf { it.receivedQuantity }

private val purchaseColumns: List<TableColumn<PurchaseRequest>> = listOf(
    TableColumn(
        "الرقم", 110.dp,
        text = { it.requestNumber },
        comparator = compareBy<PurchaseRequest> { it.requestNumber }
    ),
    TableColumn(
        "المورد", 140.dp,
        text = { it.supplierName ?: "بدون مورد" },
        comparator = compareBy<PurchaseRequest> { it.supplierName.orEmpty() },
        emphasis = { if (it.supplierName == null) CellEmphasis.MUTED else CellEmphasis.NORMAL }
    ),
    TableColumn(
        "الفرع", 110.dp,
        text = { it.branchName ?: "—" },
        comparator = compareBy<PurchaseRequest> { it.branchName.orEmpty() }
    ),
    TableColumn(
        "التاريخ", 100.dp,
        text = { Formatters.formatDate(it.requestDate) },
        comparator = compareBy<PurchaseRequest> { it.requestDate }
    ),
    TableColumn(
        "الحالة", 110.dp,
        text = { statusLabel(it.status) },
        comparator = compareBy<PurchaseRequest> { it.status.ordinal },
        emphasis = {
            when (it.status) {
                PurchaseStatus.CANCELLED, PurchaseStatus.REJECTED -> CellEmphasis.ERROR
                PurchaseStatus.RECEIVED -> CellEmphasis.NORMAL
                else -> CellEmphasis.WARNING
            }
        }
    ),
    TableColumn(
        "المطلوب", 90.dp, numeric = true,
        text = { Formatters.formatNumber(requested(it)) },
        comparator = compareBy<PurchaseRequest> { requested(it) }
    ),
    TableColumn(
        "المستلم", 90.dp, numeric = true,
        text = { Formatters.formatNumber(received(it)) },
        comparator = compareBy<PurchaseRequest> { received(it) }
    )
)

private val purchaseFilters: List<TableFilter<PurchaseRequest>> = listOf(
    TableFilter("مفتوحة") { isOpenPurchase(it.status) },
    TableFilter("مستلمة") { it.status == PurchaseStatus.RECEIVED },
    TableFilter("ملغاة / مرفوضة") { it.status == PurchaseStatus.CANCELLED || it.status == PurchaseStatus.REJECTED },
    TableFilter("مسودة") { it.status == PurchaseStatus.DRAFT }
)
