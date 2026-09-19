package com.inventorysmartai.app.presentation.datacenter.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.ImportJob
import com.inventorysmartai.app.domain.model.ImportJobStatus
import com.inventorysmartai.app.domain.repository.ImportRepository
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ImportHistoryFilter(val labelAr: String) {
    ALL("سجل الاستيراد"), REVIEW("قيد المراجعة"), ERRORS("الأخطاء"), COMPLETED("المقبولة");

    companion object {
        fun fromRouteArg(value: String?): ImportHistoryFilter = when (value) {
            "review" -> REVIEW
            "errors" -> ERRORS
            "completed" -> COMPLETED
            else -> ALL
        }
    }
}

/** Spec section 18 (Import History) doubles as the Data Center's section C ("العمليات"): rather
 *  than four separate screens for "Import history / Pending reviews / Errors / Accepted
 *  imports", this is one list with a filter driven by which tile was tapped — all four are
 *  genuinely the same job list viewed through a different lens, not different data. */
@HiltViewModel
class ImportHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val importRepository: ImportRepository
) : ViewModel() {

    val filter: ImportHistoryFilter = ImportHistoryFilter.fromRouteArg(savedStateHandle[Destination.ImportHistory.ARG_FILTER])

    private val _uiState = MutableStateFlow<UiState<List<ImportJob>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ImportJob>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            importRepository.observeJobs()
                .map { jobs -> jobs.filter { matchesFilter(it) } }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "تعذّر تحميل السجل") }
                .collect { jobs -> _uiState.value = if (jobs.isEmpty()) UiState.Empty() else UiState.Success(jobs) }
        }
    }

    private fun matchesFilter(job: ImportJob): Boolean = when (filter) {
        ImportHistoryFilter.ALL -> true
        ImportHistoryFilter.REVIEW -> job.status == ImportJobStatus.REVIEW_REQUIRED
        ImportHistoryFilter.ERRORS -> (job.errorRows ?: 0) > 0
        ImportHistoryFilter.COMPLETED -> job.status == ImportJobStatus.COMPLETED || job.status == ImportJobStatus.PARTIALLY_COMPLETED
    }
}
