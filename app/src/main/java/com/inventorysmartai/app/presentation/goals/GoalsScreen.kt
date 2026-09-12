package com.inventorysmartai.app.presentation.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.StateContent

@Composable
fun GoalsScreen(navController: NavController, viewModel: GoalsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("الأهداف والعمولات") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(
                state = uiState,
                onRetry = {},
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text("أضف صنفاً من المخزون أولاً لتتمكن من تحديد هدف له", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) { data ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text("الصنف", style = MaterialTheme.typography.titleSmall)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(data.products) { product ->
                                FilterChip(
                                    selected = data.selectedProductId == product.id,
                                    onClick = { viewModel.onProductSelected(product.id) },
                                    label = { Text(product.name) }
                                )
                            }
                        }
                    }

                    items(data.groups, key = { it.localId }) { row ->
                        GoalGroupCard(
                            row = row,
                            onTargetChange = { viewModel.onTargetChange(row.localId, it) },
                            onCommissionValueChange = { viewModel.onCommissionValueChange(row.localId, it) },
                            onCommissionTypeChange = { viewModel.onCommissionTypeChange(row.localId, it) },
                            onRemove = { viewModel.onRemoveGroup(row.localId) }
                        )
                    }

                    item {
                        OutlinedButton(onClick = viewModel::onAddGroup, modifier = Modifier.fillMaxWidth()) {
                            Text("إضافة مجموعة")
                        }
                    }

                    item {
                        val achievement = data.achievement
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("الملخص", style = MaterialTheme.typography.titleSmall)
                                SummaryRow("الإجمالي", Formatters.formatNumber(achievement?.totalTarget ?: 0.0))
                                SummaryRow("المحقق", Formatters.formatNumber(achievement?.achievedQuantity ?: 0.0))
                                SummaryRow("المتبقي", Formatters.formatNumber(achievement?.remainingQuantity ?: 0.0))
                                SummaryRow("نسبة التحقيق", Formatters.formatPercent(achievement?.achievementPercent ?: 0.0))
                            }
                        }
                    }

                    item {
                        Button(onClick = viewModel::onSave, modifier = Modifier.fillMaxWidth()) {
                            Text("حفظ الهدف")
                        }
                    }

                    if (data.isSaved) {
                        item { Snackbar { Text("تم حفظ الهدف بنجاح") } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
