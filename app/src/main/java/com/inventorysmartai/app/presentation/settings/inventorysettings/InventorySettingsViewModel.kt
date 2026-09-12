package com.inventorysmartai.app.presentation.settings.inventorysettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventorySettingsUi(val nearExpiryWindowDays: Int = 30, val defaultLowStockThreshold: Double = 5.0)

@HiltViewModel
class InventorySettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<InventorySettingsUi> = combine(
        settingsRepository.observeNearExpiryWindowDays(),
        settingsRepository.observeDefaultLowStockThreshold()
    ) { days, threshold -> InventorySettingsUi(days, threshold) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventorySettingsUi())

    fun onNearExpiryWindowChanged(days: Int) {
        viewModelScope.launch { settingsRepository.setNearExpiryWindowDays(days) }
    }

    fun onDefaultLowStockThresholdChanged(value: Double) {
        viewModelScope.launch { settingsRepository.setDefaultLowStockThreshold(value) }
    }
}
