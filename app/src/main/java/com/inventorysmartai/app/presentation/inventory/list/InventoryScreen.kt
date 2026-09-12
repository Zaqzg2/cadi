package com.inventorysmartai.app.presentation.inventory.list

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.SearchField
import com.inventorysmartai.app.core.designsystem.component.StateContent
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.presentation.inventory.components.InventoryFilterSheet
import com.inventorysmartai.app.presentation.inventory.components.ProductCard

private val tabs = listOf(
    InventoryStatusFilter.ALL to "الكل",
    InventoryStatusFilter.AVAILABLE to "متوفر",
    InventoryStatusFilter.LOW to "منخفض",
    InventoryStatusFilter.ZERO to "صفر",
    InventoryStatusFilter.NEAR_EXPIRY to "قريب الانتهاء",
    InventoryStatusFilter.EXPIRED to "منتهي"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(navController: NavController, viewModel: InventoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAdvancedSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المخزون") },
                actions = {
                    IconButton(onClick = { showAdvancedSearch = !showAdvancedSearch }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "بحث بالباركود / رقم الصنف")
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "الفرز والفلاتر")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state = uiState, onRetry = {}) { data ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SearchField(value = data.filters.searchQuery, onValueChange = viewModel::onSearchQueryChange)
                    if (showAdvancedSearch) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = data.filters.itemNumberQuery,
                                onValueChange = viewModel::onItemNumberQueryChange,
                                label = { Text("رقم الصنف") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = data.filters.barcodeQuery,
                                onValueChange = viewModel::onBarcodeQueryChange,
                                label = { Text("الباركود") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }

                val selectedIndex = tabs.indexOfFirst { it.first == data.filters.selectedTab }.coerceAtLeast(0)
                ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 16.dp) {
                    tabs.forEach { (tab, label) ->
                        Tab(
                            selected = data.filters.selectedTab == tab,
                            onClick = { viewModel.onTabSelected(tab) },
                            text = { Text(label) }
                        )
                    }
                }

                if (data.products.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Text("لا توجد أصناف مطابقة لهذا الفلتر", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(data.products, key = { it.product.id }) { summary ->
                            ProductCard(
                                summary = summary,
                                onClick = { navController.navigate(Destination.ProductDetail.createRoute(summary.product.id)) }
                            )
                        }
                    }
                }

                if (showFilterSheet) {
                    InventoryFilterSheet(
                        branches = data.branches,
                        categories = data.categories,
                        selectedBranchId = data.filters.selectedBranchId,
                        selectedCategoryId = data.filters.selectedCategoryId,
                        sortOrder = data.filters.sortOrder,
                        onBranchSelected = viewModel::onBranchSelected,
                        onCategorySelected = viewModel::onCategorySelected,
                        onSortOrderSelected = viewModel::onSortOrderSelected,
                        onDismiss = { showFilterSheet = false }
                    )
                }
            }
        }
    }
}
