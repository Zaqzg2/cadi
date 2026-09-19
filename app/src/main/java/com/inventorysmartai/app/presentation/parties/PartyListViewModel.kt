package com.inventorysmartai.app.presentation.parties

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.Customer
import com.inventorysmartai.app.domain.model.Supplier
import com.inventorysmartai.app.domain.repository.PartyRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PartyKind(val titleAr: String, val addLabel: String) {
    CUSTOMER("العملاء", "إضافة عميل"),
    SUPPLIER("الموردون", "إضافة مورّد")
}

data class PartyItemUi(val id: Long, val name: String, val phone: String?)

/** Data Center section B ("البيانات الأساسية") needed Customers/Suppliers to actually go
 *  somewhere — PartyRepository's create/list logic already existed from an earlier phase, only
 *  a screen was missing. Kept intentionally minimal (list + add, mirroring CatalogListScreen's
 *  branches/categories/units pattern) rather than full CRUD, since the spec only asks for this
 *  section to be "functional", not for a new customer/supplier management feature. */
@HiltViewModel
class PartyListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val partyRepository: PartyRepository
) : ViewModel() {

    val kind: PartyKind = when (savedStateHandle.get<String>(Destination.PartyList.ARG_KIND)) {
        "supplier" -> PartyKind.SUPPLIER
        else -> PartyKind.CUSTOMER
    }

    private val _uiState = MutableStateFlow<UiState<List<PartyItemUi>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PartyItemUi>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val flow = if (kind == PartyKind.SUPPLIER) {
                partyRepository.observeSuppliers().map { list -> list.map { PartyItemUi(it.id, it.name, it.phone) } }
            } else {
                partyRepository.observeCustomers().map { list -> list.map { PartyItemUi(it.id, it.name, it.phone) } }
            }
            flow.catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل القائمة") }
                .collect { items -> _uiState.value = if (items.isEmpty()) UiState.Empty() else UiState.Success(items) }
        }
    }

    fun addParty(name: String, phone: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (kind == PartyKind.SUPPLIER) {
                partyRepository.upsertSupplier(Supplier(name = name.trim(), phone = phone?.trim()?.ifBlank { null }))
            } else {
                partyRepository.upsertCustomer(Customer(name = name.trim(), phone = phone?.trim()?.ifBlank { null }))
            }
        }
    }
}
