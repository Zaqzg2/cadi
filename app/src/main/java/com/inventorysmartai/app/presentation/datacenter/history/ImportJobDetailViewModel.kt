package com.inventorysmartai.app.presentation.datacenter.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.importing.ImportReviewManager
import com.inventorysmartai.app.domain.model.ImportJob
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.repository.ImportRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportJobDetailUi(val job: ImportJob, val rows: List<ImportRow>)

/** Spec section 18: "Open an import to see its detailed report" — read-only, no row actions
 *  (those only make sense for a REVIEW_REQUIRED job, which is the live [ImportReviewScreen]/
 *  [com.inventorysmartai.app.presentation.datacenter.importflow.ImportFlowViewModel] instead). */
@HiltViewModel
class ImportJobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val importRepository: ImportRepository,
    private val importReviewManager: ImportReviewManager
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<Long>(Destination.ImportJobDetail.ARG_JOB_ID) ?: -1L

    private val _uiState = MutableStateFlow<UiState<ImportJobDetailUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<ImportJobDetailUi>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(importRepository.observeJob(jobId), importReviewManager.observeRows(jobId)) { job, rows -> job to rows }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل تفاصيل الاستيراد") }
                .collect { (job, rows) ->
                    _uiState.value = if (job == null) UiState.Error("لم يتم العثور على عملية الاستيراد") else UiState.Success(ImportJobDetailUi(job, rows))
                }
        }
    }
}
