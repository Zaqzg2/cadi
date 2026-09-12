package com.inventorysmartai.app.presentation.purchases.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PurchaseListViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<PurchaseRequest>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PurchaseRequest>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            purchaseRepository.observeRequests()
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل طلبات الشراء") }
                .collect { list ->
                    _uiState.value = if (list.isEmpty()) UiState.Empty("لا توجد طلبات شراء بعد") else UiState.Success(list)
                }
        }
    }
}
