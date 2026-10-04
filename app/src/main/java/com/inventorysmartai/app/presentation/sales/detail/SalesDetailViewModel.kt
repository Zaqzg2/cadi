package com.inventorysmartai.app.presentation.sales.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.common.toDecimalOrNull
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
        (quantity.toDecimalOrNull() ?: 0.0) * (unitPrice.toDecimalOrNull() ?: 0.0) * (1 - (discountPercent.toDecimalOrNull() ?: 0.0) / 100.0)
}

data class SalesDetailData(
    val isNew: Boolean,
    val branches: List<Branch>,
    val customers: List<Customer>,
    val products: List<Product>,
    /** Stock of each product in the CURRENT branch — shown in the picker and the table. */
    val stockByProduct: Map<Long, Double>,
    val branchId: Long?,
    val customerId: Long?,
    val status: InvoiceStatus,
    val invoiceNumber: String,
    val items: List<SalesItemRow>,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    /** One-shot snackbar text. */
    val message: String? = null,
    /** Set right after rows were removed → the screen offers "تراجع". */
    val removedMessage: String? = null
) {
    val total: Double get() = items.sumOf { it.lineTotal }
    /** A confirmed/cancelled invoice is history: it already moved stock, so it is view-only. */
    val readOnly: Boolean get() = status != InvoiceStatus.DRAFT
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
    /** productId -> (branchId -> quantity) */
    private var stock: Map<Long, Map<Long, Double>> = emptyMap()
    private val branchId = MutableStateFlow<Long?>(null)
    private val customerId = MutableStateFlow<Long?>(null)
    private val status = MutableStateFlow(InvoiceStatus.DRAFT)
    private val invoiceNumber = MutableStateFlow("")
    private val items = MutableStateFlow<List<SalesItemRow>>(emptyList())
    private val isSaved = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val removedMessage = MutableStateFlow<String?>(null)

    /** Rows removed last, with their position, for "تراجع". */
    private var lastRemoved: List<Pair<Int, SalesItemRow>> = emptyList()
    /** The loaded invoice — fields the screen does not edit (notes, currency, balances...) are kept from it. */
    private var existing: SalesInvoice? = null

    private val _uiState = MutableStateFlow<UiState<SalesDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<SalesDetailData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            branches.value = catalogRepository.observeBranches().first().filter { it.isActive }
            customers.value = partyRepository.observeCustomers().first()
            val summaries = productRepository.observeProductsWithStock().first()
            products.value = summaries.map { it.product }
            stock = summaries.associate { s -> s.product.id to s.branchStocks.groupBy { it.branchId }.mapValues { (_, v) -> v.sumOf { it.quantity } } }
            if (!isNew) {
                salesRepository.observeInvoice(invoiceId).first()?.let { applyExisting(it) }
            } else {
                branchId.value = branches.value.firstOrNull()?.id
                // No customer by default: a new invoice used to be silently attached to the FIRST customer.
                customerId.value = null
                invoiceNumber.value = "INV-${System.currentTimeMillis().toString().takeLast(6)}"
            }
            publish()
        }
    }

    private fun applyExisting(invoice: SalesInvoice) {
        existing = invoice
        branchId.value = invoice.branchId
        customerId.value = invoice.customerId
        status.value = invoice.status
        invoiceNumber.value = invoice.invoiceNumber
        items.value = invoice.items.map {
            SalesItemRow(
                it.productId, it.productName ?: it.itemNameSnapshot.orEmpty(), it.itemNumberSnapshot, it.unitSnapshot,
                plain(it.quantity), plain(it.unitPrice), plain(it.discountPercent)
            )
        }
    }

    private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    private fun publish() {
        val branch = branchId.value
        val stockHere = products.value.associate { p -> p.id to (stock[p.id]?.get(branch) ?: 0.0) }
        _uiState.value = UiState.Success(
            SalesDetailData(
                isNew = isNew, branches = branches.value, customers = customers.value, products = products.value,
                stockByProduct = stockHere, branchId = branch, customerId = customerId.value, status = status.value,
                invoiceNumber = invoiceNumber.value, items = items.value, isSaved = isSaved.value,
                errorMessage = errorMessage.value, message = message.value, removedMessage = removedMessage.value
            )
        )
    }

    fun onBranchSelected(id: Long?) { if (id != null) { branchId.value = id; publish() } }
    fun onCustomerSelected(id: Long?) { customerId.value = id; publish() }
    fun onErrorShown() { errorMessage.value = null; publish() }
    fun onMessageShown() { message.value = null; publish() }
    fun onRemovedMessageShown() { removedMessage.value = null; publish() }

    private fun newRow(product: Product) = SalesItemRow(
        productId = product.id,
        productName = product.name,
        itemNumberSnapshot = product.itemNumber,
        unitSnapshot = product.unitName,
        quantity = "1",
        unitPrice = plain(product.defaultPrice ?: 0.0)
    )

    fun onAddProduct(productId: Long) = onAddProducts(setOf(productId))

    /** Adds several products at once (from the picker). Already-present ones are skipped. */
    fun onAddProducts(ids: Set<Long>) {
        if (status.value != InvoiceStatus.DRAFT) return
        val toAdd = ids.filter { id -> items.value.none { it.productId == id } }.mapNotNull { id -> products.value.find { it.id == id } }
        if (toAdd.isEmpty()) return
        items.value = items.value + toAdd.map(::newRow)
        message.value = "تمت إضافة ${toAdd.size} صنف"
        publish()
    }

    /** Barcode / item number scanned: add the product, or raise its quantity by 1 if already on the invoice. */
    fun onBarcodeScanned(code: String) {
        if (status.value != InvoiceStatus.DRAFT) return
        val clean = code.trim()
        val product = products.value.find { it.barcode == clean } ?: products.value.find { it.itemNumber == clean }
        if (product == null) { message.value = "لا يوجد صنف بالباركود $clean"; publish(); return }
        val row = items.value.find { it.productId == product.id }
        if (row == null) {
            items.value = items.value + newRow(product)
            message.value = "أُضيف «${product.name}»"
        } else {
            val newQty = (row.quantity.toDecimalOrNull() ?: 0.0) + 1
            items.value = items.value.map { if (it.productId == product.id) it.copy(quantity = plain(newQty)) else it }
            message.value = "«${product.name}» — الكمية ${plain(newQty)}"
        }
        publish()
    }

    fun onQuantityChange(productId: Long, value: String) = edit(productId) { it.copy(quantity = value) }
    fun onPriceChange(productId: Long, value: String) = edit(productId) { it.copy(unitPrice = value) }
    fun onDiscountChange(productId: Long, value: String) = edit(productId) { it.copy(discountPercent = value) }

    private fun edit(productId: Long, change: (SalesItemRow) -> SalesItemRow) {
        if (status.value != InvoiceStatus.DRAFT) return
        items.value = items.value.map { if (it.productId == productId) change(it) else it }
        publish()
    }

    fun onRemoveItem(productId: Long) = onRemoveItems(setOf(productId))

    fun onRemoveItems(ids: Set<Long>) {
        if (status.value != InvoiceStatus.DRAFT) return
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
        lastRemoved.sortedBy { it.first }.forEach { (index, row) -> list.add(index.coerceAtMost(list.size), row) }
        items.value = list
        lastRemoved = emptyList()
        publish()
    }

    /** First problem found in the lines, phrased for the person ("السطر 3 (حليب): الكمية غير صالحة"). */
    private fun validate(): String? {
        if (items.value.isEmpty()) return "أضف صنفًا واحدًا على الأقل"
        items.value.forEachIndexed { i, row ->
            val where = "السطر ${i + 1} (${row.productName})"
            val qty = row.quantity.toDecimalOrNull()
            if (qty == null || qty <= 0.0) return "$where: الكمية يجب أن تكون أكبر من صفر"
            val price = row.unitPrice.toDecimalOrNull()
            if (price == null || price < 0.0) return "$where: السعر غير صالح"
            val discount = if (row.discountPercent.isBlank()) 0.0 else row.discountPercent.toDecimalOrNull()
            if (discount == null || discount < 0.0 || discount > 100.0) return "$where: الخصم يجب أن يكون بين 0 و100"
        }
        return null
    }

    /** [targetStatus] DRAFT just persists the lines with no stock effect at all; CONFIRMED is
     *  "complete the invoice" — validates stock, decreases inventory and writes a movement, all
     *  inside one DB transaction (see SalesRepositoryImpl). A failed validation leaves the form
     *  untouched and surfaces [SalesDetailData.errorMessage] instead of losing the user's edits. */
    fun onSave(targetStatus: InvoiceStatus) {
        if (status.value != InvoiceStatus.DRAFT) return
        val branch = branchId.value ?: run { errorMessage.value = "اختر الفرع"; publish(); return }
        validate()?.let { errorMessage.value = it; publish(); return }
        val base = existing
        viewModelScope.launch {
            try {
                salesRepository.saveInvoice(
                    (base ?: SalesInvoice(invoiceNumber = invoiceNumber.value, invoiceDate = System.currentTimeMillis(), customerId = null, branchId = branch)).copy(
                        id = if (isNew) 0L else invoiceId,
                        invoiceNumber = invoiceNumber.value,
                        customerId = customerId.value,
                        branchId = branch,
                        status = targetStatus,
                        items = items.value.map {
                            SalesInvoiceItem(
                                productId = it.productId,
                                itemNumberSnapshot = it.itemNumberSnapshot,
                                itemNameSnapshot = it.productName,
                                unitSnapshot = it.unitSnapshot,
                                quantity = it.quantity.toDecimalOrNull() ?: 0.0,
                                unitPrice = it.unitPrice.toDecimalOrNull() ?: 0.0,
                                discountPercent = if (it.discountPercent.isBlank()) 0.0 else (it.discountPercent.toDecimalOrNull() ?: 0.0)
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
            } catch (e: IllegalStateException) {
                errorMessage.value = e.message
                publish()
            } catch (e: Exception) {
                errorMessage.value = "تعذّر الحفظ: ${e.message ?: e.javaClass.simpleName}"
                publish()
            }
        }
    }

    /** Deletes a DRAFT invoice (a confirmed one is history and cannot be deleted). */
    fun onDeleteDraft() {
        if (isNew || status.value != InvoiceStatus.DRAFT) return
        viewModelScope.launch {
            runCatching { salesRepository.deleteDraftInvoice(invoiceId) }
                .onSuccess { isSaved.value = true; publish() }
                .onFailure { errorMessage.value = it.message ?: "تعذّر الحذف"; publish() }
        }
    }
}
