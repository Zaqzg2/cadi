package com.inventorysmartai.app.presentation.stockops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.repository.StockOperationRepository
import com.inventorysmartai.app.domain.stockops.StockOperation
import com.inventorysmartai.app.domain.stockops.StockOperationHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** سجل التحويل والمرتجعات: قائمة بحث فقط (التنفيذ في [StockOperationFormViewModel]). */
@HiltViewModel
class StockOperationsViewModel @Inject constructor(
    repository: StockOperationRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val uiState: StateFlow<UiState<List<StockOperation>>> =
        combine(repository.observeOperations(), query) { operations, q ->
            toState(operations.filter { StockOperationHistory.matches(it, q) })
        }
            .catch { e -> emit(UiState.Error(e.message ?: "تعذّر تحميل السجل")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private fun toState(items: List<StockOperation>): UiState<List<StockOperation>> =
        if (items.isEmpty()) UiState.Empty("لا توجد عمليات بعد") else UiState.Success(items)

    fun onQueryChange(value: String) { query.value = value }
}
