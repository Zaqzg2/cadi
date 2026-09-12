package com.inventorysmartai.app.presentation.purchases.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.PurchaseReceipt
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.model.PurchaseRequestItem
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.domain.model.Supplier
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.PartyRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseItemRow(val productId: Long, val productName: String, val currentStock: Double, val requestedQuantity: String, val receivedQuantity: Double = 0.0)

data class PurchaseDetailData(
    val isNew: Boolean,
    val branches: List<Branch>,
    val suppliers: List<Supplier>,
    val products: List<Product>,
    val branchId: Long?,
    val supplierId: Long?,
    val status: PurchaseStatus,
    val requestNumber: String,
    val items: List<PurchaseItemRow>,
    val isSaved: Boolean = false
)

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val purchaseRepository: PurchaseRepository,
    private val catalogRepository: CatalogRepository,
    private val partyRepository: PartyRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val requestId: Long = savedStateHandle[Destination.PurchaseDetail.ARG_REQUEST_ID] ?: -1L
    private val isNew = requestId <= 0L

    private val branches = MutableStateFlow<List<Branch>>(emptyList())
    private val suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    private val products = MutableStateFlow<List<Product>>(emptyList())
    private val branchId = MutableStateFlow<Long?>(null)
    private val supplierId = MutableStateFlow<Long?>(null)
    private val status = MutableStateFlow(PurchaseStatus.DRAFT)
    private val requestNumber = MutableStateFlow("")
    private val items = MutableStateFlow<List<PurchaseItemRow>>(emptyList())
    private val isSaved = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<UiState<PurchaseDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<PurchaseDetailData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            branches.value = catalogRepository.observeBranches().first()
            suppliers.value = partyRepository.observeSuppliers().first()
            products.value = productRepository.observeProducts().first()
            if (!isNew) {
                purchaseRepository.observeRequest(requestId).first()?.let { applyExisting(it) }
            } else {
                branchId.value = branches.value.firstOrNull()?.id
                requestNumber.value = "PR-${System.currentTimeMillis().toString().takeLast(6)}"
            }
            publish()
        }
    }

    private fun applyExisting(request: PurchaseRequest) {
        branchId.value = request.branchId
        supplierId.value = request.supplierId
        status.value = request.status
        requestNumber.value = request.requestNumber
        items.value = request.items.map {
            PurchaseItemRow(it.productId, it.productName ?: "", it.currentStockSnapshot, it.requestedQuantity.toString(), it.receivedQuantity)
        }
    }

    private fun publish() {
        _uiState.value = UiState.Success(
            PurchaseDetailData(isNew, branches.value, suppliers.value, products.value, branchId.value, supplierId.value, status.value, requestNumber.value, items.value, isSaved.value)
        )
    }

    fun onBranchSelected(id: Long) { branchId.value = id; publish() }
    fun onSupplierSelected(id: Long) { supplierId.value = id; publish() }

    fun onAddProduct(productId: Long) {
        if (items.value.any { it.productId == productId }) return
        val product = products.value.find { it.id == productId } ?: return
        viewModelScope.launch {
            val currentStock = productRepository.observeProductDetail(productId).first()?.totalQuantity ?: 0.0
            items.value = items.value + PurchaseItemRow(productId, product.name, currentStock, "")
            publish()
        }
    }

    fun onQuantityChange(productId: Long, value: String) {
        items.value = items.value.map { if (it.productId == productId) it.copy(requestedQuantity = value) else it }
        publish()
    }

    fun onRemoveItem(productId: Long) {
        items.value = items.value.filterNot { it.productId == productId }
        publish()
    }

    fun onSave(newStatus: PurchaseStatus) {
        val branch = branchId.value ?: return
        viewModelScope.launch {
            purchaseRepository.saveRequest(
                PurchaseRequest(
                    id = if (isNew) 0L else requestId,
                    requestNumber = requestNumber.value,
                    supplierId = supplierId.value,
                    branchId = branch,
                    requestDate = System.currentTimeMillis(),
                    status = newStatus,
                    items = items.value.map {
                        PurchaseRequestItem(
                            productId = it.productId,
                            currentStockSnapshot = it.currentStock,
                            requestedQuantity = it.requestedQuantity.toDoubleOrNull() ?: 0.0,
                            receivedQuantity = it.receivedQuantity
                        )
                    }
                )
            )
            status.value = newStatus
            isSaved.value = true
            publish()
        }
    }

    /** Marks the request RECEIVED and, via PurchaseRepository.receive(), actually moves stock. */
    fun onReceiveAll() {
        if (isNew) return
        viewModelScope.launch {
            val receivedQuantities = items.value.associate { row ->
                row.productId to ((row.requestedQuantity.toDoubleOrNull() ?: 0.0) - row.receivedQuantity).coerceAtLeast(0.0)
            }
            purchaseRepository.receive(
                PurchaseReceipt(purchaseRequestId = requestId, receivedDate = System.currentTimeMillis()),
                receivedQuantities
            )
            onSave(PurchaseStatus.RECEIVED)
        }
    }
}
