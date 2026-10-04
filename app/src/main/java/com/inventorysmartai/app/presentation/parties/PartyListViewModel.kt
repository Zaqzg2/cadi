@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.inventorysmartai.app.presentation.parties

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.common.toDecimalOrNull
import com.inventorysmartai.app.domain.model.Customer
import com.inventorysmartai.app.domain.model.Supplier
import com.inventorysmartai.app.domain.repository.PartyRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PartyKind(val titleAr: String, val addLabel: String) {
    CUSTOMER("العملاء", "إضافة عميل"),
    SUPPLIER("الموردون", "إضافة مورّد")
}

/** One customer or supplier flattened for the shared screen. [extra] is the opening balance (customer)
 *  or the notes (supplier) as text. */
data class PartyItemUi(
    val id: Long,
    val number: String?,
    val name: String,
    val phone: String?,
    val address: String?,
    val extra: String?
)

/** Everything the edit dialog can change. */
data class PartyForm(
    val number: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val extra: String = ""
)

/** A party that could not be deleted because documents use it → the screen offers to archive instead. */
data class BlockedParty(val id: Long, val name: String, val documents: Int)

@HiltViewModel
class PartyListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val partyRepository: PartyRepository
) : ViewModel() {

    val kind: PartyKind = when (savedStateHandle.get<String>(Destination.PartyList.ARG_KIND)) {
        "supplier" -> PartyKind.SUPPLIER
        else -> PartyKind.CUSTOMER
    }

    private val showArchived = MutableStateFlow(false)
    val archivedShown: StateFlow<Boolean> = showArchived.asStateFlow()
    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<List<PartyItemUi>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PartyItemUi>>> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _blocked = MutableStateFlow<BlockedParty?>(null)
    val blocked: StateFlow<BlockedParty?> = _blocked.asStateFlow()

    private var all: List<PartyItemUi> = emptyList()

    init {
        viewModelScope.launch {
            val source: Flow<List<PartyItemUi>> = showArchived.flatMapLatest { archived -> items(archived) }
            combine(source, query) { list, q ->
                all = list
                val needle = q.trim().lowercase()
                val filtered = if (needle.isEmpty()) list else list.filter {
                    it.name.lowercase().contains(needle) || it.phone.orEmpty().contains(needle) || it.number.orEmpty().lowercase().contains(needle)
                }
                filtered
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل القائمة") }
                .collect { items -> _uiState.value = if (items.isEmpty()) UiState.Empty() else UiState.Success(items) }
        }
    }

    private fun items(archived: Boolean): Flow<List<PartyItemUi>> =
        if (kind == PartyKind.SUPPLIER) {
            (if (archived) partyRepository.observeArchivedSuppliers() else partyRepository.observeSuppliers())
                .map { list -> list.map { PartyItemUi(it.id, it.supplierNumber, it.name, it.phone, it.address, it.notes) } }
        } else {
            (if (archived) partyRepository.observeArchivedCustomers() else partyRepository.observeCustomers())
                .map { list -> list.map { PartyItemUi(it.id, it.customerNumber, it.name, it.phone, it.address, it.openingBalance.takeIf { b -> b != 0.0 }?.toString()) } }
        }

    fun onQueryChange(value: String) { query.value = value }
    fun toggleArchived() { showArchived.value = !showArchived.value }
    fun onMessageShown() { _message.value = null }
    fun dismissBlocked() { _blocked.value = null }

    fun formFor(id: Long?): PartyForm {
        val item = all.firstOrNull { it.id == id } ?: return PartyForm()
        return PartyForm(item.number.orEmpty(), item.name, item.phone.orEmpty(), item.address.orEmpty(), item.extra.orEmpty())
    }

    /** Creates ([id] null) or edits ([id] set) — with validation and duplicate-name protection. */
    fun save(id: Long?, form: PartyForm) {
        val name = form.name.trim()
        if (name.isEmpty()) { _message.value = "الاسم مطلوب"; return }
        val duplicate = all.any { it.id != id && it.name.trim().equals(name, ignoreCase = true) }
        if (duplicate) { _message.value = "الاسم \"$name\" موجود مسبقًا"; return }
        val balance = if (kind == PartyKind.CUSTOMER && form.extra.isNotBlank()) {
            form.extra.toDecimalOrNull() ?: run { _message.value = "الرصيد الافتتاحي غير صالح"; return }
        } else 0.0

        viewModelScope.launch {
            runCatching {
                val number = form.number.trim().ifBlank { null }
                val phone = form.phone.trim().ifBlank { null }
                val address = form.address.trim().ifBlank { null }
                if (kind == PartyKind.SUPPLIER) {
                    val supplier = Supplier(id = id ?: 0L, supplierNumber = number, name = name, phone = phone, address = address, notes = form.extra.trim().ifBlank { null })
                    if (id == null) partyRepository.upsertSupplier(supplier) else partyRepository.updateSupplier(supplier)
                } else {
                    val customer = Customer(id = id ?: 0L, customerNumber = number, name = name, phone = phone, address = address, openingBalance = balance)
                    if (id == null) partyRepository.upsertCustomer(customer) else partyRepository.updateCustomer(customer)
                }
            }.onFailure {
                // the unique customerNumber / supplierNumber index
                _message.value = "تعذّر الحفظ — قد يكون الرقم \"${form.number.trim()}\" مستخدمًا لطرف آخر"
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val name = all.firstOrNull { it.id == id }?.name.orEmpty()
            runCatching {
                if (kind == PartyKind.SUPPLIER) partyRepository.deleteSupplier(id) else partyRepository.deleteCustomer(id)
            }.onSuccess { result ->
                if (result.deleted) _message.value = "تم حذف \"$name\"" else _blocked.value = BlockedParty(id, name, result.blockedByDocuments)
            }.onFailure { _message.value = "تعذّر الحذف: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (kind == PartyKind.SUPPLIER) partyRepository.setSupplierActive(id, active) else partyRepository.setCustomerActive(id, active)
            }.onSuccess { _message.value = if (active) "تمت الاستعادة" else "تمت الأرشفة" }
                .onFailure { _message.value = "تعذّر التنفيذ: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    fun archiveBlocked() {
        val b = _blocked.value ?: return
        _blocked.value = null
        setActive(b.id, false)
    }
}
