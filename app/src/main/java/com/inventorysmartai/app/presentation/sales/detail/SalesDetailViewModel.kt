package com.inventorysmartai.app.presentation.sales.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.data.repository.InsufficientStockException
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Customer
import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.model.SalesInvoiceItem
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.PartyRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.SalesRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SalesItemRow(
    val productId: Long,
    val productName: String,
    val itemNumberSnapshot: String? = null,
    val unitSnapshot: String? = null,
    val quantity: String,
    val unitPrice: String,
    val discountPercent: String = "0"
) {
    val lineTotal: Double get() =
        (quantity.toDoubleOrNull() ?: 0.0) * (unitPrice.toDoubleOrNull() ?: 0.0) * (1 - (discountPercent.toDoubleOrNull() ?: 0.0) / 100.0)
}

data class SalesDetailData(
    val isNew: Boolean,
    val branches: List<Branch>,
    val customers: List<Customer>,
    val products: List<Product>,
    val branchId: Long?,
    val customerId: Long?,
    val status: InvoiceStatus,
    val invoiceNumber: String,
    val items: List<SalesItemRow>,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
) {
    val total: Double get() = items.sumOf { it.lineTotal }
}

@HiltViewModel
class SalesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val salesRepository: SalesRepository,
    private val catalogRepository: CatalogRepository,
    private val partyRepository: PartyRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val invoiceId: Long = savedStateHandle[Destination.SalesDetail.ARG_INVOICE_ID] ?: -1L
    private val isNew = invoiceId <= 0L

    private val branches = MutableStateFlow<List<Branch>>(emptyList())
    private val customers = MutableStateFlow<List<Customer>>(emptyList())
    private val products = MutableStateFlow<List<Product>>(emptyList())
    private val branchId = MutableStateFlow<Long?>(null)
    private val customerId = MutableStateFlow<Long?>(null)
    private val status = MutableStateFlow(InvoiceStatus.DRAFT)
    private val invoiceNumber = MutableStateFlow("")
    private val items = MutableStateFlow<List<SalesItemRow>>(emptyList())
    private val isSaved = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow<UiState<SalesDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<SalesDetailData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            branches.value = catalogRepository.observeBranches().first()
            customers.value = partyRepository.observeCustomers().first()
            products.value = productRepository.observeProducts().first()
            if (!isNew) {
                salesRepository.observeInvoice(invoiceId).first()?.let { applyExisting(it) }
            } else {
                branchId.value = branches.value.firstOrNull()?.id
                customerId.value = customers.value.firstOrNull()?.id
                invoiceNumber.value = "INV-${System.currentTimeMillis().toString().takeLast(6)}"
            }
            publish()
        }
    }

    private fun applyExisting(invoice: SalesInvoice) {
        branchId.value = invoice.branchId
        customerId.value = invoice.customerId
        status.value = invoice.status
        invoiceNumber.value = invoice.invoiceNumber
        items.value = invoice.items.map {
            SalesItemRow(it.productId, it.productName ?: "", it.itemNumberSnapshot, it.unitSnapshot, it.quantity.toString(), it.unitPrice.toString(), it.discountPercent.toString())
        }
    }

    private fun publish() {
        _uiState.value = UiState.Success(
            SalesDetailData(isNew, branches.value, customers.value, products.value, branchId.value, customerId.value, status.value, invoiceNumber.value, items.value, isSaved.value, errorMessage.value)
        )
    }

    fun onBranchSelected(id: Long) { branchId.value = id; publish() }
    fun onCustomerSelected(id: Long) { customerId.value = id; publish() }
    fun onErrorShown() { errorMessage.value = null; publish() }

    fun onAddProduct(productId: Long) {
        if (items.value.any { it.productId == productId }) return
        val product = products.value.find { it.id == productId } ?: return
        items.value = items.value + SalesItemRow(
            productId = productId,
            productName = product.name,
            itemNumberSnapshot = product.itemNumber,
            unitSnapshot = product.unitName,
            quantity = "1",
            unitPrice = (product.defaultPrice ?: 0.0).toString()
        )
        publish()
    }

    fun onQuantityChange(productId: Long, value: String) {
        items.value = items.value.map { if (it.productId == productId) it.copy(quantity = value) else it }
        publish()
    }

    fun onPriceChange(productId: Long, value: String) {
        items.value = items.value.map { if (it.productId == productId) it.copy(unitPrice = value) else it }
        publish()
    }

    fun onDiscountChange(productId: Long, value: String) {
        items.value = items.value.map { if (it.productId == productId) it.copy(discountPercent = value) else it }
        publish()
    }

    fun onRemoveItem(productId: Long) {
        items.value = items.value.filterNot { it.productId == productId }
        publish()
    }

    /** [targetStatus] DRAFT just persists the lines with no stock effect at all; CONFIRMED is
     *  "complete the invoice" — validates stock, decreases inventory and writes a movement, all
     *  inside one DB transaction (see SalesRepositoryImpl). A failed validation leaves the form
     *  untouched and surfaces [SalesDetailData.errorMessage] instead of losing the user's edits. */
    fun onSave(targetStatus: InvoiceStatus) {
        val branch = branchId.value ?: return
        if (items.value.isEmpty()) return
        viewModelScope.launch {
            try {
                salesRepository.saveInvoice(
                    SalesInvoice(
                        id = if (isNew) 0L else invoiceId,
                        invoiceNumber = invoiceNumber.value,
                        invoiceDate = System.currentTimeMillis(),
                        customerId = customerId.value,
                        branchId = branch,
                        status = targetStatus,
                        items = items.value.map {
                            SalesInvoiceItem(
                                productId = it.productId,
                                itemNumberSnapshot = it.itemNumberSnapshot,
                                itemNameSnapshot = it.productName,
                                unitSnapshot = it.unitSnapshot,
                                quantity = it.quantity.toDoubleOrNull() ?: 0.0,
                                unitPrice = it.unitPrice.toDoubleOrNull() ?: 0.0,
                                discountPercent = it.discountPercent.toDoubleOrNull() ?: 0.0
                            )
                        }
                    )
                )
                status.value = targetStatus
                isSaved.value = true
                publish()
            } catch (e: InsufficientStockException) {
                errorMessage.value = e.message
                publish()
            }
        }
    }
}
