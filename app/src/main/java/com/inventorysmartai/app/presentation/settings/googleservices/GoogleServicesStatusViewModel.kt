package com.inventorysmartai.app.presentation.settings.googleservices

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.BuildConfig
import com.inventorysmartai.app.data.google.GoogleAuthCancelledException
import com.inventorysmartai.app.data.google.GoogleAuthManager
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.domain.repository.GoogleAuthRepository
import com.inventorysmartai.app.domain.repository.GoogleServiceStatus
import com.inventorysmartai.app.domain.repository.GoogleServiceStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoogleServicesStatusUiState(
    val isLoading: Boolean = true,
    val isConnecting: Boolean = false,
    val linked: Boolean = false,
    val status: GoogleServiceStatus? = null,
    val errorMessage: String? = null
)

/** Phase 4 spec's "SERVICE STATUS" screen. */
@HiltViewModel
class GoogleServicesStatusViewModel @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val googleAuthRepository: GoogleAuthRepository,
    private val googleServiceStatusRepository: GoogleServiceStatusRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GoogleServicesStatusUiState())
    val state: StateFlow<GoogleServicesStatusUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh(verifyGemini: Boolean = false) {
        viewModelScope.launch {
            update { it.copy(isLoading = true, errorMessage = null) }
            val authStatus = googleAuthRepository.getStatus().getOrNull()
            val serviceStatus = googleServiceStatusRepository.getStatus(verifyGemini).getOrNull()
            update { it.copy(isLoading = false, linked = authStatus?.linked ?: false, status = serviceStatus) }
        }
    }

    /** [activity] is used only for the duration of this one call (to launch Google's consent UI
     *  if needed) — never retained, so this does not leak the Activity past its own lifecycle. */
    fun connect(activity: ComponentActivity) {
        viewModelScope.launch {
            update { it.copy(isConnecting = true, errorMessage = null) }
            runCatching {
                val serverAuthCode = googleAuthManager.requestServerAuthCode(activity, BuildConfig.GOOGLE_BACKEND_SERVER_CLIENT_ID)
                googleAuthRepository.completeLinking(serverAuthCode).getOrThrow()
            }.onSuccess {
                update { it.copy(isConnecting = false) }
                refresh()
            }.onFailure { e ->
                update { it.copy(isConnecting = false, errorMessage = mapError(e)) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            googleAuthRepository.unlink()
            refresh()
        }
    }

    fun consumeError() = update { it.copy(errorMessage = null) }

    private fun mapError(e: Throwable): String = when (e) {
        is GoogleAuthCancelledException -> e.message ?: "تم إلغاء العملية"
        is BackendFailure -> e.messageAr
        else -> "تعذّر ربط حساب Google، يرجى المحاولة مرة أخرى"
    }

    private inline fun update(block: (GoogleServicesStatusUiState) -> GoogleServicesStatusUiState) {
        _state.value = block(_state.value)
    }
}
