package com.inventorysmartai.app.presentation.stockops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.toDecimalOrNull
import com.inventorysmartai.app.data.repository.InsufficientStockException
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Customer
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.Supplier
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.PartyRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.StockOperationRepository
import com.inventorysmartai.app.domain.stockops.InvalidStockOperationException
import com.inventorysmartai.app.domain.stockops.StockOperationLine
import com.inventorysmartai.app.domain.stockops.StockOperationRequest
import com.inventorysmartai.app.domain.stockops.StockOperationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** سطر في النموذج: صنف وكمية (نصّ كما يكتبه المستخدم حتى لحظة التنفيذ). */
data class OperationLineRow(val localId: Long, val productId: Long? = null, val quantity: String = "")

data class StockOperationFormState(
    val type: StockOperationType = StockOperationType.TRANSFER,
    val branches: List<Branch> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val products: List<Product> = emptyList(),
    val fromBranchId: Long? = null,
    val toBranchId: Long? = null,
    val partyId: Long? = null,
    val lines: List<OperationLineRow> = listOf(OperationLineRow(localId = 1L)),
    val notes: String = "",
    val submitting: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
) {
    /** الأسطر المكتملة (صنف + كمية موجبة). */
    val readyLines: List<OperationLineRow>
        get() = lines.filter { it.productId != null && (it.quantity.toDecimalOrNull() ?: 0.0) > 0.0 }

    /** زر التنفيذ يُفعَّل حين يكتمل الطلب؛ ما يبقى (نقص الرصيد مثلًا) يكشفه التنفيذ نفسه. */
    val canSubmit: Boolean
        get() = !submitting && fromBranchId != null && readyLines.isNotEmpty() &&
            (type != StockOperationType.TRANSFER || (toBranchId != null && toBranchId != fromBranchId))
}

@HiltViewModel
class StockOperationFormViewModel @Inject constructor(
    private val repository: StockOperationRepository,
    productRepository: ProductRepository,
    catalogRepository: CatalogRepository,
    partyRepository: PartyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StockOperationFormState())
    val state: StateFlow<StockOperationFormState> = _state.asStateFlow()
    private var nextLineId = 2L

    init {
        viewModelScope.launch {
            catalogRepository.observeBranches().catch { }.collect { list ->
                val active = list.filter { it.isActive }
                // الفرع المرسل يبدأ بأول فرع نشط كي لا يضطر المستخدم لاختياره في الحالة الشائعة (فرع واحد).
                _state.update { s -> s.copy(branches = active, fromBranchId = s.fromBranchId ?: active.firstOrNull()?.id) }
            }
        }
        viewModelScope.launch { productRepository.observeProducts().catch { }.collect { list -> _state.update { it.copy(products = list) } } }
        viewModelScope.launch { partyRepository.observeCustomers().catch { }.collect { list -> _state.update { it.copy(customers = list) } } }
        viewModelScope.launch { partyRepository.observeSuppliers().catch { }.collect { list -> _state.update { it.copy(suppliers = list) } } }
    }

    fun onTypeChange(type: StockOperationType) = _state.update { it.copy(type = type, partyId = null, error = null) }
    fun onFromBranch(id: Long) = _state.update { it.copy(fromBranchId = id, error = null) }
    fun onToBranch(id: Long) = _state.update { it.copy(toBranchId = id, error = null) }
    fun onParty(id: Long?) = _state.update { it.copy(partyId = id) }
    fun onNotes(text: String) = _state.update { it.copy(notes = text) }

    fun onProduct(localId: Long, productId: Long) =
        _state.update { s -> s.copy(error = null, lines = s.lines.map { if (it.localId == localId) it.copy(productId = productId) else it }) }

    fun onQuantity(localId: Long, text: String) =
        _state.update { s -> s.copy(error = null, lines = s.lines.map { if (it.localId == localId) it.copy(quantity = text) else it }) }

    fun onAddLine() {
        val id = nextLineId++
        _state.update { it.copy(lines = it.lines + OperationLineRow(localId = id)) }
    }

    fun onRemoveLine(localId: Long) {
        val replacement = nextLineId++
        _state.update { s ->
            val remaining = s.lines.filterNot { it.localId == localId }
            // النموذج يحتفظ دائمًا بسطر واحد على الأقل، فارغًا إن حُذف الأخير.
            s.copy(lines = remaining.ifEmpty { listOf(OperationLineRow(localId = replacement)) })
        }
    }

    fun submit() {
        val s = _state.value
        val branchId = s.fromBranchId
        if (!s.canSubmit || branchId == null) return

        val partyLabel = when (s.type) {
            StockOperationType.SALES_RETURN -> s.customers.firstOrNull { it.id == s.partyId }?.let { "العميل: ${it.name}" }
            StockOperationType.PURCHASE_RETURN -> s.suppliers.firstOrNull { it.id == s.partyId }?.let { "المورد: ${it.name}" }
            StockOperationType.TRANSFER -> null
        }
        val request = StockOperationRequest(
            type = s.type,
            branchId = branchId,
            toBranchId = s.toBranchId.takeIf { s.type == StockOperationType.TRANSFER },
            partyLabel = partyLabel,
            lines = s.readyLines.mapNotNull { row ->
                val productId = row.productId ?: return@mapNotNull null
                val quantity = row.quantity.toDecimalOrNull() ?: return@mapNotNull null
                StockOperationLine(productId, quantity)
            },
            notes = s.notes
        )

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                repository.execute(request)
                _state.update { it.copy(submitting = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStockOperationException) {
                fail(e.message ?: "بيانات العملية غير صالحة")
            } catch (e: InsufficientStockException) {
                fail("الرصيد لا يكفي:\n${e.message}")
            } catch (e: Exception) {
                fail(e.message ?: "تعذّر تنفيذ العملية")
            }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(submitting = false, error = message) }
}
