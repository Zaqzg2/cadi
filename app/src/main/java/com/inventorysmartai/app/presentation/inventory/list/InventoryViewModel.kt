package com.inventorysmartai.app.presentation.inventory.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Category
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.domain.model.SortOrder
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryFilters(
    val searchQuery: String = "",
    val itemNumberQuery: String = "",
    val barcodeQuery: String = "",
    val selectedTab: InventoryStatusFilter = InventoryStatusFilter.ALL,
    val selectedBranchId: Long? = null,
    val selectedCategoryId: Long? = null,
    val sortOrder: SortOrder = SortOrder.NAME_ASC
)

data class InventoryUiData(
    val products: List<ProductStockSummary>,
    val branches: List<Branch>,
    val categories: List<Category>,
    val filters: InventoryFilters
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val filters = MutableStateFlow(InventoryFilters())

    private val _uiState = MutableStateFlow<UiState<InventoryUiData>>(UiState.Loading)
    val uiState: StateFlow<UiState<InventoryUiData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                productRepository.observeProductsWithStock(),
                catalogRepository.observeBranches(),
                catalogRepository.observeCategories(),
                filters
            ) { products, branches, categories, currentFilters ->
                val filtered = applyFilters(products, currentFilters)
                if (products.isEmpty()) {
                    UiState.Empty("لا توجد أصناف بعد — أضف صنفك الأول من مركز البيانات")
                } else {
                    UiState.Success(InventoryUiData(filtered, branches, categories, currentFilters))
                }
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل المخزون") }
                .collect { _uiState.value = it }
        }
    }

    private fun applyFilters(products: List<ProductStockSummary>, f: InventoryFilters): List<ProductStockSummary> {
        var result = products
        if (f.selectedTab != InventoryStatusFilter.ALL) {
            result = result.filter { it.status.name == f.selectedTab.name }
        }
        if (f.searchQuery.isNotBlank()) {
            result = result.filter { it.product.name.contains(f.searchQuery, ignoreCase = true) }
        }
        if (f.itemNumberQuery.isNotBlank()) {
            result = result.filter { it.product.itemNumber?.contains(f.itemNumberQuery, ignoreCase = true) == true }
        }
        if (f.barcodeQuery.isNotBlank()) {
            result = result.filter { it.product.barcode?.contains(f.barcodeQuery, ignoreCase = true) == true }
        }
        if (f.selectedBranchId != null) {
            result = result.filter { summary -> summary.branchStocks.any { it.branchId == f.selectedBranchId } }
        }
        if (f.selectedCategoryId != null) {
            result = result.filter { it.product.categoryId == f.selectedCategoryId }
        }
        result = when (f.sortOrder) {
            SortOrder.NAME_ASC -> result.sortedBy { it.product.name }
            SortOrder.NAME_DESC -> result.sortedByDescending { it.product.name }
            SortOrder.QUANTITY_ASC -> result.sortedBy { it.totalQuantity }
            SortOrder.QUANTITY_DESC -> result.sortedByDescending { it.totalQuantity }
        }
        return result
    }

    fun onSearchQueryChange(value: String) = filters.update { it.copy(searchQuery = value) }
    fun onItemNumberQueryChange(value: String) = filters.update { it.copy(itemNumberQuery = value) }
    fun onBarcodeQueryChange(value: String) = filters.update { it.copy(barcodeQuery = value) }
    fun onTabSelected(tab: InventoryStatusFilter) = filters.update { it.copy(selectedTab = tab) }
    fun onBranchSelected(branchId: Long?) = filters.update { it.copy(selectedBranchId = branchId) }
    fun onCategorySelected(categoryId: Long?) = filters.update { it.copy(selectedCategoryId = categoryId) }
    fun onSortOrderSelected(order: SortOrder) = filters.update { it.copy(sortOrder = order) }
}
