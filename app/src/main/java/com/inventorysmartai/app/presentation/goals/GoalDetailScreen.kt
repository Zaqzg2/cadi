package com.inventorysmartai.app.presentation.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.presentation.common.SelectField
import com.inventorysmartai.app.presentation.common.toPickerGroups
import com.inventorysmartai.app.presentation.common.toPickerItems

/**
 * نموذج هدف واحد: الصنف، ثم مجموعات (الكمية المستهدفة + العمولة)، ثم ملخص الإنجاز. بعد الحفظ نعود إلى القائمة.
 * صفحة ثانوية بلا شريط سفلي (مثل تفاصيل الجرد)؛ زر الحفظ يرتفع فوق لوحة المفاتيح.
 */
@Composable
fun GoalDetailScreen(navController: NavController, viewModel: GoalDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val data = (uiState as? UiState.Success)?.data

    LaunchedEffect(data?.isSaved) { if (data?.isSaved == true) navController.popBackStack() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (data?.isNew == false) "تعديل الهدف" else "هدف جديد",
                onBack = { navController.popBackStack() }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::onSave,
                    enabled = data?.canSave == true,
                    modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("حفظ الهدف")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { d ->
                val productItems = remember(d.products) { d.products.toPickerItems() }
                val productGroups = remember(d.products) { d.products.toPickerGroups() }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        SelectField(
                            label = "الصنف",
                            items = productItems,
                            selectedId = d.selectedProductId,
                            onSelected = { id -> if (id != null) viewModel.onProductSelected(id) },
                            modifier = Modifier.fillMaxWidth(),
                            groups = productGroups,
                            clearable = false,
                            enabled = d.isNew
                        )
                    }

                    if (d.products.isEmpty()) {
                        item {
                            Text(
                                "لا توجد أصناف — أضف صنفًا من المخزون أولًا لتحدّد له هدفًا",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (d.updatesExisting) {
                        item {
                            Text(
                                "لهذا الصنف هدف في هذا الشهر — سيُحدَّث عند الحفظ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (d.selectedProductId == null) {
                        // اختيار الصنف يحمّل هدفه القائم (إن وُجد) ويستبدل المجموعات، فلا نفتح الحقول قبله كي لا يضيع ما يُكتب.
                        item {
                            Text(
                                "اختر الصنف أولًا لتحدّد مجموعات الهدف وعمولاتها",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(d.groups, key = { it.localId }) { row ->
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
                            val achievement = d.achievement
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
