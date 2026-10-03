package com.inventorysmartai.app.presentation.settings.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.Category
import com.inventorysmartai.app.domain.model.UnitOfMeasure
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogItemUi(val id: Long, val name: String, val secondary: String? = null)

enum class CatalogKind(val titleAr: String, val addLabel: String) {
    BRANCH("الفروع", "إضافة فرع"),
    CATEGORY("التصنيفات", "إضافة تصنيف"),
    UNIT("الوحدات", "إضافة وحدة")
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    val kind: CatalogKind = when (savedStateHandle.get<String>(Destination.SettingsCatalog.ARG_TYPE)) {
        "category" -> CatalogKind.CATEGORY
        "unit" -> CatalogKind.UNIT
        else -> CatalogKind.BRANCH
    }

    private val _uiState = MutableStateFlow<UiState<List<CatalogItemUi>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<CatalogItemUi>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val flow = when (kind) {
                CatalogKind.BRANCH -> catalogRepository.observeBranches().map { list -> list.map { CatalogItemUi(it.id, it.name, it.address) } }
                CatalogKind.CATEGORY -> catalogRepository.observeCategories().map { list -> list.map { CatalogItemUi(it.id, it.name) } }
                CatalogKind.UNIT -> catalogRepository.observeUnits().map { list -> list.map { CatalogItemUi(it.id, it.name, it.symbol) } }
            }
            flow
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر التحميل") }
                .collect { list -> _uiState.value = if (list.isEmpty()) UiState.Empty("لا توجد عناصر بعد") else UiState.Success(list) }
        }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun onMessageShown() { _message.value = null }

    /** Names currently listed, for duplicate checks (compared trimmed + case-insensitive). */
    private fun currentNames(exceptId: Long? = null): List<String> =
        ((_uiState.value as? UiState.Success)?.data ?: emptyList())
            .filter { it.id != exceptId }
            .map { it.name.trim().lowercase() }

    fun onAdd(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) { _message.value = "الاسم مطلوب"; return }
        if (clean.lowercase() in currentNames()) { _message.value = "الاسم \"$clean\" موجود مسبقًا"; return }
        viewModelScope.launch {
            runCatching {
                when (kind) {
                    CatalogKind.BRANCH -> catalogRepository.upsertBranch(Branch(name = clean))
                    CatalogKind.CATEGORY -> catalogRepository.upsertCategory(Category(name = clean))
                    CatalogKind.UNIT -> catalogRepository.upsertUnit(UnitOfMeasure(name = clean))
                }
            }.onFailure { _message.value = "تعذّرت الإضافة: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    fun onRename(id: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) { _message.value = "الاسم مطلوب"; return }
        if (clean.lowercase() in currentNames(exceptId = id)) { _message.value = "الاسم \"$clean\" موجود مسبقًا"; return }
        viewModelScope.launch {
            runCatching {
                when (kind) {
                    CatalogKind.BRANCH -> catalogRepository.renameBranch(id, clean)
                    CatalogKind.CATEGORY -> catalogRepository.renameCategory(id, clean)
                    CatalogKind.UNIT -> catalogRepository.renameUnit(id, clean)
                }
            }.onFailure { _message.value = "تعذّر التعديل: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    /** How many records depend on this item — used by the delete confirmation text. */
    suspend fun usageCount(id: Long): Int = runCatching {
        when (kind) {
            CatalogKind.BRANCH -> catalogRepository.branchUsageCount(id)
            CatalogKind.CATEGORY -> catalogRepository.categoryUsageCount(id)
            CatalogKind.UNIT -> catalogRepository.unitUsageCount(id)
        }
    }.getOrDefault(0)

    fun onDelete(id: Long) {
        viewModelScope.launch {
            runCatching {
                when (kind) {
                    CatalogKind.BRANCH -> catalogRepository.deleteBranch(id)
                    CatalogKind.CATEGORY -> catalogRepository.deleteCategory(id)
                    CatalogKind.UNIT -> catalogRepository.deleteUnit(id)
                }
            }.onFailure {
                // e.g. a branch referenced by purchases/sales/counts (FK RESTRICT) — used to crash the app.
                _message.value = "لا يمكن الحذف لأن هذا العنصر مرتبط بعمليات سابقة (مشتريات / مبيعات / جرد)"
            }
        }
    }
}
