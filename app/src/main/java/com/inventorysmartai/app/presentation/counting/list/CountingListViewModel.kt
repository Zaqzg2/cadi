package com.inventorysmartai.app.presentation.counting.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.InventoryCount
import com.inventorysmartai.app.domain.repository.CountingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CountingListViewModel @Inject constructor(
    private val countingRepository: CountingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<InventoryCount>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<InventoryCount>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            countingRepository.observeCounts()
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل عمليات الجرد") }
                .collect { list ->
                    _uiState.value = if (list.isEmpty()) UiState.Empty("لا توجد عمليات جرد بعد") else UiState.Success(list)
                }
        }
    }
}
