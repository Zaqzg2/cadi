package com.inventorysmartai.app.presentation.purchases.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.common.toDecimalOrNull
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.PurchaseReceipt
import com.inventorysmartai.app.domain.model.PurchaseReceiptLine
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

data class PurchaseItemRow(
    val productId: Long,
    val productName: String,
    val currentStock: Double,
    val requestedQuantity: String,
    val receivedQuantity: Double = 0.0,
    /** Carried through saves so editing a request never wipes an approval decision or a line note. */
    val approvedQuantity: Double? = null,
    val notes: String? = null
) {
    val remaining: Double get() = ((requestedQuantity.toDecimalOrNull() ?: 0.0) - receivedQuantity).coerceAtLeast(0.0)
}

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
    val isSaved: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val removedMessage: String? = null
) {
    /** Once stock has been received (or the request is closed) the lines are history. */
    val locked: Boolean get() = status == PurchaseStatus.RECEIVED || status == PurchaseStatus.CANCELLED
    val canReceive: Boolean get() = !isNew && !locked && items.any { it.remaining > 0.0 }
    val hasReceipts: Boolean get() = items.any { it.receivedQuantity > 0.0 }
}

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
    private var totalStock: Map<Long, Double> = emptyMap()
    private val branchId = MutableStateFlow<Long?>(null)
    private val supplierId = MutableStateFlow<Long?>(null)
    private val status = MutableStateFlow(PurchaseStatus.DRAFT)
    private val requestNumber = MutableStateFlow("")
    private val items = MutableStateFlow<List<PurchaseItemRow>>(emptyList())
    private val isSaved = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val removedMessage = MutableStateFlow<String?>(null)
    private var lastRemoved: List<Pair<Int, PurchaseItemRow>> = emptyList()
    private var existing: PurchaseRequest? = null

    private val _uiState = MutableStateFlow<UiState<PurchaseDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<PurchaseDetailData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            branches.value = catalogRepository.observeBranches().first().filter { it.isActive }
            suppliers.value = partyRepository.observeSuppliers().first()
            val summaries = productRepository.observeProductsWithStock().first()
            products.value = summaries.map { it.product }
            totalStock = summaries.associate { it.product.id to it.totalQuantity }
            if (!isNew) {
                purchaseRepository.observeRequest(requestId).first()?.let { applyExisting(it) }
            } else {
                branchId.value = branches.value.firstOrNull()?.id
                requestNumber.value = "PR-${System.currentTimeMillis().toString().takeLast(6)}"
            }
            publish()
        }
    }

    private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    private fun applyExisting(request: PurchaseRequest) {
        existing = request
        branchId.value = request.branchId
        supplierId.value = request.supplierId
        status.value = request.status
        requestNumber.value = request.requestNumber
        items.value = request.items.map {
            PurchaseItemRow(
                it.productId, it.productName.orEmpty(), it.currentStockSnapshot, plain(it.requestedQuantity),
                it.receivedQuantity, it.approvedQuantity, it.notes
            )
        }
    }

    private fun publish() {
        _uiState.value = UiState.Success(
            PurchaseDetailData(
                isNew, branches.value, suppliers.value, products.value, branchId.value, supplierId.value, status.value,
                requestNumber.value, items.value, isSaved.value, message.value, errorMessage.value, removedMessage.value
            )
        )
    }

    fun onBranchSelected(id: Long?) { if (id != null) { branchId.value = id; publish() } }
    fun onSupplierSelected(id: Long?) { supplierId.value = id; publish() }
    fun onMessageShown() { message.value = null; publish() }
    fun onErrorShown() { errorMessage.value = null; publish() }
    fun onRemovedMessageShown() { removedMessage.value = null; publish() }

    private fun locked() = status.value == PurchaseStatus.RECEIVED || status.value == PurchaseStatus.CANCELLED

    private fun newRow(product: Product) = PurchaseItemRow(product.id, product.name, totalStock[product.id] ?: 0.0, "")

    fun onAddProducts(ids: Set<Long>) {
        if (locked()) return
        val toAdd = ids.filter { id -> items.value.none { it.productId == id } }.mapNotNull { id -> products.value.find { it.id == id } }
        if (toAdd.isEmpty()) return
        items.value = items.value + toAdd.map(::newRow)
        message.value = "تمت إضافة ${toAdd.size} صنف — أدخل الكميات"
        publish()
    }

    fun onBarcodeScanned(code: String) {
        if (locked()) return
        val clean = code.trim()
        val product = products.value.find { it.barcode == clean } ?: products.value.find { it.itemNumber == clean }
        if (product == null) { message.value = "لا يوجد صنف بالباركود $clean"; publish(); return }
        if (items.value.any { it.productId == product.id }) {
            message.value = "«${product.name}» مضاف مسبقًا"
        } else {
            items.value = items.value + newRow(product)
            message.value = "أُضيف «${product.name}» — أدخل الكمية"
        }
        publish()
    }

    fun onQuantityChange(productId: Long, value: String) {
        if (locked()) return
        items.value = items.value.map { if (it.productId == productId) it.copy(requestedQuantity = value) else it }
        publish()
    }

    fun onRemoveItems(ids: Set<Long>) {
        if (locked()) return
        // A line that already received stock cannot disappear — it would orphan the receipt.
        val protectedNames = items.value.filter { it.productId in ids && it.receivedQuantity > 0.0 }.map { it.productName }
        val removable = ids - items.value.filter { it.receivedQuantity > 0.0 }.map { it.productId }.toSet()
        if (protectedNames.isNotEmpty()) message.value = "لا يمكن إزالة صنف تم استلامه: ${protectedNames.joinToString("، ")}"
        val removed = items.value.withIndex().filter { it.value.productId in removable }.map { it.index to it.value }
        if (removed.isEmpty()) { publish(); return }
        lastRemoved = removed
        items.value = items.value.filterNot { it.productId in removable }
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

    private fun validate(): String? {
        if (items.value.isEmpty()) return "أضف صنفًا واحدًا على الأقل"
        items.value.forEachIndexed { i, row ->
            val qty = row.requestedQuantity.toDecimalOrNull()
            if (qty == null || qty <= 0.0) return "السطر ${i + 1} (${row.productName}): الكمية يجب أن تكون أكبر من صفر"
        }
        return null
    }

    fun onSave(newStatus: PurchaseStatus) {
        if (locked()) return
        val branch = branchId.value ?: run { errorMessage.value = "اختر الفرع"; publish(); return }
        // A draft may be saved half-filled; sending it to the supplier may not.
        if (newStatus != PurchaseStatus.DRAFT) validate()?.let { errorMessage.value = it; publish(); return }
        viewModelScope.launch {
            try {
                val base = existing ?: PurchaseRequest(requestNumber = requestNumber.value, supplierId = null, branchId = branch, requestDate = System.currentTimeMillis(), status = newStatus)
                purchaseRepository.saveRequest(
                    base.copy(
                        id = if (isNew) 0L else requestId,
                        requestNumber = requestNumber.value,
                        supplierId = supplierId.value,
                        branchId = branch,
                        status = newStatus,
                        items = items.value.map {
                            PurchaseRequestItem(
                                productId = it.productId,
                                currentStockSnapshot = it.currentStock,
                                requestedQuantity = it.requestedQuantity.toDecimalOrNull() ?: 0.0,
                                approvedQuantity = it.approvedQuantity,
                                notes = it.notes
                            )
                        }
                    )
                )
                status.value = newStatus
                isSaved.value = true
            } catch (e: Exception) {
                errorMessage.value = "تعذّر الحفظ: ${e.message ?: e.javaClass.simpleName}"
            }
            publish()
        }
    }

    /**
     * Records a receipt for the quantities in [quantities] (productId -> received NOW). Supports partial
     * deliveries. Stock moves inside PurchaseRepository.receive(); only the STATUS is updated afterwards
     * (never re-saving the lines — that used to reset the received quantities to zero).
     */
    fun onReceive(quantities: Map<Long, Double>) {
        if (isNew || locked()) return
        val branch = branchId.value ?: return
        val lines = quantities.filterValues { it > 0.0 }.map { (productId, qty) -> PurchaseReceiptLine(productId = productId, quantity = qty) }
        if (lines.isEmpty()) { message.value = "أدخل كمية مستلمة واحدة على الأقل"; publish(); return }
        viewModelScope.launch {
            try {
                purchaseRepository.receive(
                    PurchaseReceipt(purchaseRequestId = requestId, supplierId = supplierId.value, branchId = branch, receivedDate = System.currentTimeMillis()),
                    lines
                )
                val fresh = purchaseRepository.observeRequest(requestId).first()
                if (fresh != null) {
                    existing = fresh
                    items.value = fresh.items.map {
                        PurchaseItemRow(it.productId, it.productName.orEmpty(), it.currentStockSnapshot, plain(it.requestedQuantity), it.receivedQuantity, it.approvedQuantity, it.notes)
                    }
                    val complete = fresh.items.all { it.receivedQuantity >= it.requestedQuantity }
                    if (complete) {
                        purchaseRepository.updateStatus(requestId, PurchaseStatus.RECEIVED)
                        status.value = PurchaseStatus.RECEIVED
                        message.value = "تم استلام الطلب كاملًا وتحديث المخزون"
                    } else {
                        message.value = "تم تسجيل الاستلام الجزئي وتحديث المخزون"
                    }
                }
            } catch (e: Exception) {
                errorMessage.value = "تعذّر تسجيل الاستلام: ${e.message ?: e.javaClass.simpleName}"
            }
            publish()
        }
    }

    fun onCancelRequest() {
        if (isNew || locked()) return
        viewModelScope.launch {
            runCatching { purchaseRepository.updateStatus(requestId, PurchaseStatus.CANCELLED) }
                .onSuccess { status.value = PurchaseStatus.CANCELLED; message.value = "تم إلغاء الطلب" }
                .onFailure { errorMessage.value = it.message ?: "تعذّر الإلغاء" }
            publish()
        }
    }

    fun onDelete() {
        if (isNew) return
        viewModelScope.launch {
            runCatching { purchaseRepository.deleteRequest(requestId) }
                .onSuccess { isSaved.value = true }
                .onFailure { errorMessage.value = it.message ?: "تعذّر الحذف" }
            publish()
        }
    }
}
