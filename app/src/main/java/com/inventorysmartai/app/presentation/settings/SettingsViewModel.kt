package com.inventorysmartai.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.domain.model.ThemeMode
import com.inventorysmartai.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiData(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useArabicIndicDigits: Boolean = false,
    val isDemoDataLoaded: Boolean = false,
    val demoDataJustLoaded: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val demoDataJustLoaded = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiData> = combine(
        settingsRepository.observeThemeMode(),
        settingsRepository.observeUseArabicIndicDigits(),
        settingsRepository.observeIsDemoDataLoaded(),
        demoDataJustLoaded
    ) { theme, digits, loaded, justLoaded -> SettingsUiData(theme, digits, loaded, justLoaded) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiData())

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onUseArabicIndicDigitsChanged(value: Boolean) {
        viewModelScope.launch { settingsRepository.setUseArabicIndicDigits(value) }
    }

    /** The one explicit, user-initiated action that writes demo rows into Room. */
    fun onLoadDemoData() {
        viewModelScope.launch {
            settingsRepository.loadDemoData()
            demoDataJustLoaded.value = true
        }
    }
}
