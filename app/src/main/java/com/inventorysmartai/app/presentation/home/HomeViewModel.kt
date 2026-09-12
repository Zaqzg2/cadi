package com.inventorysmartai.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.Constants
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.DashboardSummary
import com.inventorysmartai.app.domain.usecase.GetDashboardSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDashboardSummary: GetDashboardSummaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<DashboardSummary>>(UiState.Loading)
    val uiState: StateFlow<UiState<DashboardSummary>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getDashboardSummary(Constants.RECENT_LIST_LIMIT)
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل بيانات الرئيسية") }
                .collect { summary -> _uiState.value = UiState.Success(summary) }
        }
    }
}
