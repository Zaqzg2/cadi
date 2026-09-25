package com.inventorysmartai.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import com.inventorysmartai.app.presentation.settings.PlaceholderScreen
import com.inventorysmartai.app.presentation.settings.SettingsScreen
import com.inventorysmartai.app.presentation.settings.catalog.CatalogListScreen
import com.inventorysmartai.app.presentation.settings.inventorysettings.InventorySettingsScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Destination.topLevelRoutes

    Scaffold(
        bottomBar = { if (showBottomBar) BottomNavBar(currentRoute, navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Home.route) { HomeScreen(navController) }

            composable(Destination.Inventory.route) { InventoryScreen(navController) }
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
            composable(
                Destination.SettingsPlaceholder.route,
                arguments = listOf(navArgument(Destination.SettingsPlaceholder.ARG_KEY) { type = NavType.StringType })
            ) { backStackEntry ->
                val key = backStackEntry.arguments?.getString(Destination.SettingsPlaceholder.ARG_KEY).orEmpty()
                PlaceholderScreen(navController, key)
            }
        }
    }
}
