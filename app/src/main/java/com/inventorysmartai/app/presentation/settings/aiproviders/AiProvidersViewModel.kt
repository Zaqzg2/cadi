package com.inventorysmartai.app.presentation.settings.aiproviders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.data.ai.provider.AiProviderId
import com.inventorysmartai.app.data.ai.provider.AiProviderSettings
import com.inventorysmartai.app.data.ai.provider.AiProviderSnapshot
import com.inventorysmartai.app.data.ai.provider.OcrSpaceClient
import com.inventorysmartai.app.data.ai.provider.OpenAiCompatClient
import com.inventorysmartai.app.data.ai.provider.toProviderFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class AiProvidersUiState(
    val snapshot: AiProviderSnapshot = AiProviderSnapshot(providers = emptyList(), preferOcr = true),
    val testing: Set<AiProviderId> = emptySet(),
    /** Last test / save result per provider, already in Arabic. */
    val messages: Map<AiProviderId, String> = emptyMap(),
    val ocrTesting: Boolean = false,
    /** Last OCR.space test / save result, already in Arabic. */
    val ocrMessage: String? = null
)

@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val settings: AiProviderSettings,
    private val client: OpenAiCompatClient,
    private val ocrSpace: OcrSpaceClient
) : ViewModel() {

    private data class Ephemeral(
        val testing: Set<AiProviderId> = emptySet(),
        val messages: Map<AiProviderId, String> = emptyMap(),
        val ocrTesting: Boolean = false,
        val ocrMessage: String? = null
    )

    private val ephemeral = MutableStateFlow(Ephemeral())

    val state: StateFlow<AiProvidersUiState> = combine(settings.snapshot, ephemeral) { snapshot, e ->
        AiProvidersUiState(snapshot, e.testing, e.messages, e.ocrTesting, e.ocrMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProvidersUiState())

    fun saveKey(id: AiProviderId, key: String) = viewModelScope.launch {
        settings.setApiKey(id, key)
        say(id, "تم حفظ المفتاح مشفّرًا على هذا الجهاز.")
    }

    fun clearKey(id: AiProviderId) = viewModelScope.launch {
        settings.setApiKey(id, "")
        say(id, "تم حذف المفتاح.")
    }

    fun setEnabled(id: AiProviderId, enabled: Boolean) = viewModelScope.launch { settings.setEnabled(id, enabled) }

    fun saveModels(id: AiProviderId, textModel: String, visionModel: String) = viewModelScope.launch {
        settings.setModels(id, textModel, visionModel)
        say(id, "تم حفظ أسماء النماذج.")
    }

    fun move(id: AiProviderId, delta: Int) = viewModelScope.launch { settings.move(id, delta) }

    fun setPreferOcr(value: Boolean) = viewModelScope.launch { settings.setPreferOcr(value) }

    /** true = skip the backend and use the keys saved on this phone; false (default) = the backend. */
    fun setPreferDirect(value: Boolean) = viewModelScope.launch { settings.setPreferDirect(value) }

    /** One tiny request: proves the key works AND the text-model id exists. */
    fun test(id: AiProviderId) = viewModelScope.launch {
        val provider = settings.active(id)
        if (provider == null) {
            say(id, "أدخل المفتاح وفعّل المزوّد أولًا.")
            return@launch
        }
        ephemeral.update { it.copy(testing = it.testing + id) }
        val message = try {
            client.ping(provider)
            "يعمل بشكل سليم ✓"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toProviderFailure().messageAr
        }
        ephemeral.update { it.copy(testing = it.testing - id, messages = it.messages + (id to message)) }
    }

    // ---- OCR.space ----

    fun saveOcrKey(key: String) = viewModelScope.launch {
        settings.setOcrSpaceKey(key)
        sayOcr("تم حفظ المفتاح مشفّرًا على هذا الجهاز.")
    }

    fun clearOcrKey() = viewModelScope.launch {
        settings.setOcrSpaceKey("")
        sayOcr("تم حذف المفتاح.")
    }

    fun setOcrEnabled(enabled: Boolean) = viewModelScope.launch { settings.setOcrSpaceEnabled(enabled) }

    /** Sends a tiny test image: proves the key works and the service answers. */
    fun testOcr() = viewModelScope.launch {
        val ocr = settings.activeOcrSpace()
        if (ocr == null) {
            sayOcr("أدخل المفتاح وفعّل الخدمة أولًا.")
            return@launch
        }
        ephemeral.update { it.copy(ocrTesting = true) }
        val message = try {
            ocrSpace.ping(ocr)
            "يعمل بشكل سليم ✓"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toProviderFailure().messageAr
        }
        ephemeral.update { it.copy(ocrTesting = false, ocrMessage = message) }
    }

    private fun say(id: AiProviderId, message: String) {
        ephemeral.update { it.copy(messages = it.messages + (id to message)) }
    }

    private fun sayOcr(message: String) {
        ephemeral.update { it.copy(ocrMessage = message) }
    }
}
