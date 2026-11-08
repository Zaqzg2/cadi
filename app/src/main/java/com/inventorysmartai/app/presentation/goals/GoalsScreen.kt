@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.goals

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
import com.inventorysmartai.app.domain.dashboard.goalSummary
import com.inventorysmartai.app.domain.dashboard.topEntries
import com.inventorysmartai.app.navigation.Destination

/**
 * الأهداف والعمولات — لوحة معلومات (مؤشرات وتوزيع وترتيب) وجدول تفاعلي (فرز، فلاتر، نقر لفتح الهدف، زر حذف بتأكيد).
 * البحث من أيقونة الشريط العلوي يضيّق اللوحة والجدول معًا، وزر + يضيف هدفًا جديدًا.
 */
@Composable
fun GoalsScreen(navController: NavController, viewModel: GoalsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<GoalListItem?>(null) }

    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); viewModel.onMessageShown() } }
    val search = rememberTopBarSearch(query = query, onQueryChange = viewModel::onQueryChange, placeholder = "بحث باسم الصنف")
    val openGoal: (GoalListItem) -> Unit = { navController.navigate(Destination.GoalDetail.createRoute(it.goal.id)) }

    Scaffold(
        topBar = { AppTopBar(title = "الأهداف والعمولات", search = search) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Destination.GoalDetail.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "هدف جديد")
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
                            if (query.isNotBlank()) "لا توجد نتائج للبحث" else "لا توجد أهداف بعد — اضغط + لتحديد أول هدف",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            ) { list ->
                val summary = remember(list) { goalSummary(list.map { it.achievement }) }
                DashboardTablePage(
                    rows = list,
                    rowKey = { it.goal.id },
                    columns = goalColumns,
                    filters = goalFilters,
                    onRowClick = openGoal,
                    onDelete = { pendingDelete = it },
                    emptyText = "لا توجد أهداف مطابقة"
                ) {
                    item {
                        val status = LocalInventoryStatusColors.current
                        KpiGrid(
                            listOf(
                                Kpi("عدد الأهداف", Formatters.formatInt(summary.count), MaterialTheme.colorScheme.primary),
                                Kpi("الإنجاز الكلي", Formatters.formatPercent(summary.overallPercent), MaterialTheme.colorScheme.secondary),
                                Kpi("إجمالي الهدف", Formatters.formatNumber(summary.totalTarget), MaterialTheme.colorScheme.primary),
                                Kpi("المحقق", Formatters.formatNumber(summary.achieved), status.available),
                                Kpi("المتبقي", Formatters.formatNumber(summary.remaining), MaterialTheme.colorScheme.tertiary),
                                Kpi("أهداف مكتملة", "${Formatters.formatInt(summary.completed)} / ${Formatters.formatInt(summary.count)}", status.available)
                            )
                        )
                    }
                    item {
                        val status = LocalInventoryStatusColors.current
                        DistributionCard(
                            "حالة الأهداف",
                            listOf(
                                Segment("مكتمل", summary.completed.toDouble(), status.available),
                                Segment("قيد التنفيذ", summary.inProgress.toDouble(), status.nearExpiry),
                                Segment("لم يبدأ", summary.notStarted.toDouble(), status.zero)
                            )
                        )
                    }
                    item {
                        val done = LocalInventoryStatusColors.current.available
                        val primary = MaterialTheme.colorScheme.primary
                        val byPercent = list.associate { goalLabel(it) to it.achievement.achievementPercent.coerceAtMost(100.0) }
                        RankBarsCard(
                            "الأعلى إنجازًا",
                            rankItems(topEntries(byPercent, 6), { if (it >= 100.0) done else primary }, { Formatters.formatPercent(it) })
                        )
                    }
                    item {
                        val color = MaterialTheme.colorScheme.tertiary
                        val remaining = list.associate { goalLabel(it) to it.achievement.remainingQuantity }
                        RankBarsCard(
                            "الأكثر متبقيًا للتحقيق",
                            rankItems(topEntries(remaining, 5), { color }, { Formatters.formatNumber(it) }),
                            emptyText = "لا يوجد متبقٍ — كل الأهداف محققة"
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DestructiveConfirmDialog(
            title = "حذف الهدف؟",
            message = "سيُحذف هدف «${item.goal.productName ?: "الصنف"}» ومجموعاته نهائيًا. لا تأثير على المخزون ولا على المبيعات.",
            onConfirm = { pendingDelete = null; viewModel.delete(item) },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** اسم الصنف مع الشهر: قد يكون للصنف الواحد هدف في أكثر من شهر. */
private fun goalLabel(item: GoalListItem): String =
    "${item.goal.productName ?: "صنف"} • ${Formatters.formatDate(item.goal.periodStart).take(7)}"

private val goalColumns: List<TableColumn<GoalListItem>> = listOf(
    TableColumn(
        "الصنف", 160.dp,
        text = { it.goal.productName ?: "صنف" },
        comparator = compareBy<GoalListItem> { it.goal.productName.orEmpty() }
    ),
    TableColumn(
        "الشهر", 90.dp,
        text = { Formatters.formatDate(it.goal.periodStart).take(7) },
        comparator = compareBy<GoalListItem> { it.goal.periodStart }
    ),
    TableColumn(
        "الهدف", 90.dp, numeric = true,
        text = { Formatters.formatNumber(it.achievement.totalTarget) },
        comparator = compareBy<GoalListItem> { it.achievement.totalTarget }
    ),
    TableColumn(
        "المحقق", 90.dp, numeric = true,
        text = { Formatters.formatNumber(it.achievement.achievedQuantity) },
        comparator = compareBy<GoalListItem> { it.achievement.achievedQuantity }
    ),
    TableColumn(
        "المتبقي", 90.dp, numeric = true,
        text = { Formatters.formatNumber(it.achievement.remainingQuantity) },
        comparator = compareBy<GoalListItem> { it.achievement.remainingQuantity }
    ),
    TableColumn(
        "الإنجاز", 90.dp, numeric = true,
        text = { Formatters.formatPercent(it.achievement.achievementPercent) },
        comparator = compareBy<GoalListItem> { it.achievement.achievementPercent },
        emphasis = {
            when {
                it.achievement.achievementPercent >= 100.0 -> CellEmphasis.NORMAL
                it.achievement.achievedQuantity <= 0.0 -> CellEmphasis.MUTED
                else -> CellEmphasis.WARNING
            }
        }
    )
)

private val goalFilters: List<TableFilter<GoalListItem>> = listOf(
    TableFilter("مكتمل") { it.achievement.achievementPercent >= 100.0 },
    TableFilter("قيد التنفيذ") { it.achievement.achievementPercent < 100.0 && it.achievement.achievedQuantity > 0.0 },
    TableFilter("لم يبدأ") { it.achievement.achievementPercent < 100.0 && it.achievement.achievedQuantity <= 0.0 }
)
