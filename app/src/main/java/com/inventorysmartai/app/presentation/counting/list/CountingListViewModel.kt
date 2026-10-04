package com.inventorysmartai.app.presentation.counting.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.InventoryCount
import com.inventorysmartai.app.domain.repository.CountingRepository
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.domain.model.CountStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CountingListViewModel @Inject constructor(
    private val countingRepository: CountingRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<List<InventoryCount>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<InventoryCount>>> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            combine(countingRepository.observeCounts(), query) { list, q ->
                val needle = q.trim().lowercase()
                if (needle.isEmpty()) list else list.filter { item -> item.branchName.orEmpty().lowercase().contains(needle) || Formatters.formatDate(item.countDate).contains(needle) }
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل عمليات الجرد") }
                .collect { list -> _uiState.value = if (list.isEmpty()) UiState.Empty("لا توجد عمليات جرد بعد") else UiState.Success(list) }
        }
    }

    fun onQueryChange(value: String) { query.value = value }
    fun onMessageShown() { _message.value = null }

    /** Only drafts can be deleted: anything else already moved stock and is part of the history. */
    fun canDelete(item: InventoryCount): Boolean = item.status == CountStatus.DRAFT || item.status == CountStatus.IN_PROGRESS

    fun delete(item: InventoryCount) {
        if (!canDelete(item)) { _message.value = "لا يمكن حذف جرد مكتمل — صحّح المخزون"; return }
        viewModelScope.launch {
            runCatching { countingRepository.deleteDraftCount(item.id) }
                .onSuccess { _message.value = "تم الحذف" }
                .onFailure { _message.value = it.message ?: "تعذّر الحذف" }
        }
    }
}
