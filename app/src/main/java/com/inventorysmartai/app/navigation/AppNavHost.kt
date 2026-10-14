package com.inventorysmartai.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inventorysmartai.app.domain.assistant.UiCommand
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inventorysmartai.app.presentation.assistant.AiAssistantScreen
import com.inventorysmartai.app.presentation.settings.googleservices.GoogleServicesStatusScreen
import com.inventorysmartai.app.presentation.counting.detail.CountingDetailScreen
import com.inventorysmartai.app.presentation.counting.list.CountingListScreen
import com.inventorysmartai.app.presentation.datacenter.DataCenterScreen
import com.inventorysmartai.app.presentation.datacenter.manual.ManualEntryScreen
import com.inventorysmartai.app.presentation.datacenter.history.ImportHistoryScreen
import com.inventorysmartai.app.presentation.datacenter.history.ImportJobDetailScreen
import com.inventorysmartai.app.presentation.datacenter.importflow.ImportAnalyzingScreen
import com.inventorysmartai.app.presentation.datacenter.importflow.ImportColumnMappingScreen
import com.inventorysmartai.app.presentation.datacenter.importflow.ImportFlowViewModel
import com.inventorysmartai.app.presentation.datacenter.importflow.ImportReviewScreen
import com.inventorysmartai.app.presentation.datacenter.importflow.ImportSetupScreen
import com.inventorysmartai.app.presentation.datacenter.importflow.ImportSheetSelectScreen
import com.inventorysmartai.app.presentation.goals.GoalsScreen
import com.inventorysmartai.app.presentation.home.HomeScreen
import com.inventorysmartai.app.presentation.inventory.detail.ProductDetailScreen
import com.inventorysmartai.app.presentation.inventory.list.InventoryScreen
import com.inventorysmartai.app.presentation.more.MoreScreen
import com.inventorysmartai.app.presentation.parties.PartyListScreen
import com.inventorysmartai.app.presentation.purchases.detail.PurchaseDetailScreen
import com.inventorysmartai.app.presentation.purchases.list.PurchaseListScreen
import com.inventorysmartai.app.presentation.reports.ReportDetailScreen
import com.inventorysmartai.app.presentation.reports.ReportsScreen
import com.inventorysmartai.app.presentation.sales.detail.SalesDetailScreen
import com.inventorysmartai.app.presentation.sales.list.SalesListScreen
import com.inventorysmartai.app.presentation.settings.AppInfoScreen
import com.inventorysmartai.app.presentation.settings.aiproviders.AiProvidersScreen
import com.inventorysmartai.app.presentation.settings.PlaceholderScreen
import com.inventorysmartai.app.presentation.settings.SettingsScreen
import com.inventorysmartai.app.presentation.settings.catalog.CatalogListScreen
import com.inventorysmartai.app.presentation.settings.inventorysettings.InventorySettingsScreen
import com.inventorysmartai.app.presentation.stock.StockHubScreen
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Destination.topLevelRoutes

    // The assistant drives the app through this bridge: its tools only post a command, and this is the one place that navigates.
    val shell: AppShellViewModel = hiltViewModel()
    val bridge = shell.bridge
    val notice by bridge.notice.collectAsStateWithLifecycle()
    LaunchedEffect(bridge) {
        bridge.commands.collect { command ->
            when (command) {
                // On top of the current screen (the chat stays one Back press away, and its running turn is not cancelled).
                is UiCommand.Navigate -> navController.navigate(command.route) { launchSingleTop = true }
            }
        }
    }

    Scaffold(
        bottomBar = { if (showBottomBar) BottomNavBar(currentRoute, navController) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Destination.Home.route,
                modifier = Modifier.padding(innerPadding).then(
                    // سحب أفقي على مساحة فارغة = الانتقال للتبويب المجاور. الجداول والبطاقات القابلة للسحب
                    // تستهلك الإيماءة أولًا فلا يحدث تعارض.
                    if (currentRoute in mainTabRoutes) Modifier.pointerInput(currentRoute) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                val i = mainTabRoutes.indexOf(currentRoute)
                                val target = when { total > 160f -> i + 1; total < -160f -> i - 1; else -> i }
                                if (target != i && target in mainTabRoutes.indices) navigateToTab(navController, mainTabRoutes[target])
                            },
                            onHorizontalDrag = { _, dx -> total += dx }
                        )
                    } else Modifier
                )
            ) {
                composable(Destination.Home.route) { HomeScreen(navController) }
                composable(Destination.Stock.route) { StockHubScreen(navController) }

                composable(Destination.Inventory.route) { InventoryScreen(navController) }
                // The same screen, opened by the assistant with its filters in the route (read by the ViewModel from its SavedStateHandle).
                composable(
                    Destination.InventoryFiltered.route,
                    arguments = listOf(
                        Destination.InventoryFiltered.ARG_STATUS,
                        Destination.InventoryFiltered.ARG_BELOW,
                        Destination.InventoryFiltered.ARG_ABOVE,
                        Destination.InventoryFiltered.ARG_INCLUSIVE,
                        Destination.InventoryFiltered.ARG_QUERY,
                        Destination.InventoryFiltered.ARG_BRANCH_ID,
                        Destination.InventoryFiltered.ARG_CATEGORY_ID,
                        Destination.InventoryFiltered.ARG_SORT
                    ).map { name -> navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null } }
                ) { InventoryScreen(navController) }
                composable(
                    Destination.ProductDetail.route,
                    arguments = listOf(navArgument(Destination.ProductDetail.ARG_PRODUCT_ID) { type = NavType.LongType })
                ) { ProductDetailScreen(navController) }

                composable(Destination.Goals.route) { GoalsScreen(navController) }

                composable(Destination.CountingList.route) { CountingListScreen(navController) }
                composable(
                    Destination.CountingDetail.route,
                    arguments = listOf(navArgument(Destination.CountingDetail.ARG_COUNT_ID) { type = NavType.LongType; defaultValue = -1L })
                ) { CountingDetailScreen(navController) }

                composable(Destination.PurchaseList.route) { PurchaseListScreen(navController) }
                composable(
                    Destination.PurchaseDetail.route,
                    arguments = listOf(navArgument(Destination.PurchaseDetail.ARG_REQUEST_ID) { type = NavType.LongType; defaultValue = -1L })
                ) { PurchaseDetailScreen(navController) }

                composable(Destination.SalesList.route) { SalesListScreen(navController) }
                composable(
                    Destination.SalesDetail.route,
                    arguments = listOf(navArgument(Destination.SalesDetail.ARG_INVOICE_ID) { type = NavType.LongType; defaultValue = -1L })
                ) { SalesDetailScreen(navController) }

                composable(Destination.Reports.route) { ReportsScreen(navController) }
                composable(
                    Destination.ReportDetail.route,
                    arguments = listOf(navArgument(Destination.ReportDetail.ARG_REPORT_TYPE) { type = NavType.StringType })
                ) { backStackEntry ->
                    val reportType = backStackEntry.arguments?.getString(Destination.ReportDetail.ARG_REPORT_TYPE).orEmpty()
                    ReportDetailScreen(navController, reportType)
                }

                composable(Destination.DataCenter.route) { DataCenterScreen(navController) }

                // --- Phase 3: bulk import flow — one shared ImportFlowViewModel across all four
                // steps via Hilt's back-stack-entry scoping (hiltViewModel(parentEntry)). ---
                navigation(startDestination = Destination.ImportSetup.route, route = Destination.ImportFlow.route) {
                    composable(Destination.ImportSetup.route) { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Destination.ImportFlow.route) }
                        ImportSetupScreen(navController, hiltViewModel<ImportFlowViewModel>(parentEntry))
                    }
                    composable(Destination.ImportSheetSelect.route) { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Destination.ImportFlow.route) }
                        ImportSheetSelectScreen(navController, hiltViewModel<ImportFlowViewModel>(parentEntry))
                    }
                    composable(Destination.ImportAnalyzing.route) { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Destination.ImportFlow.route) }
                        ImportAnalyzingScreen(navController, hiltViewModel<ImportFlowViewModel>(parentEntry))
                    }
                    composable(Destination.ImportColumnMapping.route) { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Destination.ImportFlow.route) }
                        ImportColumnMappingScreen(navController, hiltViewModel<ImportFlowViewModel>(parentEntry))
                    }
                    composable(Destination.ImportReview.route) { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Destination.ImportFlow.route) }
                        ImportReviewScreen(navController, hiltViewModel<ImportFlowViewModel>(parentEntry))
                    }
                }

                composable(
                    Destination.ImportHistory.route,
                    arguments = listOf(navArgument(Destination.ImportHistory.ARG_FILTER) { type = NavType.StringType; defaultValue = "all" })
                ) { ImportHistoryScreen(navController) }
                composable(
                    Destination.ImportJobDetail.route,
                    arguments = listOf(navArgument(Destination.ImportJobDetail.ARG_JOB_ID) { type = NavType.LongType })
                ) { ImportJobDetailScreen(navController) }

                composable(
                    Destination.ManualEntry.route,
                    arguments = listOf(
                        navArgument(Destination.ManualEntry.ARG_BARCODE) { type = NavType.StringType; defaultValue = "" },
                        navArgument(Destination.ManualEntry.ARG_PRODUCT_ID) { type = NavType.LongType; defaultValue = Destination.ManualEntry.NO_PRODUCT }
                    )
                ) { ManualEntryScreen(navController) }

                composable(
                    Destination.PartyList.route,
                    arguments = listOf(navArgument(Destination.PartyList.ARG_KIND) { type = NavType.StringType })
                ) { PartyListScreen(navController) }

                composable(Destination.AiAssistant.route) { AiAssistantScreen(navController) }
                composable(Destination.More.route) { MoreScreen(navController) }

                composable(Destination.Settings.route) { SettingsScreen(navController) }
                composable(Destination.SettingsAppInfo.route) { AppInfoScreen(navController) }
                composable(
                    Destination.SettingsCatalog.route,
                    arguments = listOf(navArgument(Destination.SettingsCatalog.ARG_TYPE) { type = NavType.StringType })
                ) { CatalogListScreen(navController) }
                composable(Destination.SettingsInventory.route) { InventorySettingsScreen(navController) }
                composable(Destination.GoogleServicesStatus.route) { GoogleServicesStatusScreen(navController) }
                composable(Destination.AiProviders.route) { AiProvidersScreen(navController) }
                composable(
                    Destination.SettingsPlaceholder.route,
                    arguments = listOf(navArgument(Destination.SettingsPlaceholder.ARG_KEY) { type = NavType.StringType })
                ) { backStackEntry ->
                    val key = backStackEntry.arguments?.getString(Destination.SettingsPlaceholder.ARG_KEY).orEmpty()
                    PlaceholderScreen(navController, key)
                }
            }

            // The assistant's answer, shown over the screen it opened for the person (the chat itself shows it when it is on screen).
            notice?.let { current ->
                if (currentRoute != Destination.AiAssistant.route) {
                    LaunchedEffect(current.id) {
                        delay(NOTICE_MILLIS)
                        bridge.clearNotice()
                    }
                    AssistantNoticeCard(
                        text = current.text,
                        onOpenChat = {
                            bridge.clearNotice()
                            if (!navController.popBackStack(Destination.AiAssistant.route, false)) {
                                navController.navigate(Destination.AiAssistant.route) { launchSingleTop = true }
                            }
                        },
                        onDismiss = { bridge.clearNotice() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(innerPadding).padding(12.dp)
                    )
                }
            }
        }
    }
}

private const val NOTICE_MILLIS = 15_000L

/** A small card with the assistant's last answer, over a screen the assistant opened. "فتح المحادثة" returns to the same conversation. */
@Composable
private fun AssistantNoticeCard(text: String, onOpenChat: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("المساعد الذكي", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("إخفاء") }
                TextButton(onClick = onOpenChat) { Text("فتح المحادثة") }
            }
        }
    }
}
