package com.inventorysmartai.app.presentation.datacenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.domain.model.ImportSourceType
import com.inventorysmartai.app.domain.repository.ImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataCenterViewModel @Inject constructor(
    private val importRepository: ImportRepository
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    /** Phase 1: no parser is wired up (see domain/importing), so this only records that the
     *  tile was tapped — real parsing/matching lands in a later phase. */
    fun onImportTileTapped(sourceType: ImportSourceType) {
        viewModelScope.launch {
            importRepository.createJob(sourceType, fileName = null)
            _snackbarMessage.value = "استيراد ${sourceLabel(sourceType)} سيتم تفعيله في مرحلة قادمة"
        }
    }

    fun onExternalServiceTapped(serviceName: String) {
        _snackbarMessage.value = "التكامل مع $serviceName قادم قريباً"
    }

    fun onSnackbarShown() {
        _snackbarMessage.value = null
    }

    private fun sourceLabel(type: ImportSourceType): String = when (type) {
        ImportSourceType.EXCEL -> "Excel"
        ImportSourceType.CSV -> "CSV"
        ImportSourceType.PDF -> "PDF"
        ImportSourceType.IMAGE -> "الصور"
        ImportSourceType.CAMERA -> "الكاميرا"
        ImportSourceType.BARCODE -> "الباركود"
        ImportSourceType.MANUAL -> "الإدخال اليدوي"
    }
}
