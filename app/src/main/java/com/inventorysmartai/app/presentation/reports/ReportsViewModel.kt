package com.inventorysmartai.app.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.repository.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReportType {
    INVENTORY, LOW_STOCK, ZERO_STOCK, EXPIRED, NEAR_EXPIRY, COUNTING, PURCHASES, SALES, GOALS, BRANCHES
}

data class ReportsData(
    val inventoryByStatus: Map<InventoryStatus, Int>,
    val salesByDay: List<Pair<Long, Double>>,
    val purchasesByDay: List<Pair<Long, Double>>
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportsRepository: ReportsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ReportsData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ReportsData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                reportsRepository.observeInventoryByStatus(),
                reportsRepository.observeSalesTotalsByDay(30),
                reportsRepository.observePurchaseTotalsByDay(30)
            ) { byStatus, sales, purchases -> ReportsData(byStatus, sales, purchases) }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل التقارير") }
                .collect { _uiState.value = UiState.Success(it) }
        }
    }
}
