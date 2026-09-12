package com.inventorysmartai.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.StatCard
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.theme.LocalInventoryStatusColors
import com.inventorysmartai.app.domain.model.AlertSeverity
import com.inventorysmartai.app.domain.model.DashboardAlert
import com.inventorysmartai.app.domain.model.DashboardSummary
import com.inventorysmartai.app.navigation.Destination

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = viewModel::load) { summary ->
                HomeContent(summary = summary, navController = navController)
            }
        }
    }
}

@Composable
private fun HomeContent(summary: DashboardSummary, navController: NavController) {
    val statusColors = LocalInventoryStatusColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("مرحباً بك", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            AiAssistantEntryCard(onClick = { navController.navigate(Destination.AiAssistant.route) })
        }

        item {
            StatGrid(summary = summary, onNavigateInventory = { navController.navigate(Destination.Inventory.route) })
        }

        item {
            AchievementCard(
                percent = summary.goalsAchievementPercent,
                onClick = { navController.navigate(Destination.Goals.route) }
            )
        }

        if (summary.alerts.isNotEmpty()) {
            item { SectionHeader(title = "التنبيهات") }
            items(summary.alerts) { alert -> AlertRow(alert) }
        }

        item {
            SectionHeader(title = "آخر عمليات الجرد", onSeeAll = { navController.navigate(Destination.CountingList.route) })
        }
        if (summary.recentCounts.isEmpty()) {
            item { EmptyRow("لا توجد عمليات جرد بعد") }
        } else {
            items(summary.recentCounts) { count ->
                RecentRow(
                    title = count.branchName ?: "فرع",
                    subtitle = Formatters.formatDate(count.countDate),
                    trailing = count.status.name,
                    onClick = { navController.navigate(Destination.CountingDetail.createRoute(count.id)) }
                )
            }
        }

        item {
            SectionHeader(title = "آخر طلبات الشراء", onSeeAll = { navController.navigate(Destination.PurchaseList.route) })
        }
        if (summary.recentPurchaseRequests.isEmpty()) {
            item { EmptyRow("لا توجد طلبات شراء بعد") }
        } else {
            items(summary.recentPurchaseRequests) { request ->
                RecentRow(
                    title = request.requestNumber,
                    subtitle = request.supplierName ?: "بدون مورد",
                    trailing = request.status.name,
                    onClick = { navController.navigate(Destination.PurchaseDetail.createRoute(request.id)) }
                )
            }
        }

        item {
            SectionHeader(title = "آخر فواتير البيع", onSeeAll = { navController.navigate(Destination.SalesList.route) })
        }
        if (summary.recentSalesInvoices.isEmpty()) {
            item { EmptyRow("لا توجد فواتير بيع بعد") }
        } else {
            items(summary.recentSalesInvoices) { invoice ->
                RecentRow(
                    title = invoice.invoiceNumber,
                    subtitle = Formatters.formatDate(invoice.invoiceDate),
                    trailing = Formatters.formatCurrency(invoice.total),
                    onClick = { navController.navigate(Destination.SalesDetail.createRoute(invoice.id)) }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatGrid(summary: DashboardSummary, onNavigateInventory: () -> Unit) {
    val statusColors = LocalInventoryStatusColors.current
    val stats = listOf(
        Triple("إجمالي الأصناف", Formatters.formatInt(summary.totalItems), MaterialTheme.colorScheme.primary),
        Triple("إجمالي الكميات", Formatters.formatNumber(summary.totalQuantity), MaterialTheme.colorScheme.primary),
        Triple("منخفض المخزون", Formatters.formatInt(summary.lowStockCount), statusColors.low),
        Triple("مخزون صفر", Formatters.formatInt(summary.zeroStockCount), statusColors.zero),
        Triple("قريب الانتهاء", Formatters.formatInt(summary.nearExpiryCount), statusColors.nearExpiry),
        Triple("منتهي", Formatters.formatInt(summary.expiredCount), statusColors.expired)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(2).forEach { rowStats ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowStats.forEach { (label, value, color) ->
                    StatCard(
                        label = label,
                        value = value,
                        accentColor = color,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateInventory
                    )
                }
                if (rowStats.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AchievementCard(percent: Double, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("نسبة تحقيق الأهداف", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(Formatters.formatPercent(percent), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AiAssistantEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("المساعد الذكي", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("اسأل عن مخزونك — قريباً", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text("عرض الكل") }
        }
    }
}

@Composable
private fun RecentRow(title: String, subtitle: String, trailing: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(trailing, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EmptyRow(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AlertRow(alert: DashboardAlert) {
    val color = when (alert.severity) {
        AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        AlertSeverity.WARNING -> LocalInventoryStatusColors.current.low
        AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
