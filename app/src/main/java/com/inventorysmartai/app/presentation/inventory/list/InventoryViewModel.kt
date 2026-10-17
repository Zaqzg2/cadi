@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.inventorysmartai.app.presentation.inventory.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.common.toDecimalOrNull
import com.inventorysmartai.app.domain.inventory.InventoryListFilter
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Category
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.domain.model.SortOrder
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.inventorysmartai.app.domain.importing.DateParsing

data class InventoryFilters(
    val searchQuery: String = "",
    val itemNumberQuery: String = "",
    val barcodeQuery: String = "",
    val selectedTab: InventoryStatusFilter = InventoryStatusFilter.ALL,
    val selectedBranchId: Long? = null,
    val selectedCategoryId: Long? = null,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    /** Stock-quantity bounds (set by the assistant: "الأصناف التي رصيدها أقل من 10"); strict unless [inclusiveBounds]. */
    val quantityBelow: Double? = null,
    val quantityAbove: Double? = null,
    val inclusiveBounds: Boolean = false
) {
    /** The same rules as a plain value, shared with the assistant's `showInventory` tool so both always agree on what matches. */
    fun toListFilter(): InventoryListFilter = InventoryListFilter(
        searchQuery = searchQuery,
        itemNumberQuery = itemNumberQuery,
        barcodeQuery = barcodeQuery,
        status = selectedTab,
        branchId = selectedBranchId,
        categoryId = selectedCategoryId,
        quantityBelow = quantityBelow,
        quantityAbove = quantityAbove,
        inclusiveBounds = inclusiveBounds,
        sortOrder = sortOrder
    )
}

/**
 * The filters the screen starts with: none for the plain Inventory route, or whatever the assistant put in the route of
 * [Destination.InventoryFiltered]. Unknown or malformed values are ignored, never fatal.
 */
internal fun initialInventoryFilters(handle: SavedStateHandle): InventoryFilters {
    fun text(key: String): String? = handle.get<String>(key)?.takeIf { it.isNotBlank() }
    val status = text(Destination.InventoryFiltered.ARG_STATUS)
        ?.let { runCatching { InventoryStatusFilter.valueOf(it) }.getOrNull() }
        ?: InventoryStatusFilter.ALL
    val sort = text(Destination.InventoryFiltered.ARG_SORT)
        ?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
        ?: SortOrder.NAME_ASC
    return InventoryFilters(
        searchQuery = text(Destination.InventoryFiltered.ARG_QUERY).orEmpty(),
        selectedTab = status,
        selectedBranchId = text(Destination.InventoryFiltered.ARG_BRANCH_ID)?.toLongOrNull(),
        selectedCategoryId = text(Destination.InventoryFiltered.ARG_CATEGORY_ID)?.toLongOrNull(),
        sortOrder = sort,
        quantityBelow = text(Destination.InventoryFiltered.ARG_BELOW)?.toDoubleOrNull(),
        quantityAbove = text(Destination.InventoryFiltered.ARG_ABOVE)?.toDoubleOrNull(),
        inclusiveBounds = text(Destination.InventoryFiltered.ARG_INCLUSIVE) == "1"
    )
}

enum class InventoryViewMode { TABLE, CARDS }

/** Cells of the inventory table that can be edited in place. */
enum class EditableField { NAME, ITEM_NUMBER, MIN_STOCK, REORDER_POINT }

private data class InventoryExtras(
    val viewMode: InventoryViewMode = InventoryViewMode.TABLE,
    val showArchived: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
)

