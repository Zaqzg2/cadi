package com.inventorysmartai.app.presentation.purchases.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import com.inventorysmartai.app.domain.model.PurchaseStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PurchaseListViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<List<PurchaseRequest>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PurchaseRequest>>> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            combine(purchaseRepository.observeRequests(), query) { list, q ->
                val needle = q.trim().lowercase()
                if (needle.isEmpty()) list else list.filter { item -> item.requestNumber.lowercase().contains(needle) || item.supplierName.orEmpty().lowercase().contains(needle) }
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل طلبات الشراء") }
                .collect { list -> _uiState.value = if (list.isEmpty()) UiState.Empty("لا توجد طلبات شراء بعد") else UiState.Success(list) }
        }
    }

    fun onQueryChange(value: String) { query.value = value }
    fun onMessageShown() { _message.value = null }

    /** Only drafts can be deleted: anything else already moved stock and is part of the history. */
    fun canDelete(item: PurchaseRequest): Boolean = item.status != PurchaseStatus.RECEIVED && item.items.none { it.receivedQuantity > 0.0 }

    fun delete(item: PurchaseRequest) {
        if (!canDelete(item)) { _message.value = "لا يمكن حذف طلب تم استلام بضاعة عليه — يمكنك إلغاؤه من داخله"; return }
        viewModelScope.launch {
            runCatching { purchaseRepository.deleteRequest(item.id) }
                .onSuccess { _message.value = "تم الحذف" }
                .onFailure { _message.value = it.message ?: "تعذّر الحذف" }
        }
    }
}
