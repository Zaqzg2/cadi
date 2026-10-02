package com.inventorysmartai.app.presentation.datacenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Outcome of a barcode scan: [productId] is the matching product, or null when none matches. */
data class BarcodeScanResult(val barcode: String, val productId: Long?)

@HiltViewModel
class DataCenterViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _scanResult = MutableStateFlow<BarcodeScanResult?>(null)
    val scanResult: StateFlow<BarcodeScanResult?> = _scanResult.asStateFlow()

    fun onBarcodeScanned(rawValue: String) {
        val barcode = rawValue.trim()
        if (barcode.isEmpty()) {
            _snackbarMessage.value = "لم يتم التعرّف على الباركود، حاول مرة أخرى"
            return
        }
        viewModelScope.launch {
            val product = runCatching { productRepository.getByBarcode(barcode) }.getOrNull()
            _scanResult.value = BarcodeScanResult(barcode, product?.id)
        }
    }

    fun onScanFailed(message: String?) {
        _snackbarMessage.value = "تعذّر تشغيل ماسح الباركود" + (message?.let { ": $it" } ?: "")
    }

    fun onScanResultHandled() { _scanResult.value = null }

    fun onExternalServiceTapped(serviceName: String) {
        _snackbarMessage.value = "التكامل مع $serviceName قادم قريباً"
    }

    fun onSnackbarShown() { _snackbarMessage.value = null }
}
