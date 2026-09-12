package com.inventorysmartai.app.presentation.inventory.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.AttachmentOwnerType
import com.inventorysmartai.app.domain.model.InventoryMovement
import com.inventorysmartai.app.domain.model.ProductStockSummary
import com.inventorysmartai.app.domain.repository.AttachmentRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailData(
    val summary: ProductStockSummary,
    val movements: List<InventoryMovement>,
    val attachmentCount: Int
) {
    val purchaseMovements get() = movements.filter { it.referenceType == "PURCHASE_REQUEST" }
    val saleMovements get() = movements.filter { it.referenceType == "SALES_INVOICE" }
    val countMovements get() = movements.filter { it.referenceType == "INVENTORY_COUNT" }
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    private val attachmentRepository: AttachmentRepository
) : ViewModel() {

    private val productId: Long = checkNotNull(savedStateHandle[Destination.ProductDetail.ARG_PRODUCT_ID])

    private val _uiState = MutableStateFlow<UiState<ProductDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ProductDetailData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                productRepository.observeProductDetail(productId),
                productRepository.observeMovements(productId),
                attachmentRepository.observeAttachments(AttachmentOwnerType.PRODUCT, productId)
            ) { summary, movements, attachments ->
                if (summary == null) UiState.Error("لم يتم العثور على الصنف")
                else UiState.Success(ProductDetailData(summary, movements, attachments.size))
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل بيانات الصنف") }
                .collect { _uiState.value = it }
        }
    }
}
