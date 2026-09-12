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

    fun onAdd(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (kind) {
                CatalogKind.BRANCH -> catalogRepository.upsertBranch(Branch(name = name))
                CatalogKind.CATEGORY -> catalogRepository.upsertCategory(Category(name = name))
                CatalogKind.UNIT -> catalogRepository.upsertUnit(UnitOfMeasure(name = name))
            }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch {
            when (kind) {
                CatalogKind.BRANCH -> catalogRepository.deleteBranch(id)
                CatalogKind.CATEGORY -> catalogRepository.deleteCategory(id)
                CatalogKind.UNIT -> catalogRepository.deleteUnit(id)
            }
        }
    }
}