data class InventoryUiData(
    val products: List<ProductStockSummary>,
    /** How many products exist in the current mode before any filter (0 = the list is truly empty). */
    val totalCount: Int,
    val branches: List<Branch>,
    val categories: List<Category>,
    val filters: InventoryFilters,
    val viewMode: InventoryViewMode,
    val showArchived: Boolean,
    val selectedIds: Set<Long>,
    /** How many products are in each stock status (by `InventoryStatus` name) before any filter — the summary tiles at the top. */
    val statusCounts: Map<String, Int> = emptyMap()
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val filters = MutableStateFlow(initialInventoryFilters(savedStateHandle))
    private val extras = MutableStateFlow(InventoryExtras())

    private val _uiState = MutableStateFlow<UiState<InventoryUiData>>(UiState.Loading)
    val uiState: StateFlow<UiState<InventoryUiData>> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Products a bulk delete could not remove because documents reference them → offer to archive. */
    private val _blocked = MutableStateFlow<List<Product>>(emptyList())
    val blocked: StateFlow<List<Product>> = _blocked.asStateFlow()

    private var lastProducts: List<ProductStockSummary> = emptyList()

    init {
        val source = extras.map { it.showArchived }.distinctUntilChanged().flatMapLatest { archived ->
            if (archived) productRepository.observeArchivedProducts() else productRepository.observeProductsWithStock()
        }
        viewModelScope.launch {
            combine(
                source,
                catalogRepository.observeBranches(),
                catalogRepository.observeCategories(),
                filters,
                extras
            ) { products, branches, categories, currentFilters, currentExtras ->
                lastProducts = products
                // Drop selections whose product disappeared (deleted / archived elsewhere).
                val liveIds = products.map { it.product.id }.toSet()
                val selection = currentExtras.selectedIds.intersect(liveIds)
                UiState.Success(
                    InventoryUiData(
                        products = applyFilters(products, currentFilters),
                        totalCount = products.size,
                        branches = branches,
                        categories = categories,
                        filters = currentFilters,
                        viewMode = currentExtras.viewMode,
                        showArchived = currentExtras.showArchived,
                        selectedIds = selection,
                        statusCounts = products.groupingBy { it.status.name }.eachCount()
                    )
                ) as UiState<InventoryUiData>
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل المخزون") }
                .collect { _uiState.value = it }
        }
    }

    private fun applyFilters(products: List<ProductStockSummary>, f: InventoryFilters): List<ProductStockSummary> =
        f.toListFilter().filterAndSort(products)

    // ---- filters ----
    fun onSearchQueryChange(value: String) = filters.update { it.copy(searchQuery = value) }
    fun onItemNumberQueryChange(value: String) = filters.update { it.copy(itemNumberQuery = value) }
    fun onBarcodeQueryChange(value: String) = filters.update { it.copy(barcodeQuery = value) }
    fun onTabSelected(tab: InventoryStatusFilter) = filters.update { it.copy(selectedTab = tab) }
    fun onBranchSelected(branchId: Long?) = filters.update { it.copy(selectedBranchId = branchId) }
    fun onCategorySelected(categoryId: Long?) = filters.update { it.copy(selectedCategoryId = categoryId) }
    fun onSortOrderSelected(order: SortOrder) = filters.update { it.copy(sortOrder = order) }
    fun onClearQuantityFilter() = filters.update { it.copy(quantityBelow = null, quantityAbove = null, inclusiveBounds = false) }

    // ---- view / selection ----
    fun toggleViewMode() = extras.update {
        it.copy(viewMode = if (it.viewMode == InventoryViewMode.TABLE) InventoryViewMode.CARDS else InventoryViewMode.TABLE, selectedIds = emptySet())
    }
    fun toggleArchived() = extras.update { it.copy(showArchived = !it.showArchived, selectedIds = emptySet()) }
    fun setSelection(ids: Set<Long>) = extras.update { it.copy(selectedIds = ids) }
    fun clearSelection() = extras.update { it.copy(selectedIds = emptySet()) }
    fun consumeMessage() { _message.value = null }
    fun dismissBlocked() { _blocked.value = emptyList() }

    // ---- actions ----

    /** Hard-delete. Products referenced by documents are not deleted; they come back via [blocked]. */
    fun deleteProducts(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { productRepository.deleteProducts(ids) }
                .onSuccess { result ->
                    clearSelection()
                    if (result.deleted > 0) _message.value = "تم حذف ${result.deleted} صنف"
                    if (result.blocked.isNotEmpty()) _blocked.value = result.blocked
                }
                .onFailure { _message.value = "تعذّر الحذف: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    fun archiveBlocked() {
        val ids = _blocked.value.map { it.id }
        _blocked.value = emptyList()
        setActive(ids, active = false)
    }

    fun setActive(ids: Collection<Long>, active: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { productRepository.setProductsActive(ids, active) }
                .onSuccess {
                    clearSelection()
                    _message.value = if (active) "تمت استعادة ${ids.size} صنف" else "تمت أرشفة ${ids.size} صنف"
                }
                .onFailure { _message.value = "تعذّر التنفيذ: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    /** In-place cell edit from the table. Invalid input is rejected with a message; nothing is half-saved. */
    fun editField(productId: Long, field: EditableField, text: String) {
        val product = lastProducts.firstOrNull { it.product.id == productId }?.product ?: return
        val clean = text.trim()
        val updated = when (field) {
            EditableField.NAME -> {
                if (clean.isEmpty()) { _message.value = "اسم الصنف لا يمكن أن يكون فارغًا"; return }
                product.copy(name = clean)
            }
            EditableField.ITEM_NUMBER -> product.copy(itemNumber = clean.ifEmpty { null })
            EditableField.MIN_STOCK -> {
                val v = clean.toDecimalOrNull()
                if (v == null || v < 0) { _message.value = "الحد الأدنى يجب أن يكون رقمًا صفرًا أو أكثر"; return }
                product.copy(minStock = v)
            }
            EditableField.REORDER_POINT -> {
                val v = clean.toDecimalOrNull()
                if (v == null || v < 0) { _message.value = "نقطة إعادة الطلب يجب أن تكون رقمًا صفرًا أو أكثر"; return }
                product.copy(reorderPoint = v)
            }
        }
        viewModelScope.launch {
            runCatching { productRepository.updateProduct(updated) }
                .onFailure { _message.value = it.message ?: "تعذّر حفظ التعديل" }
        }
    }

    /** تعديل تاريخ الانتهاء من الجدول: يُطبَّق على أقرب تاريخ (أو على كل الأسطر إن لم يوجد تاريخ). فارغ = مسح. */
    fun editExpiry(summary: ProductStockSummary, text: String) {
        val clean = text.trim()
        val date = if (clean.isEmpty()) null else (DateParsing.parseToMillis(clean) ?: run {
            _message.value = "تاريخ غير صالح — اكتبه مثل 2026-12-31"
            return
        })
        val targets = summary.branchStocks.filter { summary.nearestExpiryDate == null || it.expiryDate == summary.nearestExpiryDate }
        if (targets.isEmpty()) { _message.value = "لا يوجد رصيد لهذا الصنف — أضف رصيدًا أولًا"; return }
        viewModelScope.launch {
            runCatching { targets.forEach { productRepository.setStockExpiry(summary.product.id, it.branchId, it.batchNumber, date) } }
                .onSuccess {
                    _message.value = if (summary.branchStocks.size > 1) "تم تحديث أقرب تاريخ انتهاء — لتعديل فرع محدد افتح تفاصيل الصنف" else "تم تحديث تاريخ الانتهاء"
                }
                .onFailure { _message.value = "تعذّر الحفظ: ${it.message ?: it.javaClass.simpleName}" }
        }
    }
}
