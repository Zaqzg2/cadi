package com.inventorysmartai.app.presentation.counting.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
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

data class CountItemRow(val productId: Long, val productName: String, val systemQuantity: Double, val actualQuantity: String, val notes: String = "")

data class CountingDetailData(
    val isNew: Boolean,
    val branches: List<Branch>,
    val products: List<Product>,
    val branchId: Long?,
    val countDate: Long,
    val status: CountStatus,
    val items: List<CountItemRow>,
    val isSaved: Boolean = false
)

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
    private val branchId = MutableStateFlow<Long?>(null)
    private val countDate = MutableStateFlow(System.currentTimeMillis())
    private val status = MutableStateFlow(CountStatus.DRAFT)
    private val items = MutableStateFlow<List<CountItemRow>>(emptyList())
    private val isSaved = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<UiState<CountingDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<CountingDetailData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            branches.value = catalogRepository.observeBranches().first()
            products.value = productRepository.observeProducts().first()
            if (!isNew) {
                val existing = countingRepository.observeCount(countId).first()
                if (existing != null) applyExisting(existing)
            } else {
                branchId.value = branches.value.firstOrNull()?.id
            }
            publish()
        }
    }

    private fun applyExisting(count: InventoryCount) {
        branchId.value = count.branchId
        countDate.value = count.countDate
        status.value = count.status
        items.value = count.items.map {
            CountItemRow(it.productId, it.productName ?: "", it.systemQuantity, it.actualQuantity.toString(), it.notes ?: "")
        }
    }

    private fun publish() {
        _uiState.value = UiState.Success(
            CountingDetailData(isNew, branches.value, products.value, branchId.value, countDate.value, status.value, items.value, isSaved.value)
        )
    }

    fun onBranchSelected(id: Long) {
        branchId.value = id
        publish()
    }

    fun onAddProduct(productId: Long) {
        if (items.value.any { it.productId == productId }) return
        val product = products.value.find { it.id == productId } ?: return
        viewModelScope.launch {
            val currentBranchId = branchId.value
            val systemQty = if (currentBranchId != null) {
                productRepository.observeProductDetail(productId).first()
                    ?.branchStocks?.find { it.branchId == currentBranchId }?.quantity ?: 0.0
            } else 0.0
            items.value = items.value + CountItemRow(productId, product.name, systemQty, systemQty.toString())
            publish()
        }
    }

    fun onActualQuantityChange(productId: Long, value: String) {
        items.value = items.value.map { if (it.productId == productId) it.copy(actualQuantity = value) else it }
        publish()
    }

    fun onItemNotesChange(productId: Long, value: String) {
        items.value = items.value.map { if (it.productId == productId) it.copy(notes = value) else it }
        publish()
    }

    fun onRemoveItem(productId: Long) {
        items.value = items.value.filterNot { it.productId == productId }
        publish()
    }

    fun onSave(markCompleted: Boolean) {
        val branch = branchId.value ?: return
        viewModelScope.launch {
            val finalStatus = if (markCompleted) CountStatus.COMPLETED else CountStatus.DRAFT
            countingRepository.saveCount(
                InventoryCount(
                    id = if (isNew) 0L else countId,
                    branchId = branch,
                    countDate = countDate.value,
                    status = finalStatus,
                    items = items.value.map {
                        InventoryCountItem(
                            productId = it.productId,
                            systemQuantity = it.systemQuantity,
                            actualQuantity = it.actualQuantity.toDoubleOrNull() ?: it.systemQuantity,
                            notes = it.notes.ifBlank { null }
                        )
                    }
                )
            )
            status.value = finalStatus
            isSaved.value = true
            publish()
        }
    }
}
