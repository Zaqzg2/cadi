package com.inventorysmartai.app.presentation.datacenter.manual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Category
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.model.UnitOfMeasure
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManualEntryState(
    val name: String = "",
    val itemNumber: String = "",
    val barcode: String = "",
    val minStock: String = "",
    val reorderPoint: String = "",
    val price: String = "",
    val openingQuantity: String = "",
    val categoryId: Long? = null,
    val unitId: Long? = null,
    val branchId: Long? = null,
    val hasExpiry: Boolean = false,
    val branches: List<Branch> = emptyList(),
    val categories: List<Category> = emptyList(),
    val units: List<UnitOfMeasure> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedProductId: Long? = null
)

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    catalogRepository: CatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        ManualEntryState(barcode = savedStateHandle.get<String>(Destination.ManualEntry.ARG_BARCODE).orEmpty())
    )
    val state: StateFlow<ManualEntryState> = _state.asStateFlow()

    init {
        viewModelScope.launch { catalogRepository.observeBranches().collect { b -> _state.update { it.copy(branches = b.filter { x -> x.isActive }) } } }
        viewModelScope.launch { catalogRepository.observeCategories().collect { c -> _state.update { it.copy(categories = c.filter { x -> x.isActive }) } } }
        viewModelScope.launch { catalogRepository.observeUnits().collect { u -> _state.update { it.copy(units = u.filter { x -> x.isActive }) } } }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onItemNumber(v: String) = _state.update { it.copy(itemNumber = v, error = null) }
    fun onBarcode(v: String) = _state.update { it.copy(barcode = v, error = null) }
    fun onMinStock(v: String) = _state.update { it.copy(minStock = v, error = null) }
    fun onReorderPoint(v: String) = _state.update { it.copy(reorderPoint = v, error = null) }
    fun onPrice(v: String) = _state.update { it.copy(price = v, error = null) }
    fun onOpeningQuantity(v: String) = _state.update { it.copy(openingQuantity = v, error = null) }
    fun onCategory(id: Long?) = _state.update { it.copy(categoryId = id) }
    fun onUnit(id: Long?) = _state.update { it.copy(unitId = id) }
    fun onBranch(id: Long?) = _state.update { it.copy(branchId = id) }
    fun onHasExpiry(v: Boolean) = _state.update { it.copy(hasExpiry = v) }
    fun consumeSaved() = _state.update { it.copy(savedProductId = null) }

    fun save() {
        val s = _state.value
        if (s.isSaving) return

        val name = s.name.trim()
        if (name.isEmpty()) return fail("اسم الصنف مطلوب")

        val minStock = parseNumber(s.minStock, 0.0) ?: return fail("الحد الأدنى غير صالح")
        val reorder = parseNumber(s.reorderPoint, 0.0) ?: return fail("نقطة إعادة الطلب غير صالحة")
        val price = if (s.price.isBlank()) null else (parseNumber(s.price, 0.0) ?: return fail("السعر غير صالح"))
        val opening = parseNumber(s.openingQuantity, 0.0) ?: return fail("الكمية الافتتاحية غير صالحة")
        if (minStock < 0 || reorder < 0 || opening < 0 || (price != null && price < 0)) return fail("لا يمكن إدخال قيم سالبة")
        if (opening > 0 && s.branchId == null) return fail("اختر الفرع لتسجيل الكمية الافتتاحية")

        val itemNumber = s.itemNumber.trim().ifEmpty { null }
        val barcode = s.barcode.trim().ifEmpty { null }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                // Duplicates are checked here because ProductRepository.upsert REPLACEs on the unique
                // barcode/item-number indexes — saving a duplicate would silently overwrite (and, through
                // the cascade, wipe the stock of) another product.
                if (barcode != null && productRepository.getByBarcode(barcode) != null) {
                    return@launch finishWithError("الباركود مسجّل مسبقًا لصنف آخر")
                }
                if (itemNumber != null && productRepository.getByItemNumber(itemNumber) != null) {
                    return@launch finishWithError("رقم الصنف مسجّل مسبقًا لصنف آخر")
                }
                val id = productRepository.createWithOpeningStock(
                    product = Product(
                        itemNumber = itemNumber,
                        barcode = barcode,
                        name = name,
                        categoryId = s.categoryId,
                        unitId = s.unitId,
                        minStock = minStock,
                        reorderPoint = reorder,
                        hasExpiry = s.hasExpiry,
                        defaultPrice = price
                    ),
                    branchId = s.branchId,
                    openingQuantity = opening
                )
                _state.update { it.copy(isSaving = false, savedProductId = id) }
            } catch (e: Exception) {
                finishWithError("تعذّر حفظ الصنف: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun fail(message: String) { _state.update { it.copy(error = message) } }
    private fun finishWithError(message: String) { _state.update { it.copy(isSaving = false, error = message) } }

    /** Accepts Arabic-Indic digits and the Arabic decimal/comma separators too. */
    private fun parseNumber(raw: String, default: Double): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return default
        val normalized = buildString {
            for (ch in t) append(
                when (ch) {
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    '\u066B', '\u066C', ',', '\u060C' -> '.'
                    else -> ch
                }
            )
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
