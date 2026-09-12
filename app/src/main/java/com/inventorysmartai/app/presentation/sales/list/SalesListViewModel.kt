package com.inventorysmartai.app.presentation.sales.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalesListViewModel @Inject constructor(
    private val salesRepository: SalesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<SalesInvoice>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<SalesInvoice>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            salesRepository.observeInvoices()
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل فواتير البيع") }
                .collect { list ->
                    _uiState.value = if (list.isEmpty()) UiState.Empty("لا توجد فواتير بيع بعد") else UiState.Success(list)
                }
        }
    }
}
