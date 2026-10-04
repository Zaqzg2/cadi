package com.inventorysmartai.app.presentation.counting.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.common.toDecimalOrNull
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.CountStatus
import com.inventorysmartai.app.domain.model.InventoryCount
import com.inventorysmartai.app.domain.model.InventoryCountItem
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.CountingRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CountItemRow(
    val productId: Long,
    val productName: String,
    val systemQuantity: Double,
    val actualQuantity: String,
    val notes: String = ""
) {
    val difference: Double? get() = actualQuantity.toDecimalOrNull()?.let { it - systemQuantity }
}

data class CountingDetailData(
    val isNew: Boolean,
    val branches: List<Branch>,
    val products: List<Product>,
    val branchId: Long?,
    val countDate: Long,
    val status: CountStatus,
    val items: List<CountItemRow>,
    val isSaved: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val removedMessage: String? = null
) {
    /** A completed count already corrected the stock — it is a record now, not editable. */
    val readOnly: Boolean get() = status == CountStatus.COMPLETED || status == CountStatus.CANCELLED
    val differingCount: Int get() = items.count { (it.difference ?: 0.0) != 0.0 }
}

@HiltViewModel
class CountingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val countingRepository: CountingRepository,
    private val catalogRepository: CatalogRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val countId: Long = savedStateHandle[Destination.CountingDetail.ARG_COUNT_ID] ?: -1L
    private val isNew = countId <= 0L

    private val branches = MutableStateFlow<List<Branch>>(emptyList())
    private val products = MutableStateFlow<List<Product>>(emptyList())
    /** productId -> (branchId -> quantity) */
    private var stock: Map<Long, Map<Long, Double>> = emptyMap()
    private val branchId = MutableStateFlow<Long?>(null)
    private val countDate = MutableStateFlow(System.currentTimeMillis())
    private val status = MutableStateFlow(CountStatus.DRAFT)
    private val items = MutableStateFlow<List<CountItemRow>>(emptyList())
    private val isSaved = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val removedMessage = MutableStateFlow<String?>(null)
    private var lastRemoved: List<Pair<Int, CountItemRow>> = emptyList()
    private var existing: InventoryCount? = null

    private val _uiState = MutableStateFlow<UiState<CountingDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<CountingDetailData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            branches.value = catalogRepository.observeBranches().first().filter { it.isActive }
            val summaries = productRepository.observeProductsWithStock().first()
            products.value = summaries.map { it.product }
            stock = summaries.associate { s -> s.product.id to s.branchStocks.groupBy { it.branchId }.mapValues { (_, v) -> v.sumOf { it.quantity } } }
            if (!isNew) {
                countingRepository.observeCount(countId).first()?.let { applyExisting(it) }
            } else {
                branchId.value = branches.value.firstOrNull()?.id
            }
            publish()
        }
    }

    private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    private fun systemQty(productId: Long, branch: Long?): Double = if (branch == null) 0.0 else (stock[productId]?.get(branch) ?: 0.0)

    private fun applyExisting(count: InventoryCount) {
        existing = count
        branchId.value = count.branchId
        countDate.value = count.countDate
        status.value = count.status
        items.value = count.items.map {
            CountItemRow(it.productId, it.productName.orEmpty(), it.systemQuantity, plain(it.actualQuantity), it.notes.orEmpty())
        }
    }

    private fun publish() {
        _uiState.value = UiState.Success(
            CountingDetailData(
                isNew, branches.value, products.value, branchId.value, countDate.value, status.value, items.value,
                isSaved.value, message.value, errorMessage.value, removedMessage.value
            )
        )
    }

    fun onMessageShown() { message.value = null; publish() }
    fun onErrorShown() { errorMessage.value = null; publish() }
    fun onRemovedMessageShown() { removedMessage.value = null; publish() }

    /** Changing the branch refreshes every line's system quantity (it is per branch); lines the person
     *  has not edited follow the new system quantity, edited ones keep what was typed. */
    fun onBranchSelected(id: Long?) {
        if (id == null || status.value.let { it == CountStatus.COMPLETED || it == CountStatus.CANCELLED }) return
        branchId.value = id
        items.value = items.value.map { row ->
            val newSystem = systemQty(row.productId, id)
            val untouched = row.actualQuantity == plain(row.systemQuantity)
            row.copy(systemQuantity = newSystem, actualQuantity = if (untouched) plain(newSystem) else row.actualQuantity)
        }
        publish()
    }

    private fun readOnly() = status.value == CountStatus.COMPLETED || status.value == CountStatus.CANCELLED

    fun onAddProducts(ids: Set<Long>) {
        if (readOnly()) return
        val branch = branchId.value
        val toAdd = ids.filter { id -> items.value.none { it.productId == id } }.mapNotNull { id -> products.value.find { it.id == id } }
        if (toAdd.isEmpty()) return
        items.value = items.value + toAdd.map { p -> systemQty(p.id, branch).let { sys -> CountItemRow(p.id, p.name, sys, plain(sys)) } }
        message.value = "تمت إضافة ${toAdd.size} صنف — عدّل الكمية الفعلية لما يختلف"
        publish()
    }

    /** Scanning counts one unit: a new line starts at 1, a repeated scan adds 1 (physical counting by scanning). */
    fun onBarcodeScanned(code: String) {
        if (readOnly()) return
        val clean = code.trim()
        val product = products.value.find { it.barcode == clean } ?: products.value.find { it.itemNumber == clean }
        if (product == null) { message.value = "لا يوجد صنف بالباركود $clean"; publish(); return }
        val row = items.value.find { it.productId == product.id }
        if (row == null) {
            items.value = items.value + CountItemRow(product.id, product.name, systemQty(product.id, branchId.value), "1")
            message.value = "«${product.name}» — العدد 1"
        } else {
            val next = (row.actualQuantity.toDecimalOrNull() ?: 0.0) + 1
            items.value = items.value.map { if (it.productId == product.id) it.copy(actualQuantity = plain(next)) else it }
            message.value = "«${product.name}» — العدد ${plain(next)}"
        }
        publish()
    }

    fun onActualQuantityChange(productId: Long, value: String) {
        if (readOnly()) return
        items.value = items.value.map { if (it.productId == productId) it.copy(actualQuantity = value) else it }
        publish()
    }

    fun onItemNotesChange(productId: Long, value: String) {
        if (readOnly()) return
        items.value = items.value.map { if (it.productId == productId) it.copy(notes = value) else it }
        publish()
    }

    fun onRemoveItems(ids: Set<Long>) {
        if (readOnly()) return
        val removed = items.value.withIndex().filter { it.value.productId in ids }.map { it.index to it.value }
        if (removed.isEmpty()) return
        lastRemoved = removed
        items.value = items.value.filterNot { it.productId in ids }
        removedMessage.value = if (removed.size == 1) "تمت إزالة «${removed[0].second.productName}»" else "تمت إزالة ${removed.size} أصناف"
        publish()
    }

    fun onUndoRemove() {
        if (lastRemoved.isEmpty()) return
        val list = items.value.toMutableList()
        lastRemoved.sortedBy { it.first }.forEach { (i, row) -> list.add(i.coerceAtMost(list.size), row) }
        items.value = list
        lastRemoved = emptyList()
        publish()
    }

    /** An invalid / blank actual quantity used to be silently replaced by the system quantity (hiding a typo
     *  as "no difference"). Now it blocks the save and names the line. */
    private fun validate(): String? {
        if (items.value.isEmpty()) return "أضف صنفًا واحدًا على الأقل"
        items.value.forEachIndexed { i, row ->
            val qty = row.actualQuantity.toDecimalOrNull()
            if (qty == null || qty < 0.0) return "السطر ${i + 1} (${row.productName}): الكمية الفعلية غير صالحة"
        }
        return null
    }

    fun onSave(markCompleted: Boolean) {
        if (readOnly()) return
        val branch = branchId.value ?: run { errorMessage.value = "اختر الفرع"; publish(); return }
        // A draft may keep half-typed lines; completing (which changes stock) needs every line valid.
        if (markCompleted) validate()?.let { errorMessage.value = it; publish(); return }
        viewModelScope.launch {
            val finalStatus = if (markCompleted) CountStatus.COMPLETED else CountStatus.DRAFT
            try {
                val base = existing ?: InventoryCount(branchId = branch, countDate = countDate.value, status = finalStatus)
                countingRepository.saveCount(
                    base.copy(
                        id = if (isNew) 0L else countId,
                        branchId = branch,
                        countDate = countDate.value,
                        status = finalStatus,
                        items = items.value.map {
                            InventoryCountItem(
                                productId = it.productId,
                                systemQuantity = it.systemQuantity,
                                actualQuantity = it.actualQuantity.toDecimalOrNull() ?: it.systemQuantity,
                                notes = it.notes.ifBlank { null }
                            )
                        }
                    )
                )
                status.value = finalStatus
                isSaved.value = true
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "تعذّر الحفظ"
            }
            publish()
        }
    }

    fun onDeleteDraft() {
        if (isNew || readOnly()) return
        viewModelScope.launch {
            runCatching { countingRepository.deleteDraftCount(countId) }
                .onSuccess { isSaved.value = true }
                .onFailure { errorMessage.value = it.message ?: "تعذّر الحذف" }
            publish()
        }
    }
}
