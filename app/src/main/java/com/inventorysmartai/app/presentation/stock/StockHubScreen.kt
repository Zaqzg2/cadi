package com.inventorysmartai.app.presentation.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Constants
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.theme.LocalInventoryStatusColors
import com.inventorysmartai.app.domain.repository.CountingRepository
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import com.inventorysmartai.app.domain.usecase.GetDashboardSummaryUseCase
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StockHubState(
    val items: Int = 0, val low: Int = 0, val nearExpiry: Int = 0, val expired: Int = 0,
    val counts: Int = 0, val requests: Int = 0
)

@HiltViewModel
class StockHubViewModel @Inject constructor(
    getDashboardSummary: GetDashboardSummaryUseCase,
    countingRepository: CountingRepository,
    purchaseRepository: PurchaseRepository
) : ViewModel() {
    val state: StateFlow<StockHubState> = combine(
        getDashboardSummary(Constants.RECENT_LIST_LIMIT),
        countingRepository.observeCounts(),
        purchaseRepository.observeRequests()
    ) { d, counts, requests ->
        StockHubState(d.totalItems, d.lowStockCount, d.nearExpiryCount, d.expiredCount, counts.size, requests.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StockHubState())
}

/** صفحة واحدة بثلاث بطاقات: المخزون · الجرد · الشراء — الضغط على البطاقة يفتح القسم، والزر ينشئ جديدًا مباشرة. */
@Composable
fun StockHubScreen(navController: NavController, viewModel: StockHubViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val status = LocalInventoryStatusColors.current
    val scheme = MaterialTheme.colorScheme

    Scaffold(topBar = { AppTopBar(title = "المخزون والجرد والشراء") }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HubCard(
                title = "المخزون", icon = Icons.Filled.Inventory2, accent = scheme.primary,
                stats = listOf(
                    Stat("الأصناف", s.items, scheme.primary), Stat("منخفض", s.low, status.low),
                    Stat("قريب الانتهاء", s.nearExpiry, status.nearExpiry), Stat("منتهي", s.expired, status.expired)
                ),
                actionLabel = "إضافة صنف",
                onOpen = { navController.navigate(Destination.Inventory.route) },
                onAction = { navController.navigate(Destination.ManualEntry.createRoute()) }
            )
            HubCard(
                title = "الجرد", icon = Icons.Filled.FactCheck, accent = scheme.tertiary,
                stats = listOf(Stat("عمليات الجرد", s.counts, scheme.tertiary)),
                actionLabel = "جرد جديد",
                onOpen = { navController.navigate(Destination.CountingList.route) },
                onAction = { navController.navigate(Destination.CountingDetail.createRoute()) }
            )
            HubCard(
                title = "الشراء", icon = Icons.Filled.ShoppingCart, accent = scheme.secondary,
                stats = listOf(Stat("طلبات الشراء", s.requests, scheme.secondary)),
                actionLabel = "طلب شراء جديد",
                onOpen = { navController.navigate(Destination.PurchaseList.route) },
                onAction = { navController.navigate(Destination.PurchaseDetail.createRoute()) }
            )
        }
    }
}

private data class Stat(val label: String, val value: Int, val color: Color)

@Composable
private fun HubCard(
    title: String, icon: ImageVector, accent: Color, stats: List<Stat>,
    actionLabel: String, onOpen: () -> Unit, onAction: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = accent) }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                stats.forEach { st ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Formatters.formatInt(st.value), style = MaterialTheme.typography.headlineSmall, color = st.color)
                        Text(st.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FilledTonalButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}
