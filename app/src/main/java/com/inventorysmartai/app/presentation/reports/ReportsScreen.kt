package com.inventorysmartai.app.presentation.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.navigation.Destination

private val reportTiles = listOf(
    ReportType.INVENTORY to "تقرير المخزون",
    ReportType.LOW_STOCK to "منخفض المخزون",
    ReportType.ZERO_STOCK to "مخزون صفر",
    ReportType.EXPIRED to "منتهي الصلاحية",
    ReportType.NEAR_EXPIRY to "قريب الانتهاء",
    ReportType.COUNTING to "تقرير الجرد",
    ReportType.PURCHASES to "تقرير المشتريات",
    ReportType.SALES to "تقرير المبيعات",
    ReportType.GOALS to "تقرير الأهداف",
    ReportType.BRANCHES to "تقرير الفروع"
)

@Composable
fun ReportsScreen(navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("التقارير") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(reportTiles) { (type, label) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navController.navigate(Destination.ReportDetail.createRoute(type.name)) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
