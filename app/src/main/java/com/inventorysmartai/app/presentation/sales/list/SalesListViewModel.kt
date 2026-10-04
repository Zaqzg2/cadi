package com.inventorysmartai.app.presentation.sales.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.repository.SalesRepository
import com.inventorysmartai.app.domain.model.InvoiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalesListViewModel @Inject constructor(
    private val salesRepository: SalesRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<List<SalesInvoice>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<SalesInvoice>>> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            combine(salesRepository.observeInvoices(), query) { list, q ->
                val needle = q.trim().lowercase()
                if (needle.isEmpty()) list else list.filter { item -> item.invoiceNumber.lowercase().contains(needle) || item.customerName.orEmpty().lowercase().contains(needle) }
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل فواتير البيع") }
                .collect { list -> _uiState.value = if (list.isEmpty()) UiState.Empty("لا توجد فواتير بيع بعد") else UiState.Success(list) }
        }
    }

    fun onQueryChange(value: String) { query.value = value }
    fun onMessageShown() { _message.value = null }

    /** Only drafts can be deleted: anything else already moved stock and is part of the history. */
    fun canDelete(item: SalesInvoice): Boolean = item.status == InvoiceStatus.DRAFT

    fun delete(item: SalesInvoice) {
        if (!canDelete(item)) { _message.value = "لا يمكن حذف فاتورة مؤكدة — خصمت من المخزون"; return }
        viewModelScope.launch {
            runCatching { salesRepository.deleteDraftInvoice(item.id) }
                .onSuccess { _message.value = "تم الحذف" }
                .onFailure { _message.value = it.message ?: "تعذّر الحذف" }
        }
    }
}
