package com.inventorysmartai.app.presentation.settings.googleservices

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.ApiException
import com.inventorysmartai.app.BuildConfig
import com.inventorysmartai.app.data.google.GoogleAuthCancelledException
import com.inventorysmartai.app.data.backend.BackendConfig
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

    /** [verifyAi] = true spends one tiny real AI request on the server to prove a provider key and model work. */
    fun refresh(verifyAi: Boolean = false) {
        viewModelScope.launch {
            update { it.copy(isLoading = true, errorMessage = null) }
            val authStatus = googleAuthRepository.getStatus().getOrNull()
            val statusResult = googleServiceStatusRepository.getStatus(verifyAi)
            val serviceStatus = statusResult.getOrNull()
            // A failed status call is the most useful thing to tell the person: it is usually "the server is asleep",
            // "wrong URL" or "wrong app key", each of which has its own message.
            val failure = statusResult.exceptionOrNull()
            update {
                it.copy(
                    isLoading = false,
                    linked = authStatus?.linked ?: false,
                    status = serviceStatus,
                    errorMessage = failure?.let { f -> mapError(f) }
                )
            }
        }
    }

    /** [activity] is used only for the duration of this one call (to launch Google's consent UI
     *  if needed) — never retained, so this does not leak the Activity past its own lifecycle. */
    fun connect(activity: ComponentActivity) {
        BackendConfig.configurationProblem()?.let { problem ->
            update { it.copy(errorMessage = problem) }
            return
        }
        // Fail fast with the real reason instead of letting Google reject a placeholder client id with an
        // opaque error that the generic message below would hide.
        if (BuildConfig.GOOGLE_BACKEND_SERVER_CLIENT_ID.startsWith("CHANGE-ME")) {
            update { it.copy(errorMessage = "لم يتم ضبط GOOGLE_BACKEND_SERVER_CLIENT_ID في إعدادات البناء — أضف معرّف OAuth (Web application) ثم أعد بناء التطبيق") }
            return
        }
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
        is BackendFailure.NetworkUnavailable -> BackendConfig.unreachableMessage()
        is BackendFailure -> e.messageAr
        is ApiException -> when (e.statusCode) {
            10 -> "خطأ إعداد (DEVELOPER_ERROR): تأكد من إنشاء OAuth client من نوع Android في Google Cloud بنفس اسم الحزمة وبصمة SHA-1 للتوقيع"
            7 -> "لا يوجد اتصال بالإنترنت"
            12501, 16 -> "تم إلغاء العملية"
            else -> "تعذّر ربط حساب Google (رمز ${e.statusCode})"
        }
        else -> "تعذّر ربط حساب Google، يرجى المحاولة مرة أخرى"
    }

    private inline fun update(block: (GoogleServicesStatusUiState) -> GoogleServicesStatusUiState) {
        _state.value = block(_state.value)
    }
}
