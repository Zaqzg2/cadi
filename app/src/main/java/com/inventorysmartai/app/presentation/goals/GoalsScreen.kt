@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.goals

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.CardAction
import com.inventorysmartai.app.core.designsystem.component.DestructiveConfirmDialog
import com.inventorysmartai.app.core.designsystem.component.LongPressCard
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.core.designsystem.component.SwipeActionRow
import com.inventorysmartai.app.core.designsystem.component.rememberTopBarSearch
import com.inventorysmartai.app.navigation.Destination

/**
 * قائمة الأهداف — موحَّدة مع قائمة الجرد: ترويسة مدمجة وبحث بالأيقونة، بطاقات (سحب/ضغط مطوّل للحذف)،
 * وزر + لهدف جديد. الضغط على بطاقة يفتح نموذج ذلك الهدف في صفحة مستقلة ([GoalDetailScreen]).
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
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.goal.id }) { item ->
                        // swipe: delete, tap: open — same behaviour as the counting list
                        SwipeActionRow(onDelete = { pendingDelete = item }) {
                            LongPressCard(
                                onClick = { openGoal(item) },
                                actions = listOf(
                                    CardAction("فتح") { openGoal(item) },
                                    CardAction("حذف") { pendingDelete = item }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.goal.productName ?: "صنف", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "${Formatters.formatDate(item.goal.periodStart)} • المحقق ${Formatters.formatNumber(item.achievement.achievedQuantity)} من ${Formatters.formatNumber(item.achievement.totalTarget)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(
                                        text = Formatters.formatPercent(item.achievement.achievementPercent),
                                        color = if (item.achievement.achievementPercent >= 100.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
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
